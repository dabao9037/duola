package com.admin.common.task;

import com.admin.common.dto.GostDto;
import com.admin.common.utils.GostUtil;
import com.admin.common.utils.NodeServiceSnapshot;
import com.admin.entity.CleanupTask;
import com.admin.entity.Forward;
import com.admin.entity.Tunnel;
import com.admin.service.CleanupTaskService;
import com.admin.service.ForwardService;
import com.admin.service.TunnelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 节点端残留服务清理。
 *
 * 两条路径：
 * 1) retryPending —— 强制删除/删除失败时入队的任务，按指数退避重试；
 * 2) scanOrphans —— 用节点每 10 分钟上报的服务清单做对账，把
 *    “转发记录已不存在”或“转发已不属于该节点”的残留服务入队。
 *
 * 历史问题：节点侧删除曾经是“全有或全无”，面板又允许跳过节点删除，
 * 导致转发删了、服务还在跑流量。这个任务是对应的兜底。
 */
@Slf4j
@Configuration
@EnableScheduling
public class CleanupTaskAsync {

    private static final int MAX_RETRIES = 8;
    private static final long BASE_BACKOFF_MS = 60L * 1000L;
    private static final long MAX_BACKOFF_MS = 60L * 60L * 1000L;

    @Resource
    private CleanupTaskService cleanupTaskService;

    @Resource
    @Lazy
    private ForwardService forwardService;

    @Resource
    @Lazy
    private TunnelService tunnelService;

    /**
     * 重试待清理任务
     */
    @Scheduled(fixedDelay = 120_000L)
    public void retryPending() {
        long now = System.currentTimeMillis();
        List<CleanupTask> tasks;
        try {
            tasks = cleanupTaskService.list(new LambdaQueryWrapper<CleanupTask>()
                    .eq(CleanupTask::getState, "pending")
                    .le(CleanupTask::getNextRetryAt, now)
                    .orderByAsc(CleanupTask::getId)
                    .last("limit 20"));
        } catch (Exception e) {
            log.warn("读取待清理任务失败: {}", e.getMessage());
            return;
        }

        for (CleanupTask task : tasks) {
            try {
                GostDto result = "tls".equals(task.getServiceType())
                        ? GostUtil.DeleteRemoteService(task.getNodeId(), task.getServiceName())
                        : GostUtil.DeleteService(task.getNodeId(), task.getServiceName());

                if (GostUtil.isSuccess(result)) {
                    task.setState("done");
                    task.setLastError(null);
                    log.info("残留服务清理成功: 节点={} 服务={} 转发={}",
                            task.getNodeId(), task.getServiceName(), task.getForwardId());
                } else {
                    int retries = (task.getRetries() == null ? 0 : task.getRetries()) + 1;
                    task.setRetries(retries);
                    task.setLastError(truncate(result == null ? "无响应" : result.getMsg()));
                    task.setNextRetryAt(now + backoff(retries));
                    if (retries >= MAX_RETRIES) {
                        task.setState("failed");
                        log.warn("残留服务清理连续失败 {} 次，转人工: 节点={} 服务={} 原因={}",
                                retries, task.getNodeId(), task.getServiceName(), task.getLastError());
                    }
                }
                task.setUpdatedTime(System.currentTimeMillis());
                cleanupTaskService.updateById(task);
            } catch (Exception e) {
                log.warn("清理任务执行异常: id={} {}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * 用节点上报的服务清单做对账
     */
    @Scheduled(fixedDelay = 600_000L)
    public void scanOrphans() {
        Map<Long, NodeServiceSnapshot.Entry> snapshots = NodeServiceSnapshot.all();
        if (snapshots.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, NodeServiceSnapshot.Entry> entry : snapshots.entrySet()) {
            Long nodeId = entry.getKey();
            for (String raw : entry.getValue().getServices()) {
                if (raw == null) {
                    continue;
                }
                String name = raw.trim();
                if (name.isEmpty() || "web_api".equals(name)) {
                    continue;
                }
                String[] parts = name.split("_");
                if (parts.length != 4) {
                    continue;
                }
                String type = parts[3];
                // udp 服务跟着 tcp 一起删，只处理 tcp/tls 避免重复入队
                if (!"tcp".equals(type) && !"tls".equals(type)) {
                    continue;
                }
                try {
                    handleOrphan(nodeId, parts[0], parts[1] + "_" + parts[2], type);
                } catch (Exception e) {
                    log.warn("对账服务 {} 失败: {}", name, e.getMessage());
                }
            }
        }
    }

    private void handleOrphan(Long nodeId, String forwardIdText, String suffix, String type) {
        Long forwardId = Long.valueOf(forwardIdText);
        Forward forward = forwardService.getById(forwardId);
        boolean orphan = forward == null || !belongsToNode(forward, nodeId);
        if (!orphan) {
            return;
        }

        String serviceName = forwardIdText + "_" + suffix;
        enqueue(forwardId, nodeId, serviceName, type);
    }

    /**
     * 转发是否还挂在这台节点上。换过节点后，旧节点上的服务也应视为残留。
     */
    private boolean belongsToNode(Forward forward, Long nodeId) {
        if (forward.getTunnelId() == null) {
            return false;
        }
        Tunnel tunnel = tunnelService.getById(forward.getTunnelId());
        return tunnel != null && nodeId.equals(tunnel.getInNodeId());
    }

    public void enqueue(Long forwardId, Long nodeId, String serviceName, String serviceType) {
        int exists = cleanupTaskService.count(new LambdaQueryWrapper<CleanupTask>()
                .eq(CleanupTask::getForwardId, forwardId)
                .eq(CleanupTask::getNodeId, nodeId)
                .eq(CleanupTask::getServiceName, serviceName)
                .in(CleanupTask::getState, "pending", "failed"));
        if (exists > 0) {
            return;
        }

        long now = System.currentTimeMillis();
        CleanupTask task = new CleanupTask();
        task.setForwardId(forwardId);
        task.setNodeId(nodeId);
        task.setServiceName(serviceName);
        task.setServiceType(serviceType);
        task.setRetries(0);
        task.setNextRetryAt(now);
        task.setState("pending");
        task.setCreatedTime(now);
        task.setUpdatedTime(now);
        task.setStatus(0);
        cleanupTaskService.save(task);
        log.info("已加入待清理队列: 节点={} 服务={} 转发={}", nodeId, serviceName, forwardId);
    }

    private long backoff(int retries) {
        long delay = BASE_BACKOFF_MS * (1L << Math.min(retries - 1, 6));
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 250 ? text : text.substring(0, 250);
    }
}

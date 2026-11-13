package com.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 节点端待清理的服务任务。
 *
 * 背景：删除转发时如果节点离线或节点侧删除失败，转发记录会被删掉、
 * 但 gost 上的服务还活着，端口继续跑流量。该表记录这类残留，
 * 由 CleanupTaskAsync 定时重试，节点恢复后自动清理。
 *
 * @author QAQ
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cleanup_task")
public class CleanupTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 已删除的转发 ID
     */
    private Long forwardId;

    /**
     * 残留服务所在的节点 ID
     */
    private Long nodeId;

    /**
     * 服务基础名，不含类型后缀，例如 439_1_0
     */
    private String serviceName;

    /**
     * 服务类型：tcp / udp / tls
     */
    private String serviceType;

    /**
     * 已重试次数
     */
    private Integer retries;

    /**
     * 下次重试时间（毫秒时间戳）
     */
    private Long nextRetryAt;

    /**
     * 最近一次失败原因
     */
    private String lastError;

    /**
     * 任务状态：pending / done / failed
     */
    private String state;
}

package com.admin.common.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点最近一次上报的服务清单快照。
 *
 * gost 节点每 10 分钟 POST /flow/config 上报完整配置，这里只保留服务名集合
 * 和时间戳。删除服务前先用它过滤，避免把不存在的名字发下去；对账任务也用它
 * 判断节点上到底有哪些残留。
 *
 * 快照超过 30 分钟未更新视为失效（节点可能离线），调用方需自行回落。
 */
public class NodeServiceSnapshot {

    private static final long TTL_MS = 30L * 60L * 1000L;

    private static final Map<Long, Entry> CACHE = new ConcurrentHashMap<>();

    public static class Entry {
        private final Set<String> services;
        private final long updatedAt;

        Entry(Set<String> services, long updatedAt) {
            this.services = services;
            this.updatedAt = updatedAt;
        }

        public Set<String> getServices() {
            return services;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public boolean isStale() {
            return System.currentTimeMillis() - updatedAt > TTL_MS;
        }
    }

    public static void update(Long nodeId, Set<String> serviceNames) {
        if (nodeId == null) {
            return;
        }
        CACHE.put(nodeId, new Entry(Collections.unmodifiableSet(new HashSet<>(serviceNames)), System.currentTimeMillis()));
    }

    /**
     * 返回有效快照；节点从没上报过或快照已过期时返回 null。
     */
    public static Entry get(Long nodeId) {
        if (nodeId == null) {
            return null;
        }
        Entry entry = CACHE.get(nodeId);
        if (entry == null || entry.isStale()) {
            return null;
        }
        return entry;
    }

    public static Map<Long, Entry> all() {
        CACHE.entrySet().removeIf(e -> e.getValue().isStale());
        return Collections.unmodifiableMap(CACHE);
    }
}

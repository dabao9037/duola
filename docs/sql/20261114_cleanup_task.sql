-- 节点端残留服务清理队列
-- 背景：删除转发时若节点离线或节点侧删除失败，转发记录被删、gost 服务仍在跑流量。
-- 该表记录待清理的服务，由 CleanupTaskAsync 定时重试（指数退避，最多 8 次）。

CREATE TABLE IF NOT EXISTS `cleanup_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `forward_id` bigint(20) NOT NULL,
  `node_id` bigint(20) NOT NULL,
  `service_name` varchar(128) NOT NULL COMMENT '服务基础名，不含类型后缀，如 439_1_0',
  `service_type` varchar(8) NOT NULL DEFAULT 'tcp' COMMENT 'tcp / udp / tls',
  `retries` int(11) NOT NULL DEFAULT '0',
  `next_retry_at` bigint(20) NOT NULL DEFAULT '0',
  `last_error` varchar(255) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending / done / failed',
  `created_time` bigint(20) DEFAULT NULL,
  `updated_time` bigint(20) DEFAULT NULL,
  `status` int(11) DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_state_next` (`state`,`next_retry_at`),
  KEY `idx_forward_node` (`forward_id`,`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

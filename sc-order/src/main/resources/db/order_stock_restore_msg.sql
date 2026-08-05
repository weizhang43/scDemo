CREATE TABLE IF NOT EXISTS `t_order_stock_restore_msg` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `o_id`        INT          NOT NULL COMMENT '订单ID',
  `source`      TINYINT      NOT NULL DEFAULT 0 COMMENT '消息来源 0:取消订单 1:售后退款',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0:待处理 1:已完成 2:失败',
  `retry_cnt`   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `max_retry`   INT          NOT NULL DEFAULT 5 COMMENT '最大重试次数',
  `next_retry`  DATETIME     NOT NULL COMMENT '下次重试时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_o_id` (`o_id`),
  KEY `idx_status_next_retry` (`status`, `next_retry`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='取消订单回库存本地消息表';

-- 2026-08 售后功能：区分回库存来源。0:取消订单（回库存后删订单明细） 1:售后退款（订单仍存续，明细保留）。
-- 已建表的存量库补执行：
-- ALTER TABLE `t_order_stock_restore_msg`
--   ADD COLUMN `source` TINYINT NOT NULL DEFAULT 0 COMMENT '消息来源 0:取消订单 1:售后退款' AFTER `o_id`;

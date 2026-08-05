CREATE TABLE IF NOT EXISTS `t_pay_record` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pay_no`         VARCHAR(32)   NOT NULL COMMENT '支付单号（本系统生成）',
  `o_id`           INT           NOT NULL COMMENT '订单ID',
  `order_no`       VARCHAR(32)   NOT NULL COMMENT '订单号',
  `u_id`           INT           NOT NULL COMMENT '下单用户ID',
  `amount`         DECIMAL(12,2) NOT NULL COMMENT '支付金额',
  `channel`        VARCHAR(16)   NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道',
  `status`         TINYINT       NOT NULL DEFAULT 0 COMMENT '0:待支付 1:成功 2:失败 3:已关闭 4:待退款 5:已退款',
  `transaction_id` VARCHAR(64)   NULL COMMENT '网关交易号',
  `pay_time`       DATETIME      NULL COMMENT '支付成功时间',
  `notify_time`    DATETIME      NULL COMMENT '最近一次回调处理时间',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_pay_no` (`pay_no`),
  KEY `idx_o_id` (`o_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';

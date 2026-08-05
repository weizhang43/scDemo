CREATE TABLE IF NOT EXISTS `t_mock_pay_txn` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transaction_id`     VARCHAR(64)   NOT NULL COMMENT '网关交易号',
  `pay_no`             VARCHAR(32)   NOT NULL COMMENT '商户支付单号（预下单幂等键）',
  `amount`             DECIMAL(12,2) NOT NULL COMMENT '交易金额',
  `subject`            VARCHAR(256)  NULL COMMENT '交易摘要',
  `status`             TINYINT       NOT NULL DEFAULT 0 COMMENT '0:待支付 1:成功 2:失败 3:已关闭',
  `notify_url`         VARCHAR(256)  NOT NULL COMMENT '异步回调地址',
  `notify_cnt`         INT           NOT NULL DEFAULT 0 COMMENT '已回调次数',
  `last_notify_result` VARCHAR(64)   NULL COMMENT '最近一次回调响应',
  `refund_time`        DATETIME      NULL COMMENT '退款时间',
  `create_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME      NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_transaction_id` (`transaction_id`),
  UNIQUE INDEX `uk_pay_no` (`pay_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟支付网关交易表（sc-pay 私有）';

CREATE TABLE IF NOT EXISTS `t_pay_notify_log` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pay_no`         VARCHAR(32)   NULL COMMENT '支付单号',
  `transaction_id` VARCHAR(64)   NULL COMMENT '网关交易号',
  `trade_status`   VARCHAR(16)   NULL COMMENT '回调交易状态 SUCCESS/FAIL',
  `raw_params`     VARCHAR(2048) NULL COMMENT '原始回调参数 JSON',
  `sign_ok`        TINYINT       NOT NULL DEFAULT 0 COMMENT '验签结果 0:失败 1:通过',
  `process_result` VARCHAR(256)  NULL COMMENT '处理结果描述',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_pay_no` (`pay_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调流水表（每次回调无条件落一条）';

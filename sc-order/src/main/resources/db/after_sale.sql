-- 售后工单表。申请的前置条件（订单是我的、订单已完成/已发货）读的全是 sc-order 的表，
-- 且退款要联动本服务的 t_pay_record，故落在 sc-order（同 t_product_review 的落位理由）。
CREATE TABLE IF NOT EXISTS `t_after_sale` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `o_id` INT NOT NULL COMMENT '订单ID',
  `u_id` INT NOT NULL COMMENT '申请人ID（取自网关注入的 X-User-Id，不采信前端）',
  `type` TINYINT NOT NULL DEFAULT 1 COMMENT '售后类型 1:退货退款（2:换货 预留）',
  `reason` VARCHAR(500) NOT NULL COMMENT '申请原因',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0:待审核 1:同意退款中 2:已退款 3:已拒绝 4:已取消',
  `reject_reason` VARCHAR(200) DEFAULT NULL COMMENT '拒绝原因',
  `refund_no` VARCHAR(64) DEFAULT NULL COMMENT '退款依据的支付单号（t_pay_record.pay_no）',
  `refund_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '退款金额（=申请时订单支付金额）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
  `refund_time` DATETIME DEFAULT NULL COMMENT '退款完成时间',
  `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  -- 一单同时只允许一张售后工单：并发重复申请由唯一键兜底；
  -- 被拒绝/已取消(3/4)后允许重新申请 —— 走 UPDATE 复用同一行而非再插一行
  UNIQUE KEY `uk_o_id` (`o_id`),
  INDEX `idx_u_id` (`u_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后工单表';

-- 设计说明：
-- 1. 订单不加"售后中"新状态：售后态由本表维护，订单列表 LEFT JOIN 本表展示，避免污染订单状态机。
-- 2. refund_amount 落快照：退款金额是资金事实，必须固化在申请当时，不随订单后续变化。
-- 3. order_no / u_name 等展示字段不落库，查询时联 t_order / t_user 现取。
-- 4. 库存回补复用 t_order_stock_restore_msg（source=1），退款走已有 MockPayGatewayClient，
--    支付单 1(成功)→4(待退款)→5(已退款)；退款失败工单停在 1，由 sc-job retryAfterSaleRefund 重试。

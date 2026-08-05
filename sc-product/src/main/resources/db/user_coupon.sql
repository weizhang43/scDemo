CREATE TABLE IF NOT EXISTS `t_user_coupon` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '用户券ID',
  `template_id` INT NOT NULL COMMENT '券模板ID(t_coupon_template.id)',
  `u_id` INT NOT NULL COMMENT '持券用户ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-未使用 1-已锁定(下单占用) 2-已使用',
  `o_id` INT DEFAULT NULL COMMENT '核销订单ID（use 时绑定）',
  `coupon_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '锁定时按订单额算出的抵扣金额快照',
  `claim_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `use_time` DATETIME DEFAULT NULL COMMENT '核销时间',
  `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tpl_user` (`template_id`, `u_id`),
  KEY `idx_uid_status` (`u_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- 设计说明：
-- 1. uk_tpl_user 唯一索引兜底一人一张：Redis 已领集合丢失（重启/过期）时
--    DuplicateKeyException 仍是权威防线，处理方式同秒杀/售后（不做 check-then-insert）。
-- 2. 三态流转：0→1 下单锁定(lock，同时写 coupon_amount 快照)、
--    1→2 支付成功核销(use，绑定 o_id 写 use_time)、
--    1→0 / 2→0 取消订单或售后退款返还(restore，清空快照)。
--    所有流转均为条件 UPDATE(CAS)，天然幂等，返还搭 t_order_stock_restore_msg 消费的顺风车重试。
-- 3. 券与订单的权威关联在 t_order.coupon_id（金额事实快照 coupon_amount 同在订单侧），
--    本表 o_id 仅为核销留痕；抵扣额在 lock 时由服务端按模板规则计算，不采信前端。

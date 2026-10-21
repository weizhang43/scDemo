CREATE TABLE IF NOT EXISTS `t_points_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `u_id` INT NOT NULL COMMENT '用户ID',
  `change_value` INT NOT NULL COMMENT '积分变更值：正数获取，负数消耗',
  `type` VARCHAR(16) NOT NULL COMMENT '类型：EARN-下单获取 REDEEM-兑换消耗 COMPENSATE-兑换失败返还',
  `order_no` VARCHAR(64) NOT NULL COMMENT '关联单号：订单号（EARN）或兑换流水号（REDEEM/COMPENSATE）',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid_no_type` (`u_id`, `order_no`, `type`),
  KEY `idx_uid_time` (`u_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员积分流水表（uk_u_no_type 保证支付回调重试加分幂等）';

-- 会员等级按「累计获取积分」划分（不随消耗下降）：
--   普通 <100 / 白银 ≥100 / 黄金 ≥500 / 铂金 ≥2000
-- 规则为代码常量（PointsLevelRule），不建配置表。

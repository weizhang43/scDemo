CREATE TABLE IF NOT EXISTS `t_order` (
  `o_id` INT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
  `add_person` VARCHAR(64) DEFAULT NULL COMMENT '下单人(存 t_user.u_name)',
  `u_id` INT DEFAULT NULL COMMENT '下单顾客ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `order_address` VARCHAR(500) DEFAULT NULL COMMENT '下单地址',
  `address_id` INT DEFAULT NULL COMMENT '收货地址ID',
  `order_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '订单金额',
  `order_status` TINYINT DEFAULT 1 COMMENT '订单状态(-1:订单取消 0:待付款 1:已下单 2:已完成)',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`o_id`),
  UNIQUE INDEX `uk_order_no` (`order_no`),
  INDEX `idx_uid_create_time` (`u_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 已有表新增字段（若表已存在执行）
ALTER TABLE `t_order`
  ADD COLUMN `order_address` VARCHAR(500) DEFAULT NULL COMMENT '下单地址',
  ADD COLUMN `order_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '订单金额',
  ADD COLUMN `order_status` TINYINT DEFAULT 1 COMMENT '订单状态(0:订单取消 1:已下单 2:已完成)';

ALTER TABLE `t_order`
  ADD COLUMN `order_no` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '订单编号' AFTER `o_id`;
UPDATE `t_order` SET `order_no` = CONCAT('ORD', DATE_FORMAT(NOW(),'%Y%m%d'), LPAD(o_id, 8, '0')) WHERE `order_no` = '';
ALTER TABLE `t_order` ADD UNIQUE INDEX `uk_order_no` (`order_no`);

ALTER TABLE `t_order`
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  ADD COLUMN `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 订单归属：顾客只能看到 u_id 等于自己的订单。
-- 回填按 add_person = t_user.u_name 关联；匹配不上的（demo 造的 'curry' 等）留 NULL，
-- 顾客一律查不到，商家/管理员仍可见。
ALTER TABLE `t_order`
  ADD COLUMN `u_id` INT DEFAULT NULL COMMENT '下单顾客ID' AFTER `add_person`;
UPDATE `t_order` o JOIN `t_user` u ON o.add_person = u.u_name
  SET o.u_id = u.u_id WHERE o.u_id IS NULL;
ALTER TABLE `t_order` ADD INDEX `idx_uid_create_time` (`u_id`, `create_time`);

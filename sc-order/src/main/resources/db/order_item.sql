CREATE TABLE IF NOT EXISTS `t_order_item` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `o_id` INT NOT NULL COMMENT '订单ID',
  `p_id` INT NOT NULL COMMENT '商品ID',
  `p_name` VARCHAR(128) DEFAULT NULL COMMENT '商品名称快照',
  `price` DECIMAL(18,2) DEFAULT NULL COMMENT '下单单价快照',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '购买数量',
  PRIMARY KEY (`id`),
  INDEX `idx_o_id` (`o_id`),
  INDEX `idx_p_id` (`p_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品中间表';

-- t_order 表追加 address_id 字段（已有表执行）
ALTER TABLE `t_order`
  ADD COLUMN `address_id` INT DEFAULT NULL COMMENT '收货地址ID' AFTER `order_address`;

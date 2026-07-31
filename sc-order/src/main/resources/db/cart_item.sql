-- 购物车明细表。不存价格/名称快照：下单时 doPlaceOrder 会用 expectedPrice 与商品实时价比对，
-- 快照一旦过期必然撞「价格已更新，请刷新后重新下单」，故列表接口每次回源读实时价。
CREATE TABLE IF NOT EXISTS `t_cart_item` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `u_id` INT NOT NULL COMMENT '顾客ID（取自网关注入的 X-User-Id，不采信前端）',
  `p_id` INT NOT NULL COMMENT '商品ID',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '加购数量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次加购时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后变更时间',
  PRIMARY KEY (`id`),
  -- 承重索引：让「重复加购累加」能用一条 INSERT ... ON DUPLICATE KEY UPDATE 原子完成，
  -- 缺了它连点两次加购会插出两行而不是累加
  UNIQUE KEY `uk_u_id_p_id` (`u_id`, `p_id`),
  INDEX `idx_u_id` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车明细表';

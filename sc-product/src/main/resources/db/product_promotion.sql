CREATE TABLE IF NOT EXISTS `t_product_promotion` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '折扣活动ID',
  `p_id` INT NOT NULL COMMENT '商品ID（t_product.p_id）',
  `discount` TINYINT NOT NULL COMMENT '折扣率 1-99，如 85 表示 8.5 折；不打折即不存在该行',
  `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
  `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_pid_window` (`p_id`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品折扣活动表';

-- 设计说明：
-- 1. 不设 status 列。是否生效完全由时间窗决定（now BETWEEN start_time AND end_time），
--    「取消活动」即删除该行 —— 避免 status 与时间窗两个真相源互相矛盾。
-- 2. 不冗余 merchant_id。归属由 t_product.merchant_id 关联得出，
--    否则商品转让商家后这里会变成脏数据。
-- 3. 创建时拒绝同一 p_id 的时间窗重叠，从根上消除「同时多个折扣生效、取哪个」的歧义。
--    重叠判定：新窗口 start < 已存在 end AND 新窗口 end > 已存在 start。
-- 4. discount 取值 1-99：100 表示不打折，用「没有生效中的行」表达，不用 discount=100 表达。

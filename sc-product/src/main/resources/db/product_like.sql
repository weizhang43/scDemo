-- 商品点赞记录表。这张明细表既负责去重（谁点过），也是点赞数的唯一真值来源：
-- 唯一键保证一人一行，所以 COUNT(*) 就是精确点赞人数，展示与排序都联它现取。
-- Redis 的 product:like:count 只剩写侧削峰的职责，把 t_product.like_count 快照列攒批回写。
CREATE TABLE IF NOT EXISTS `t_product_like` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `u_id` INT NOT NULL COMMENT '点赞人ID（取自网关注入的 X-User-Id，不采信前端）',
  `p_id` INT NOT NULL COMMENT '商品ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  -- 承重索引：一人一商品只能点一次，靠它在 SQL 层去重。
  -- 缺了它就退化成「先查后插」，并发双击会插出两行、点赞数 +2
  UNIQUE KEY `uk_u_id_p_id` (`u_id`, `p_id`),
  INDEX `idx_p_id` (`p_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品点赞记录表';

-- 一次性对账：t_product.like_count 是建表时的种子列，从未与本表核对过，
-- 早期还被 like() 用作 Redis 的播种值，把错误值一路带了下去。跑一次把快照列拉回真值。
-- 展示已不读这一列，执行与否不影响页面，但留着脏数据会让后续排查困惑。
UPDATE `t_product` p
SET p.like_count = (SELECT COUNT(*) FROM `t_product_like` l WHERE l.p_id = p.p_id);

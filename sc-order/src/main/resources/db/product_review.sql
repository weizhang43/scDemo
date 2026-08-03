-- 商品评价表。评价的三个前置条件（订单是我的、订单已完成、商品在这单里）读的全是 sc-order 的表，
-- 故落在 sc-order 而非 sc-product，放别处每次评价要多一次 Feign 往返。
CREATE TABLE IF NOT EXISTS `t_product_review` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `u_id` INT NOT NULL COMMENT '评价人ID（取自网关注入的 X-User-Id，不采信前端）',
  `o_id` INT NOT NULL COMMENT '订单ID',
  `p_id` INT NOT NULL COMMENT '商品ID',
  `p_name` VARCHAR(128) DEFAULT NULL COMMENT '商品名快照（取自订单明细）',
  `rating` TINYINT NOT NULL COMMENT '星级 1-5',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '文字评论，可为空（只打星不写字也算一次评价）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  -- 按「订单+商品」各评一次：同一件商品买两单可以各评一次，同一单里不能刷评
  UNIQUE KEY `uk_o_id_p_id` (`o_id`, `p_id`),
  INDEX `idx_p_id` (`p_id`),
  INDEX `idx_u_id` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

-- 已建过带 u_name 的旧版本时补执行一次：
-- ALTER TABLE `t_product_review` DROP COLUMN `u_name`;

-- 不存用户名快照，只存 u_id：展示名在查询时 LEFT JOIN t_user 现取（同 OrderMapper 处理 add_person 的做法）。
-- 这样用户改名后评价区跟着变，也不必让中文名途经只允许 ASCII 的 HTTP 头——途经会被写成问号。
-- p_name 仍存快照：商品在 sc-product，同库拿不到，且「我的评价」应显示下单当时的商品名。
-- 订单被删不级联删评价：评价是给商品的公共信息，脱离订单仍应独立显示。

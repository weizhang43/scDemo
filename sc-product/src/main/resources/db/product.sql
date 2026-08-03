CREATE TABLE IF NOT EXISTS `t_product` (
  `p_id` INT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `p_name` VARCHAR(128) DEFAULT NULL COMMENT '商品名称',
  `price` INT DEFAULT NULL COMMENT '价格（单位：元）',
  `stock` INT DEFAULT NULL COMMENT '库存',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `shelf_life` INT DEFAULT NULL COMMENT '保质期（天）',
  `origin` VARCHAR(128) DEFAULT NULL COMMENT '产地',
  `is_expired` TINYINT DEFAULT 0 COMMENT '是否过期：0-未过期，1-已过期',
  `manufacturer` VARCHAR(128) DEFAULT NULL COMMENT '厂家名称',
  `pro_desc` TEXT DEFAULT NULL COMMENT '商品描述',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间（ES 增量同步依据）',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数量',
  `image_url` VARCHAR(512) DEFAULT NULL COMMENT '商品图片URL',
  `merchant_id` INT DEFAULT NULL COMMENT '所属商家用户ID（t_user.u_id，u_type=1）；NULL 视为公共商品，任何商家可管理',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态 1-上架 0-下架（下架后顾客端不可见）',
  `p_type` TINYINT NOT NULL DEFAULT 7 COMMENT '商品类型 1-食品饮品 2-电子产品 3-服装饰品 4-家用电器 5-汽车 6-厨房用品 7-其他',
  PRIMARY KEY (`p_id`),
  KEY `idx_merchant_status` (`merchant_id`, `status`),
  KEY `idx_status_pid` (`status`, `p_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 已存在的表执行以下 ALTER 增量脚本：
-- ALTER TABLE `t_product` ADD COLUMN `production_date` DATE DEFAULT NULL COMMENT '生产日期' AFTER `stock`;
-- ALTER TABLE `t_product` ADD COLUMN `shelf_life` INT DEFAULT NULL COMMENT '保质期（天）' AFTER `production_date`;
-- ALTER TABLE `t_product` ADD COLUMN `origin` VARCHAR(128) DEFAULT NULL COMMENT '产地' AFTER `shelf_life`;
-- ALTER TABLE `t_product` ADD COLUMN `is_expired` TINYINT DEFAULT 0 COMMENT '是否过期：0-未过期，1-已过期' AFTER `origin`;
-- ALTER TABLE `t_product` ADD COLUMN `manufacturer` VARCHAR(128) DEFAULT NULL COMMENT '厂家名称' AFTER `is_expired`;
-- ALTER TABLE `t_product` ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数量' AFTER `manufacturer`;
-- ALTER TABLE `t_product` ADD COLUMN `image_url` VARCHAR(512) DEFAULT NULL COMMENT '商品图片URL' AFTER `like_count`;

-- 商品描述大字段 + 增量同步时间戳（ES 索引同步用）
-- ALTER TABLE `t_product` ADD COLUMN `pro_desc` TEXT DEFAULT NULL COMMENT '商品描述' AFTER `manufacturer`;
-- ALTER TABLE `t_product` ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间（ES 增量同步依据）' AFTER `pro_desc`;

-- 商家归属 + 上架状态（商家端商品隔离、顾客端下架不可见）
-- ALTER TABLE `t_product` ADD COLUMN `merchant_id` INT DEFAULT NULL COMMENT '所属商家用户ID（t_user.u_id，u_type=1）；NULL 视为公共商品，任何商家可管理' AFTER `image_url`;
-- ALTER TABLE `t_product` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态 1-上架 0-下架（下架后顾客端不可见）' AFTER `merchant_id`;
-- ALTER TABLE `t_product` ADD KEY `idx_merchant_status` (`merchant_id`, `status`);
-- ALTER TABLE `t_product` ADD KEY `idx_status_pid` (`status`, `p_id`);

-- 存量商品归属回填：归给 u_id 最小的商家账号。
-- 若库中没有 u_type=1 的账号，子查询返回 NULL，merchant_id 保持 NULL —— 此时按"公共商品"语义，
-- 任何商家账号仍可在商品管理页看到并管理，不会出现空列表。
-- UPDATE `t_product` SET `merchant_id` = (SELECT MIN(`u_id`) FROM `t_user` WHERE `u_type` = 1)
--   WHERE `merchant_id` IS NULL;

-- 商品类型
-- ALTER TABLE `t_product` ADD COLUMN `p_type` TINYINT NOT NULL DEFAULT 7 COMMENT '商品类型 1-食品饮品 2-电子产品 3-服装饰品 4-家用电器 5-汽车 6-厨房用品 7-其他' AFTER `status`;

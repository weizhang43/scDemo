CREATE TABLE IF NOT EXISTS `t_product` (
  `p_id` INT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `p_name` VARCHAR(128) DEFAULT NULL COMMENT '商品名称',
  `price` INT DEFAULT NULL COMMENT '价格',
  `stock` INT DEFAULT NULL COMMENT '库存',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `shelf_life` INT DEFAULT NULL COMMENT '保质期（天）',
  `origin` VARCHAR(128) DEFAULT NULL COMMENT '产地',
  `is_expired` TINYINT DEFAULT 0 COMMENT '是否过期：0-未过期，1-已过期',
  `manufacturer` VARCHAR(128) DEFAULT NULL COMMENT '厂家名称',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数量',
  `image_url` VARCHAR(512) DEFAULT NULL COMMENT '商品图片URL',
  PRIMARY KEY (`p_id`)
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


CREATE TABLE IF NOT EXISTS `region` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(64) NOT NULL COMMENT '名称',
  `parent_id` BIGINT DEFAULT 0 COMMENT '上级ID',
  `level` TINYINT NOT NULL COMMENT '1-省 2-市 3-区',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区域表';
CREATE TABLE IF NOT EXISTS `t_notice` (
  `notice_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
  `content` LONGTEXT NULL COMMENT '通知内容（富文本HTML）',
  `cover_image` VARCHAR(255) NULL COMMENT '封面图URL（用于首页轮播）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-发布 0-草稿/下架',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '轮播排序，值大靠前',
  `create_by` INT NULL COMMENT '创建人ID',
  `create_name` VARCHAR(64) NULL COMMENT '创建人用户名',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`notice_id`),
  KEY `idx_status_sort` (`status`, `sort_order`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';

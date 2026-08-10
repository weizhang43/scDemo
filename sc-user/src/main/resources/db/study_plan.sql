CREATE TABLE IF NOT EXISTS `t_study_plan` (
  `plan_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content` LONGTEXT NULL COMMENT '计划内容（富文本HTML）',
  `publish_date` DATE NOT NULL COMMENT '发布日期',
  `plan_date` DATE NOT NULL COMMENT '计划日期',
  `publish_name` VARCHAR(64) NOT NULL DEFAULT 'zhangwei' COMMENT '发布人',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-已发布 2-已完成 3-已超期',
  `finish_date` DATE NULL COMMENT '完成日期',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`plan_id`),
  UNIQUE KEY `uk_plan_date` (`plan_date`),
  KEY `idx_status_plan_date` (`status`, `plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习计划表';

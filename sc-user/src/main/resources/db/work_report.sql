CREATE TABLE IF NOT EXISTS `t_work_report` (
  `report_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content` LONGTEXT NULL COMMENT '内容',
  `type` TINYINT NOT NULL COMMENT '类型 1-日报 2-周报',
  `create_name` VARCHAR(64) NOT NULL DEFAULT 'zhangwei' COMMENT '生成人',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`report_id`),
  KEY `idx_type_create_time` (`type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日报/周报表';

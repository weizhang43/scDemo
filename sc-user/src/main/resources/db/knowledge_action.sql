-- 知识速记-刷题统计：用户行为记录表
CREATE TABLE IF NOT EXISTS `t_knowledge_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_id` BIGINT NOT NULL COMMENT '知识点ID',
  `action` VARCHAR(20) NOT NULL COMMENT '行为类型 view/favorite/unfavorite/note/master',
  `tag` TINYINT NULL COMMENT '标签冗余字段，便于按标签聚合',
  `duration` INT NOT NULL DEFAULT 0 COMMENT '刷题耗时（秒），仅 view 行为可能 > 0',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_action` (`knowledge_id`, `action`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_action_create_time` (`action`, `create_time`),
  KEY `idx_tag_action` (`tag`, `action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识速记-用户行为记录表';

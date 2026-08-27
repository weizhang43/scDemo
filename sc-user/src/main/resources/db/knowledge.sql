CREATE TABLE IF NOT EXISTS `t_knowledge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question` LONGTEXT NOT NULL COMMENT '题干（富文本HTML）',
  `answer` LONGTEXT NULL COMMENT '答案（富文本HTML）',
  `add_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-正常 2-已收藏',
  `tag` TINYINT NOT NULL DEFAULT 1 COMMENT '标签 1-Java基础与核心特性 2-集合框架与数据结构 3-并发编程与多线程 4-JVM与性能调优 5-Spring全家桶 6-数据库与缓存 7-消息队列与分布式',
  `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除 0-否 1-是（忽略的试题逻辑删除）',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '查看次数（查看进度）',
  `last_view_time` DATETIME NULL COMMENT '最后查看时间',
  PRIMARY KEY (`id`),
  KEY `idx_del_flag_id` (`del_flag`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识速记-知识点表';

CREATE TABLE IF NOT EXISTS `t_knowledge_note` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `knowledge_id` BIGINT NOT NULL COMMENT '知识点ID',
  `content` TEXT NOT NULL COMMENT '笔记内容',
  `important` TINYINT NOT NULL DEFAULT 0 COMMENT '是否重点 0-否 1-是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识速记-笔记表';

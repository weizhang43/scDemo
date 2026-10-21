CREATE TABLE IF NOT EXISTS `t_browse_history` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `u_id` INT NOT NULL COMMENT '用户ID',
  `p_id` INT NOT NULL COMMENT '商品ID',
  `category_id` INT NULL COMMENT '商品分类ID（浏览时快照，用于推荐加权）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid_pid` (`u_id`, `p_id`),
  KEY `idx_u_id_time` (`u_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览足迹表（每用户每商品一条，重复浏览刷新时间）';

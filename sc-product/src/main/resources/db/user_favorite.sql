CREATE TABLE IF NOT EXISTS `t_user_favorite` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `u_id` INT NOT NULL COMMENT '用户ID',
  `p_id` INT NOT NULL COMMENT '商品ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid_pid` (`u_id`, `p_id`),
  KEY `idx_u_id` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

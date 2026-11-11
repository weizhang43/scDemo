CREATE TABLE IF NOT EXISTS `login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `u_name` VARCHAR(50) DEFAULT '' COMMENT '用户名',
  `u_type` TINYINT DEFAULT NULL COMMENT '角色:1商家 2顾客 3管理员',
  `ip` VARCHAR(128) DEFAULT '' COMMENT '登录IP地址',
  `login_time` DATETIME COMMENT '登录时间',
  `status` TINYINT DEFAULT 1 COMMENT '1-成功 0-失败',
  `msg` VARCHAR(255) DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_uname` (`u_name`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';
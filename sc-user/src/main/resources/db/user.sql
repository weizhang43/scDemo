CREATE TABLE IF NOT EXISTS `t_user` (
  `u_id` INT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `u_name` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码',
  `real_name` VARCHAR(64) NULL COMMENT '用户姓名',
  `gender` TINYINT NULL COMMENT '性别 0-未知 1-男 2-女',
  `phone` VARCHAR(20) NULL COMMENT '手机号码',
  `birthday` DATE NULL COMMENT '出生日期',
  `email` VARCHAR(128) NULL COMMENT '邮箱',
  PRIMARY KEY (`u_id`),
  UNIQUE KEY `uk_u_name` (`u_name`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

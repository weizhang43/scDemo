CREATE TABLE IF NOT EXISTS `t_user_address` (
  `a_id` INT NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `u_id` INT NOT NULL COMMENT '用户ID',
  `consignee` VARCHAR(64) NOT NULL COMMENT '收件人',
  `phone` VARCHAR(20) NOT NULL COMMENT '收件人手机号',
  `province` VARCHAR(64) DEFAULT NULL COMMENT '省',
  `city` VARCHAR(64) DEFAULT NULL COMMENT '市',
  `district` VARCHAR(64) DEFAULT NULL COMMENT '区/县',
  `detail` VARCHAR(500) NOT NULL COMMENT '详细地址',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认(0:否 1:是)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`a_id`),
  INDEX `idx_u_id` (`u_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

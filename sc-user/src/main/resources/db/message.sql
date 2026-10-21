CREATE TABLE IF NOT EXISTS `t_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `u_id` INT NOT NULL COMMENT '收件人用户ID',
  `msg_type` VARCHAR(32) NOT NULL DEFAULT 'system' COMMENT '消息类型：order-订单 / aftersale-售后 / system-系统',
  `title` VARCHAR(200) NOT NULL COMMENT '消息标题',
  `content` VARCHAR(1000) NULL COMMENT '消息内容',
  `biz_id` VARCHAR(64) NULL COMMENT '关联业务ID（如订单oId，用于前端跳转）',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态 0-未读 1-已读',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_uid_read_time` (`u_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息表';

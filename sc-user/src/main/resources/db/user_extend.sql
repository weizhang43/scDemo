-- t_user 新增字段：用户姓名、性别、手机号码、出生日期
ALTER TABLE `t_user`
  ADD COLUMN `real_name`   VARCHAR(64)  NULL COMMENT '用户姓名' AFTER `password`,
  ADD COLUMN `gender`      TINYINT      NULL COMMENT '性别 0-未知 1-男 2-女' AFTER `real_name`,
  ADD COLUMN `phone`       VARCHAR(20)  NULL COMMENT '手机号码' AFTER `gender`,
  ADD COLUMN `birthday`    DATE         NULL COMMENT '出生日期' AFTER `phone`;

-- 手机号唯一索引（找回密码依赖手机号定位用户）
ALTER TABLE `t_user`
  ADD UNIQUE KEY `uk_phone` (`phone`);

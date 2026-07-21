-- t_user 新增字段：邮箱
ALTER TABLE `t_user`
  ADD COLUMN `email` VARCHAR(128) NULL COMMENT '邮箱' AFTER `birthday`;

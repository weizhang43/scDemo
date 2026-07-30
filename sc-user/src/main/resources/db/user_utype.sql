-- t_user 新增字段：用户类型
-- 存量数据统一按管理员处理（DEFAULT 3 会自动填充已有行）
ALTER TABLE `t_user`
  ADD COLUMN `u_type` TINYINT NOT NULL DEFAULT 3 COMMENT '用户类型 1-商家 2-顾客 3-管理员' AFTER `email`;

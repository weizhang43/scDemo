-- t_user 新增字段：头像
-- 存的是 FileController 返回的相对路径（/user/image/xxx.png），不存二进制。
-- 网关已把 GET /user/image/** 放进匿名白名单，所以这个路径可以直接绑到 <img src>。
ALTER TABLE `t_user`
  ADD COLUMN `avatar` VARCHAR(255) NULL COMMENT '头像图片路径' AFTER `birthday`;

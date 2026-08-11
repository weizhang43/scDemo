-- 用户逻辑删除 + 用户删除按钮权限
-- 1) t_user 增加逻辑删除标志位
ALTER TABLE `t_user`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-正常 1-已删除';

-- 2) 新增按钮权限：按钮_用户删除权限（挂在"用户管理"菜单 id=2 下，参见 rbac.sql）
INSERT INTO `t_module` (`parent_id`, `name`, `type`, `permission`, `url`, `sort`) VALUES
  (2, '按钮_用户删除权限', 'BTN', 'user:delete', NULL, 3);

-- 3) 默认授予超级管理员角色（role_id=1）
INSERT INTO `t_role_module` (`role_id`, `module_id`)
SELECT 1, `id` FROM `t_module` WHERE `permission` = 'user:delete';

-- 角色表
CREATE TABLE IF NOT EXISTS `t_role` (
  `id`          INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code`        VARCHAR(50)  NOT NULL COMMENT '角色编码',
  `name`        VARCHAR(50)  NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) NULL COMMENT '描述',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `create_time` DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS `t_user_role` (
  `id`          INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     INT NOT NULL COMMENT '用户ID(关联 t_user.u_id)',
  `role_id`     INT NOT NULL COMMENT '角色ID(关联 t_role.id)',
  `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- 权限/资源表(树形结构)
CREATE TABLE IF NOT EXISTS `t_module` (
  `id`          INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id`   INT NOT NULL DEFAULT 0 COMMENT '父节点ID,0为根节点',
  `name`        VARCHAR(50)  NOT NULL COMMENT '名称',
  `type`        VARCHAR(10)  NOT NULL DEFAULT 'MENU' COMMENT '类型 MENU菜单 BTN按钮',
  `permission`  VARCHAR(100) NULL COMMENT '权限标识',
  `url`         VARCHAR(200) NULL COMMENT '菜单访问URL',
  `icon`        VARCHAR(50)  NULL COMMENT '图标',
  `sort`        INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `create_time` DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限/资源表';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS `t_role_module` (
  `id`          INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id`     INT NOT NULL COMMENT '角色ID',
  `module_id`   INT NOT NULL COMMENT '权限ID',
  `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_module` (`role_id`, `module_id`),
  KEY `idx_module_id` (`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联表';

-- 初始化数据
INSERT INTO `t_role` (`code`, `name`, `description`) VALUES
  ('admin', '超级管理员', '拥有所有权限'),
  ('user_manager', '用户管理员', '仅用户管理权限');

INSERT INTO `t_module` (`id`, `parent_id`, `name`, `type`, `permission`, `url`, `sort`) VALUES
  (1, 0, '系统管理', 'MENU', 'system',      '/system',         1),
  (2, 1, '用户管理', 'MENU', 'user:list',   '/system/users',   1),
  (3, 1, '角色管理', 'MENU', 'role:list',   '/system/roles',   2),
  (4, 1, '权限管理', 'MENU', 'module:list', '/system/modules', 3),
  (5, 2, '用户新增', 'BTN',  'user:add',    NULL,              1),
  (6, 2, '用户编辑', 'BTN',  'user:update', NULL,              2),
  (7, 3, '角色新增', 'BTN',  'role:add',    NULL,              1),
  (8, 3, '角色授权', 'BTN',  'role:assign', NULL,              2);

-- 超管拥有全部权限
INSERT INTO `t_role_module` (`role_id`, `module_id`)
SELECT 1, `id` FROM `t_module`;

-- 用户管理员:仅系统管理+用户管理
INSERT INTO `t_role_module` (`role_id`, `module_id`) VALUES
  (2, 1), (2, 2), (2, 5), (2, 6);

ALTER TABLE `t_notice`
  ADD COLUMN `target_types` TINYINT NULL COMMENT '通知对象位掩码：商家1 顾客2 管理员4；NULL为全部' AFTER `sort_order`;

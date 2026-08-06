-- t_user 增加注册时间列：存量行保留 NULL（不计入“今日新增”），
-- DEFAULT CURRENT_TIMESTAMP 使注册插入无需改动应用代码。
ALTER TABLE t_user
  ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间' AFTER u_type;

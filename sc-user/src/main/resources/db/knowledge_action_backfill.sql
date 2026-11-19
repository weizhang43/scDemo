-- =============================================
-- 知识速记-历史数据回填脚本
-- 用途：把 t_knowledge / t_knowledge_note 中已存在的数据
--       按时间字段回填到 t_knowledge_action，用于刷题统计展示历史数据
-- 说明：
--   1. 幂等——重复执行前会先清理已回填的数据（标记为 backfill=1 的）
--   2. 收藏数据没有时间戳，按当前状态用 add_time 作为收藏时间估算
--   3. 笔记有 create_time，可精确回填
--   4. 掌握（已忽略）数据没有时间戳，按 add_time 估算
--   5. 已存在的 view_count 也回填为对应数量的 view 行为（create_time 取 last_view_time）
-- =============================================

-- 0. 给 t_knowledge_action 加一个 backfill 标记字段（如果尚未添加过），用于幂等清理
-- ALTER TABLE `t_knowledge_action` ADD COLUMN `backfill` TINYINT NOT NULL DEFAULT 0 COMMENT '是否历史回填 0-否 1-是';

-- 1. 清理上次回填的数据（如果之前有跑过本脚本）
-- DELETE FROM `t_knowledge_action` WHERE `backfill` = 1;

-- 2. 回填收藏（当前 status=2 的题）——用 add_time 作为收藏时间
INSERT INTO `t_knowledge_action` (`knowledge_id`, `action`, `tag`, `duration`, `create_time`)
SELECT `id`, 'favorite', `tag`, 0, `add_time`
FROM `t_knowledge`
WHERE `status` = 2;

-- 3. 回填笔记（当前还存在的笔记）——用笔记自身的 create_time，最精确
INSERT INTO `t_knowledge_action` (`knowledge_id`, `action`, `tag`, `duration`, `create_time`)
SELECT n.`knowledge_id`, 'note', k.`tag`, 0, n.`create_time`
FROM `t_knowledge_note` n
JOIN `t_knowledge` k ON k.`id` = n.`knowledge_id`;

-- 4. 回填已掌握（del_flag=1 的题）——用 add_time 估算
INSERT INTO `t_knowledge_action` (`knowledge_id`, `action`, `tag`, `duration`, `create_time`)
SELECT `id`, 'master', `tag`, 0, `add_time`
FROM `t_knowledge`
WHERE `del_flag` = 1;

-- 5. 回填查看次数：view_count 大于 0 的题，按 view_count 数量各插入一条 view 记录
--    create_time 用 last_view_time（如果为空则用 add_time）
--    MySQL 5.7 兼容写法：使用 mysql.help_topic 作为数字辅助表
INSERT INTO `t_knowledge_action` (`knowledge_id`, `action`, `tag`, `duration`, `create_time`)
SELECT k.`id`, 'view', k.`tag`, 0,
       COALESCE(k.`last_view_time`, k.`add_time`)
FROM `t_knowledge` k
JOIN (
  SELECT `help_topic_id` AS n FROM mysql.`help_topic`
) seq ON seq.n < k.`view_count`
WHERE k.`view_count` > 0;

-- =============================================
-- 验证：执行后可跑下面的 SQL 看每天的总量是否符合预期
-- =============================================
-- SELECT DATE(create_time) AS day, action, COUNT(*) AS cnt
-- FROM t_knowledge_action
-- GROUP BY DATE(create_time), action
-- ORDER BY day DESC, action;

-- =============================================
-- 试题维度 TOP10 数据排查 SQL
-- =============================================

-- 1. 笔记数 TOP10（应该看到 kid + cnt）
SELECT knowledge_id AS kid, COUNT(*) AS cnt
FROM t_knowledge_note
GROUP BY knowledge_id
ORDER BY cnt DESC
LIMIT 10;

-- 2. 耗时 TOP10（应该看到 kid + sec）
SELECT knowledge_id AS kid, SUM(duration) AS sec
FROM t_knowledge_action
WHERE action = 'view'
GROUP BY knowledge_id
HAVING SUM(duration) > 0
ORDER BY sec DESC
LIMIT 10;

-- 3. 查看次数 TOP10（应该看到 id + view_count）
SELECT id, question, view_count
FROM t_knowledge
WHERE view_count > 0
ORDER BY view_count DESC
LIMIT 10;

-- 4. 行为表整体看一下：每种类型的总数
SELECT action, COUNT(*) AS cnt, SUM(duration) AS total_duration
FROM t_knowledge_action
GROUP BY action;

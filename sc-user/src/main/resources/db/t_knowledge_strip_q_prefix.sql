-- ============================================================
-- 任务：去掉 t_knowledge.question（题干）开头的题号前缀
-- 目标库：zhangwei  /  目标表：t_knowledge  /  目标字段：question（LONGTEXT 富文本）
-- 题号格式：Q + 一串数字 + 分隔符（半角点 . / 全角： / 半角: / 顿号 、 / 中文逗号 ，）
--          分隔符后若有空白（空格/制表符/全角空格）一并去掉
--          题干可能被 <p>...</p> 包裹，题号在 <p> 之后，故前缀 <p> 也参与匹配
-- 幂等：已去前缀的行再跑不会变化
-- ============================================================

-- ---------- 1. 预检：先看会影响哪些行、影响前后的样本（先单独跑这段） ----------
-- SELECT
--   id,
--   LEFT(question, 80) AS before,
--   LEFT(
--     REGEXP_REPLACE(
--       question,
--       '^(<p[^>]*>)?[[:space:]]*Q[0-9]+[.:：、，][[:space:]]*',
--       COALESCE(REGEXP_SUBSTR(question, '^<p[^>]*>'), '')
--     ),
--     80
--   ) AS after
-- FROM t_knowledge
-- WHERE question REGEXP '(<p[^>]*>)?[[:space:]]*Q[0-9]+[.:：、，]'
-- LIMIT 20;
--
-- -- 影响行数核对
-- SELECT COUNT(*) AS will_update
-- FROM t_knowledge
-- WHERE question REGEXP '(<p[^>]*>)?[[:space:]]*Q[0-9]+[.:：、，]';

-- ---------- 2. 备份（强烈建议：在跑 UPDATE 前执行一次，便于回滚） ----------
-- CREATE TABLE t_knowledge_bak_q_prefix_20261118 AS
-- SELECT * FROM t_knowledge;

-- ---------- 3. 正式 UPDATE（确认预检无误后再执行） ----------
-- 思路：
--   * 先用 REGEXP_SUBSTR 取出开头的 <p ...> 标签（可能为空）
--   * 再用 REGEXP_REPLACE 把 “(可选<p>) + 可选空白 + Q数字 + 分隔符 + 可选空白” 整段替换为 <p> 标签
UPDATE t_knowledge
SET question = REGEXP_REPLACE(
      question,
      '^(<p[^>]*>)?[[:space:]]*Q[0-9]+[.:：、，][[:space:]]*',
      REGEXP_SUBSTR(question, '^<p[^>]*>')
    )
WHERE question REGEXP '^(<p[^>]*>)?[[:space:]]*Q[0-9]+[.:：、，]';

-- ---------- 4. 校验：执行后确认已无 Q<数字> 开头的题干 ----------
-- SELECT COUNT(*) AS still_has_prefix
-- FROM t_knowledge
-- WHERE question REGEXP '^(<p[^>]*>)?[[:space:]]*Q[0-9]+[.:：、，]';
-- -- 期望：0

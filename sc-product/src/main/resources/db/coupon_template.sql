CREATE TABLE IF NOT EXISTS `t_coupon_template` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '券模板ID',
  `merchant_id` INT NOT NULL DEFAULT 0 COMMENT '发行商家ID，0=平台通用（仅管理员可发）',
  `name` VARCHAR(64) NOT NULL COMMENT '券名称',
  `type` TINYINT NOT NULL COMMENT '券类型 1:满减 2:折扣',
  `threshold_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛：订单应付满 X 元可用，0 不限',
  `off_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '满减券减免金额（type=1 必填）',
  `discount_rate` DECIMAL(4,2) DEFAULT NULL COMMENT '折扣率 0-1，如 0.85 为 85 折（type=2 必填）',
  `total_count` INT NOT NULL COMMENT '发行总量',
  `remain_count` INT NOT NULL COMMENT '剩余可领数量',
  `valid_start` DATETIME NOT NULL COMMENT '有效期开始',
  `valid_end` DATETIME NOT NULL COMMENT '有效期结束',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-有效 0-已停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_window` (`status`, `valid_start`, `valid_end`),
  KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板表';

-- 设计说明：
-- 1. 每人每模板限领 1 张，由 t_user_coupon 的 uk_tpl_user 唯一索引兜底，
--    Redis 侧用 coupon:claimed:{templateId} 集合（sismember）做一人一张的快速判定，
--    与秒杀 seckill:bought:{activityId} 同一套 Lua 模板，不单设 per_user_limit 配置。
-- 2. 领券防超发完整复刻秒杀：coupon:stock:{templateId} 懒播种（值取 DB remain_count），
--    Lua 原子完成 存在性校验 → 已领校验 → DECR → SADD → EXPIRE；
--    DB 落库失败回滚 Lua，TTL = valid_end + 30 分钟余量。
-- 3. 停用（status=0）时 Redis 库存置 0 而非删 key，理由同秒杀：
--    删 key 后在途补偿的 INCR 会把它重建成 1。
-- 4. 叠加规则：券作用于「促销折后价汇总出的整单应付额」，一单最多一张券；
--    满减直减 off_amount（不超过订单额），折扣券按 discount_rate 对总额打折。

CREATE TABLE IF NOT EXISTS `t_seckill_activity` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '秒杀活动ID',
  `p_id` INT NOT NULL COMMENT '商品ID（t_product.p_id）',
  `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价（单位：元）',
  `seckill_stock` INT NOT NULL COMMENT '活动库存：从商品库存中划出的可秒杀上限，不预扣真实库存',
  `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
  `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-有效 0-已取消',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_pid_status_window` (`p_id`, `status`, `start_time`, `end_time`),
  KEY `idx_status_window` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品秒杀活动表';

-- 设计说明：
-- 1. 与折扣活动不同，这里保留 status —— 商家「手动取消进行中的秒杀」是真实需求，
--    且取消不能删行（Redis 侧还有在途预扣需要能查到活动信息做补偿）。
-- 2. seckill_stock 只是上限，不预扣 t_product.stock。每笔秒杀成交仍走
--    checkAndDeductStock 扣减真实库存，所以商品仍可正常销售。
--    创建时拒绝同商品时间窗重叠（否则顾客端无法确定该按哪个价格抢），
--    并校验「所有未结束活动划出的名额总量 <= 商品当前库存」。
-- 3. Redis 键按 activityId 而非 pId：seckill:stock:{activityId} / seckill:bought:{activityId}，
--    播种值取 min(seckill_stock, 商品当前库存)，并设 TTL = end_time + 余量，避免每个活动永久漏键。
-- 4. 取消活动用 SET 0，绝不能 DELETE 库存键 —— 回滚 Lua 是无条件 INCR，
--    在途补偿会把已删的键重建成 1，绕过「键不存在→未就绪」守卫把活动复活。
-- 5. 秒杀价只在 /order/seckill 路径生效，普通下单路径取不到，
--    否则顾客可用大 quantity 走普通下单以秒杀价批量买入。

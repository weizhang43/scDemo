-- 秒杀订单标记：在已有订单上打标，方便「我的秒杀」查询
ALTER TABLE `t_order` ADD COLUMN `source` VARCHAR(20) DEFAULT 'NORMAL' COMMENT '订单来源：NORMAL-普通下单 SECKILL-秒杀';
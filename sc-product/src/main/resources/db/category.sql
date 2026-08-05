CREATE TABLE IF NOT EXISTS `t_category` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` INT NOT NULL DEFAULT 0 COMMENT '父分类ID，0=一级分类（最多两级）',
  `name` VARCHAR(64) NOT NULL COMMENT '分类名称',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1-启用 0-停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表（两级树）';

-- 初始 7 条一级分类：id 1-7 与原 t_product.p_type 字典一一对应，
-- 使得存量迁移只需一句 UPDATE；新建分类的 id 从 100 起步（AUTO_INCREMENT=100），永不与字典段冲突。
INSERT IGNORE INTO `t_category` (`id`, `parent_id`, `name`, `sort`) VALUES
  (1, 0, '食品饮品', 1),
  (2, 0, '电子产品', 2),
  (3, 0, '服装饰品', 3),
  (4, 0, '家用电器', 4),
  (5, 0, '汽车', 5),
  (6, 0, '厨房用品', 6),
  (7, 0, '其他', 7);

-- t_product 增量脚本：新增 category_id，保留 p_type（已废弃，仅兼容回滚）
-- ALTER TABLE `t_product` ADD COLUMN `category_id` INT DEFAULT NULL COMMENT '分类ID（t_category.id）；p_type 已废弃，仅保留兼容' AFTER `p_type`;
-- ALTER TABLE `t_product` ADD KEY `idx_category_id` (`category_id`);
-- 存量迁移：原 p_type 值即对应一级分类 id
-- UPDATE `t_product` SET `category_id` = `p_type` WHERE `category_id` IS NULL;

-- 设计说明：
-- 1. 两级树：parent_id=0 为一级分类，二级分类的 parent_id 必须指向一级分类，服务端校验拒绝更深层级。
-- 2. 删除约束：仅当分类无子分类且无商品引用（t_product.category_id）时才允许删除。
-- 3. 商品可挂任意层级分类；按一级分类筛选/统计时展开为「自身 + 子分类」的 IN 查询（一级分类 GROUP BY 用 IF(parent_id=0, id, parent_id) 归并到根）。

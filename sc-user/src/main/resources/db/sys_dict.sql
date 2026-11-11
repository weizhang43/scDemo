CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `dict_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` VARCHAR(64) DEFAULT '' COMMENT '字典名称',
  `dict_type` VARCHAR(64) NOT NULL COMMENT '字典类型编码',
  `status` TINYINT DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '更新时间',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `data_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典数据ID',
  `dict_type` VARCHAR(64) NOT NULL COMMENT '字典类型编码',
  `dict_label` VARCHAR(64) DEFAULT '' COMMENT '字典标签',
  `dict_value` VARCHAR(64) DEFAULT '' COMMENT '字典键值',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `status` TINYINT DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '更新时间',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`data_id`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';
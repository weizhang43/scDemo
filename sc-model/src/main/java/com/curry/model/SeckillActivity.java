package com.curry.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品秒杀活动。
 * seckillStock 只是从商品库存中划出的可秒杀上限，不预扣 t_product.stock，
 * 每笔成交仍走 checkAndDeductStock 扣真实库存，因此商品可同时正常销售。
 */
@Data
@TableName("t_seckill_activity")
public class SeckillActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @JsonProperty("pId")
    @TableField("p_id")
    private Integer pId;

    /** 秒杀价（元），只在 /order/seckill 路径生效 */
    @TableField("seckill_price")
    private BigDecimal seckillPrice;

    /** 活动库存：可秒杀名额上限 */
    @TableField("seckill_stock")
    private Integer seckillStock;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("start_time")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("end_time")
    private Date endTime;

    /** 1-有效 0-已取消。取消不删行：Redis 侧还有在途预扣需要查到活动做补偿 */
    @TableField("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("create_time")
    private Date createTime;

    /** 商品名称，展示用，由关联查询回填 */
    @JsonProperty("pName")
    @TableField(exist = false)
    private String pName;

    /** 商品原价（元），展示划线价用 */
    @TableField(exist = false)
    private Integer price;

    @JsonProperty("imageUrl")
    @TableField(exist = false)
    private String imageUrl;

    /** 商品当前真实库存，商家端校验划出名额用 */
    @TableField(exist = false)
    private Integer productStock;

    /** Redis 中剩余可抢名额；未播种时为 null（等同于全部剩余） */
    @TableField(exist = false)
    private Integer remainStock;
}

package com.example.scproduct.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.SeckillActivity;
import com.example.scproduct.auth.AudienceScope;
import response.ResponseDto;

public interface SeckillActivityService extends IService<SeckillActivity> {

    /**
     * 创建秒杀活动：校验归属、时间窗不重叠、划出名额不超过商品当前库存。
     */
    ResponseDto<SeckillActivity> create(SeckillActivity activity, AudienceScope scope);

    /**
     * 取消活动：置 status=0 并把 Redis 名额置 0（保留 key 与 TTL）。
     * 绝不删 key —— 在途补偿会把已删的 key 重建出来。
     */
    ResponseDto<SeckillActivity> cancel(Integer id, AudienceScope scope);

    /**
     * 商家端：秒杀活动分页列表，pId 非空时只看某个商品。
     */
    ResponseDto<SeckillActivity> pageQuery(Integer pId, int pageNo, int pageSize, AudienceScope scope);

    /**
     * 顾客端：尚未结束的有效活动（含未开始的，供前端倒计时），带剩余名额。
     */
    ResponseDto<SeckillActivity> listForCustomer();

    /**
     * 按主键查活动详情（带商品名与原价）。秒杀落库的价格与名称快照取自这里。
     */
    SeckillActivity detail(Integer id);

    /**
     * 秒杀预扣名额（Redis 原子，一人一单）。
     * 名额按 activityId 计，播种值取 min(活动名额, 商品当前库存) 并按活动结束时间设 TTL。
     *
     * @return code=200 预扣成功；否则 msg 为失败原因
     */
    ResponseDto<SeckillActivity> preDeduct(Integer activityId, Integer uId);

    /**
     * 秒杀补偿：移除用户已购标记，并按 restoreStock 决定是否归还名额。
     *
     * @param restoreStock true 归还名额（可重试的偶发失败）；
     *                     false 只放开一人一单（真实库存不足 —— 归还名额只会让下一个人撞同一堵墙）
     */
    ResponseDto<SeckillActivity> rollback(Integer activityId, Integer uId, boolean restoreStock);
}

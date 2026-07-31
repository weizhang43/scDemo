package com.example.scorder.auth;

/**
 * 订单可见范围。三态：
 *   内部服务调用 / MQ 消费线程 / 商家 / 管理员 → 不过滤
 *   顾客                                  → 只看 u_id 等于自己的订单
 *
 * 不可变对象：可以安全地从 Controller 传到 Service 与线程池。
 */
public final class OrderScope {

    private static final OrderScope UNRESTRICTED = new OrderScope(null);

    /** 非 null 时只返回 u_id 等于该值的订单 */
    private final Integer ownerUId;

    private OrderScope(Integer ownerUId) {
        this.ownerUId = ownerUId;
    }

    /** 服务间内部调用、商家与管理员：不加任何过滤条件 */
    public static OrderScope unrestricted() {
        return UNRESTRICTED;
    }

    /** 顾客：只能看自己的订单 */
    public static OrderScope owner(Integer uId) {
        return new OrderScope(uId);
    }

    /** 供 Mapper 拼接 SQL 条件：null 表示不过滤 */
    public Integer getOwnerUId() {
        return ownerUId;
    }

    public boolean isUnrestricted() {
        return ownerUId == null;
    }

    /**
     * 是否有权操作（支付 / 取消 / 收货 / 删除）指定订单。
     * 顾客只能操作自己的；u_id 为 null 的历史订单顾客一律无权。
     *
     * @param orderUId 订单当前的 u_id，NULL 表示回填不到下单人
     */
    public boolean canManage(Integer orderUId) {
        if (ownerUId == null) {
            return true;
        }
        return ownerUId.equals(orderUId);
    }
}

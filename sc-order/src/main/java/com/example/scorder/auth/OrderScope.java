package com.example.scorder.auth;

/**
 * 订单可见范围：顾客按订单归属，商家按订单明细中的商家快照，管理员/内部调用不限制。
 * 不可变对象：可以安全地从 Controller 传到 Service 与线程池。
 */
public final class OrderScope {

    private static final OrderScope UNRESTRICTED = new OrderScope(null, null);

    private final Integer ownerUId;
    private final Integer merchantId;

    private OrderScope(Integer ownerUId, Integer merchantId) {
        this.ownerUId = ownerUId;
        this.merchantId = merchantId;
    }

    public static OrderScope unrestricted() {
        return UNRESTRICTED;
    }

    public static OrderScope owner(Integer uId) {
        return new OrderScope(uId, null);
    }

    public static OrderScope merchant(Integer merchantId) {
        return new OrderScope(null, merchantId);
    }

    public Integer getOwnerUId() {
        return ownerUId;
    }

    public Integer getMerchantId() {
        return merchantId;
    }

    public boolean isUnrestricted() {
        return ownerUId == null && merchantId == null;
    }

    public boolean isCustomer() {
        return ownerUId != null;
    }

    public boolean isMerchant() {
        return merchantId != null;
    }

    /** 顾客可操作自己的订单；商家仅可操作包含本店明细的订单；管理员/内部调用不限制。 */
    public boolean canManage(Integer orderUId, boolean containsMerchantItem) {
        return isUnrestricted()
                || (isCustomer() && ownerUId.equals(orderUId))
                || (isMerchant() && containsMerchantItem);
    }

    /** 顾客/管理员的旧归属判断。商家必须使用带明细归属的重载方法。 */
    public boolean canManage(Integer orderUId) {
        return isUnrestricted() || (isCustomer() && ownerUId.equals(orderUId));
    }
}

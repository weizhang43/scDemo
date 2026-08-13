package com.example.scproduct.auth;

/**
 * 商品可见范围。四态：
 *   内部服务调用 / 管理员 → 不过滤
 *   顾客               → 只看上架商品（status=1）
 *   商家               → 严格只看自己的商品（merchant_id = uId），上下架均可见
 *
 * 不可变对象：异步导出会把它带到线程池里，避免共享可变状态。
 */
public final class AudienceScope {

    private static final AudienceScope UNRESTRICTED = new AudienceScope(null, false);
    private static final AudienceScope ON_SALE_ONLY = new AudienceScope(null, true);

    /** 非 null 时只返回 merchant_id 等于该值的记录 */
    private final Integer merchantId;
    /** true 时只返回 status=1 的上架商品 */
    private final boolean onSaleOnly;

    private AudienceScope(Integer merchantId, boolean onSaleOnly) {
        this.merchantId = merchantId;
        this.onSaleOnly = onSaleOnly;
    }

    /** 服务间内部调用与管理员：不加任何过滤条件 */
    public static AudienceScope unrestricted() {
        return UNRESTRICTED;
    }

    /** 顾客：下架商品一律不可见 */
    public static AudienceScope customer() {
        return ON_SALE_ONLY;
    }

    /** 商家：严格只含自己的商品，含已下架的 */
    public static AudienceScope merchant(Integer merchantId) {
        return new AudienceScope(merchantId, false);
    }

    public Integer getMerchantId() {
        return merchantId;
    }

    public boolean isOnSaleOnly() {
        return onSaleOnly;
    }

    public boolean isUnrestricted() {
        return merchantId == null && !onSaleOnly;
    }

    /**
     * 是否有权管理（改 / 删 / 补货 / 上下架）指定商品。
     * 顾客无管理权；管理员与内部调用不限；商家严格仅限自己的商品，
     * 归属为 NULL 的存量公共商品只有管理员可管。
     *
     * @param productMerchantId 商品当前的 merchant_id，NULL 表示公共商品
     */
    public boolean canManage(Integer productMerchantId) {
        if (onSaleOnly) {
            return false;
        }
        if (merchantId == null) {
            return true;
        }
        return merchantId.equals(productMerchantId);
    }
}

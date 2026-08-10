package com.example.scorder.service.impl;

import com.curry.model.Address;
import com.curry.model.Product;
import response.ResponseDto;

import java.math.BigDecimal;

/**
 * 订单域内部共用的静态工具方法：Feign 响应成功判定、商品有效价计算、地址快照拼装。
 * 仅供 service.impl 包内使用，避免各实现类重复散落同样的判断逻辑。
 */
final class OrderSupport {

    private OrderSupport() {
        // 工具类禁止实例化
    }

    /**
     * 判断远程调用响应是否成功（非空且 code 为成功码）。
     *
     * @param resp 远程调用响应
     * @return true 表示调用成功
     */
    static boolean isSuccess(ResponseDto<?> resp) {
        return resp != null && ResponseDto.SUCCESS_CODE.equals(resp.getCode());
    }

    /**
     * 商品的服务端有效单价：sc-product 已回填折后价则用它，否则退回原价。
     *
     * @param p 商品（可为 null）
     * @return 有效单价，商品或价格缺失时为 0
     */
    static BigDecimal effectivePriceOf(Product p) {
        if (p == null) {
            return BigDecimal.ZERO;
        }
        if (p.getEffectivePrice() != null) {
            return p.getEffectivePrice();
        }
        return p.getPrice() == null ? BigDecimal.ZERO : BigDecimal.valueOf(p.getPrice());
    }

    /**
     * 把地址对象拼接为单行文本：收件人 + 电话 + 省市区 + 详情。
     *
     * @param addr 地址对象
     * @return 单行地址文本
     */
    static String buildAddressText(Address addr) {
        StringBuilder sb = new StringBuilder();
        if (addr.getConsignee() != null) {
            sb.append(addr.getConsignee()).append(' ');
        }
        if (addr.getPhone() != null) {
            sb.append(addr.getPhone()).append(' ');
        }
        if (addr.getProvince() != null) {
            sb.append(addr.getProvince());
        }
        if (addr.getCity() != null) {
            sb.append(addr.getCity());
        }
        if (addr.getDistrict() != null) {
            sb.append(addr.getDistrict());
        }
        if (addr.getDetail() != null) {
            sb.append(addr.getDetail());
        }
        return sb.toString().trim();
    }
}

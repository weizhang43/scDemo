package com.example.scorder.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.scorder.dto.CartAddRequest;
import com.example.scorder.entity.CartItem;
import com.example.scorder.vo.CartItemVO;
import com.example.scorder.vo.CartMutationVO;
import response.ResponseDto;

import java.util.List;

/**
 * 购物车。只做 CRUD，结算由前端复用 /order/placeOrderV2 编排，故无 checkout 方法。
 * uId 一律由 Controller 从网关注入的 X-User-Id 传入，...Own 后缀表示方法内做了归属校验。
 */
public interface CartService extends IService<CartItem> {

    ResponseDto<CartMutationVO> addToCart(Integer uId, CartAddRequest req);

    ResponseDto<CartItemVO> listOwn(Integer uId);

    ResponseDto<CartMutationVO> updateQuantityOwn(Integer uId, CartAddRequest req);

    /** 删除后 daoResult 回传剩余条目数，前端可直接刷新角标 */
    ResponseDto<Integer> removeOwn(Integer uId, Integer pId);

    ResponseDto<Integer> removeBatchOwn(Integer uId, List<Integer> pIds);

    ResponseDto<Integer> countOwn(Integer uId);
}

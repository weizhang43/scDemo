package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Address;
import response.ResponseDto;


public interface AddressService extends IService<Address> {

    /**
     * 查询某用户的全部收货地址（默认地址在前）
     */
    ResponseDto<Address> listByUser(Integer uId);

    /**
     * 新增收货地址
     */
    ResponseDto<Address> addAddress(Address address);

    /**
     * 修改收货地址
     */
    ResponseDto<Address> updateAddress(Address address);

    /**
     * 删除收货地址
     */
    ResponseDto<Address> removeAddress(Integer aId);

    /**
     * 修改自己的收货地址：先校验地址归属 uId，再更新。
     */
    ResponseDto<Address> updateOwn(Address address, Integer uId);

    /**
     * 删除自己的收货地址：先校验地址归属 uId，再删除。
     */
    ResponseDto<Address> removeOwn(Integer aId, Integer uId);

    /**
     * 将某地址设为当前用户的默认地址
     */
    ResponseDto<Address> setDefault(Integer aId, Integer uId);
}

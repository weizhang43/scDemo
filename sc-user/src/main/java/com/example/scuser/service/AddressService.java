package com.example.scuser.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.curry.model.Address;
import response.ResponseDto;

import java.util.List;

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
     * 将某地址设为当前用户的默认地址
     */
    ResponseDto<Address> setDefault(Integer aId, Integer uId);
}

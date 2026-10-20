package com.example.scuser.controller;

import com.curry.model.Address;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import response.ResponseDto;

@RestController
@RequestMapping(value = "/user/address")
public class AddressController {

    @Autowired
    private AddressService addressService;

    @GetMapping("/list")
    public ResponseDto<Address> list(
            @RequestParam(value = "uId", required = false) Integer targetUId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer operatorUId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer operatorType) {
        if (operatorUId == null) return ResponseDto.error("未登录");
        if (isAdmin(operatorType) && targetUId != null) {
            return addressService.listByUser(targetUId);
        }
        return addressService.listByUser(operatorUId);
    }

    @GetMapping("/{aId}")
    public ResponseDto<Address> get(
            @PathVariable("aId") Integer aId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        Address address = addressService.getById(aId);
        if (address == null || uId == null) {
            return ResponseDto.error("地址不存在");
        }
        if (!isAdmin(uType) && !uId.equals(address.getUId())) {
            return ResponseDto.error("地址不存在");
        }
        return ResponseDto.success(address);
    }

    @PostMapping
    public ResponseDto<Address> add(
            @RequestBody Address address,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uId == null) return ResponseDto.error("未登录");
        if (!isAdmin(uType)) {
            address.setUId(uId);
        } else if (address.getUId() == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        return addressService.addAddress(address);
    }

    @PutMapping
    public ResponseDto<Address> update(
            @RequestBody Address address,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (isAdmin(uType)) return addressService.updateAddress(address);
        return addressService.updateOwn(address, uId);
    }

    @DeleteMapping("/{aId}")
    public ResponseDto<Address> delete(
            @PathVariable("aId") Integer aId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (isAdmin(uType)) return addressService.removeAddress(aId);
        return addressService.removeOwn(aId, uId);
    }

    @PostMapping("/default")
    public ResponseDto<Address> setDefault(
            @RequestParam("aId") Integer aId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (isAdmin(uType)) {
            Address address = addressService.getById(aId);
            return address == null ? ResponseDto.error("地址不存在") : addressService.setDefault(aId, address.getUId());
        }
        return addressService.setDefault(aId, uId);
    }

    private boolean isAdmin(Integer uType) {
        return uType != null && uType == AuthConstant.U_TYPE_ADMIN;
    }
}

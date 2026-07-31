package com.example.scuser.controller;

import com.curry.model.Address;
import com.curry.model.User;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.service.AddressService;
import com.example.scuser.service.UserService;
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

/**
 * 当前登录用户的自助接口。身份一律取自网关注入的 X-User-Id，忽略请求体/参数里的 uId，
 * 因此顾客无法通过篡改 uId 读写他人资料。
 * 管理员代改他人资料仍走 /user/profile 与 /user/address/*，那两组接口保持原语义。
 */
@RestController
@RequestMapping(value = "/user/me")
public class UserSelfController {

    @Autowired
    private UserService userService;

    @Autowired
    private AddressService addressService;

    @GetMapping("/profile")
    public ResponseDto<User> profile(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return userService.getDetailById(uId);
    }

    @PutMapping("/profile")
    public ResponseDto<User> updateProfile(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody User user) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        user.setUId(uId);
        return userService.updateProfile(user);
    }

    @GetMapping("/address/list")
    public ResponseDto<Address> addressList(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return addressService.listByUser(uId);
    }

    @PostMapping("/address")
    public ResponseDto<Address> addAddress(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody Address address) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        address.setUId(uId);
        return addressService.addAddress(address);
    }

    @PutMapping("/address")
    public ResponseDto<Address> updateAddress(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestBody Address address) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return addressService.updateOwn(address, uId);
    }

    @DeleteMapping("/address/{aId}")
    public ResponseDto<Address> deleteAddress(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @PathVariable("aId") Integer aId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return addressService.removeOwn(aId, uId);
    }

    @PostMapping("/address/default")
    public ResponseDto<Address> setDefaultAddress(
            @RequestHeader(value = AuthConstant.HEADER_X_USER_ID, required = false) Integer uId,
            @RequestParam("aId") Integer aId) {
        if (uId == null) {
            return ResponseDto.error("未登录");
        }
        return addressService.setDefault(aId, uId);
    }
}

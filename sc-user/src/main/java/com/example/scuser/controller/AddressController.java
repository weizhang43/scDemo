package com.example.scuser.controller;

import com.curry.model.Address;
import com.example.scuser.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import response.ResponseDto;

@RestController
@RequestMapping(value = "/user/address")
public class AddressController {

    @Autowired
    private AddressService addressService;

    @GetMapping("/list")
    public ResponseDto<Address> list(@RequestParam("uId") Integer uId) {
        return addressService.listByUser(uId);
    }

    @GetMapping("/{aId}")
    public ResponseDto<Address> get(@PathVariable("aId") Integer aId) {
        return addressService.getById(aId) == null
                ? ResponseDto.error("地址不存在")
                : ResponseDto.success(addressService.getById(aId));
    }

    @PostMapping
    public ResponseDto<Address> add(@RequestBody Address address) {
        return addressService.addAddress(address);
    }

    @PutMapping
    public ResponseDto<Address> update(@RequestBody Address address) {
        return addressService.updateAddress(address);
    }

    @DeleteMapping("/{aId}")
    public ResponseDto<Address> delete(@PathVariable("aId") Integer aId) {
        return addressService.removeAddress(aId);
    }

    @PostMapping("/default")
    public ResponseDto<Address> setDefault(@RequestParam("aId") Integer aId,
                                          @RequestParam("uId") Integer uId) {
        return addressService.setDefault(aId, uId);
    }
}

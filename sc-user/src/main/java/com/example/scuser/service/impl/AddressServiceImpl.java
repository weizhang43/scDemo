package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Address;
import com.example.scuser.mapper.AddressMapper;
import com.example.scuser.service.AddressService;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.util.Date;
import java.util.List;

@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    @Override
    public ResponseDto<Address> listByUser(Integer uId) {
        if (uId == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<Address>()
                .eq(Address::getUId, uId)
                .orderByDesc(Address::getIsDefault)
                .orderByAsc(Address::getAId);
        List<Address> list = baseMapper.selectList(wrapper);
        return ResponseDto.success(list);
    }

    @Override
    public ResponseDto<Address> addAddress(Address address) {
        if (address.getUId() == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefaultOfUser(address.getUId(), null);
        }
        address.setAId(null);
        address.setCreateTime(new Date());
        if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }
        baseMapper.insert(address);
        return ResponseDto.success(address);
    }

    @Override
    public ResponseDto<Address> updateAddress(Address address) {
        if (address.getAId() == null) {
            return ResponseDto.error("地址ID不能为空");
        }
        Address exists = baseMapper.selectById(address.getAId());
        if (exists == null) {
            return ResponseDto.error("地址不存在");
        }
        if (!exists.getUId().equals(address.getUId() == null ? exists.getUId() : address.getUId())) {
            return ResponseDto.error("无权修改该地址");
        }
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefaultOfUser(exists.getUId(), address.getAId());
        }
        baseMapper.updateById(address);
        Address latest = baseMapper.selectById(address.getAId());
        return ResponseDto.success(latest);
    }

    @Override
    public ResponseDto<Address> removeAddress(Integer aId) {
        if (aId == null) {
            return ResponseDto.error("地址ID不能为空");
        }
        int rows = baseMapper.deleteById(aId);
        return rows > 0 ? ResponseDto.success(null) : ResponseDto.error("地址不存在或已删除");
    }

    @Override
    public ResponseDto<Address> updateOwn(Address address, Integer uId) {
        if (address == null || address.getAId() == null) {
            return ResponseDto.error("地址ID不能为空");
        }
        ResponseDto<Address> denied = checkOwnership(address.getAId(), uId);
        if (denied != null) {
            return denied;
        }
        // 归属以库中记录为准，不接受调用方传入的 uId
        address.setUId(uId);
        return updateAddress(address);
    }

    @Override
    public ResponseDto<Address> removeOwn(Integer aId, Integer uId) {
        ResponseDto<Address> denied = checkOwnership(aId, uId);
        if (denied != null) {
            return denied;
        }
        return removeAddress(aId);
    }

    /**
     * 校验地址归属：通过返回 null，否则返回对应的错误响应。
     */
    private ResponseDto<Address> checkOwnership(Integer aId, Integer uId) {
        if (aId == null || uId == null) {
            return ResponseDto.error("地址ID与用户ID均不能为空");
        }
        Address exists = baseMapper.selectById(aId);
        if (exists == null) {
            return ResponseDto.error("地址不存在");
        }
        if (!uId.equals(exists.getUId())) {
            return ResponseDto.error("无权操作该地址");
        }
        return null;
    }

    @Override
    public ResponseDto<Address> setDefault(Integer aId, Integer uId) {
        if (aId == null || uId == null) {
            return ResponseDto.error("地址ID与用户ID均不能为空");
        }
        Address exists = baseMapper.selectById(aId);
        if (exists == null || !exists.getUId().equals(uId)) {
            return ResponseDto.error("地址不存在或不属于该用户");
        }
        clearDefaultOfUser(uId, aId);
        Address update = new Address();
        update.setAId(aId);
        update.setIsDefault(1);
        baseMapper.updateById(update);
        return ResponseDto.success(null);
    }

    /**
     * 清除指定用户的默认地址标记。excludeAId 用于在更新/新增某地址为默认时，跳过该地址本身。
     */
    private void clearDefaultOfUser(Integer uId, Integer excludeAId) {
        LambdaUpdateWrapper<Address> update = new LambdaUpdateWrapper<Address>()
                .eq(Address::getUId, uId)
                .eq(Address::getIsDefault, 1)
                .set(Address::getIsDefault, 0);
        if (excludeAId != null) {
            update.ne(Address::getAId, excludeAId);
        }
        baseMapper.update(null, update);
    }
}

package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.UserRole;
import com.example.scuser.mapper.UserRoleMapper;
import com.example.scuser.service.UserRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import response.ResponseDto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {

    @Override
    public ResponseDto<Integer> listRoleIdsByUserId(Integer userId) {
        if (userId == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId);
        List<UserRole> list = baseMapper.selectList(wrapper);
        List<Integer> ids = new ArrayList<Integer>();
        for (UserRole ur : list) {
            ids.add(ur.getRoleId());
        }
        return ResponseDto.success(ids);
    }

    @Override
    @Transactional
    public ResponseDto<Void> assignRoles(Integer userId, List<Integer> roleIds) {
        if (userId == null) {
            return ResponseDto.error("用户ID不能为空");
        }
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId);
        baseMapper.delete(wrapper);
        if (roleIds != null && !roleIds.isEmpty()) {
            Date now = new Date();
            for (Integer roleId : roleIds) {
                if (roleId == null) {
                    continue;
                }
                UserRole ur = new UserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                ur.setCreateTime(now);
                baseMapper.insert(ur);
            }
        }
        return ResponseDto.success(null);
    }
}

package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Role;
import com.curry.model.RoleModule;
import com.example.scuser.mapper.RoleMapper;
import com.example.scuser.mapper.RoleModuleMapper;
import com.example.scuser.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import response.ResponseDto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    @Autowired
    private RoleModuleMapper roleModuleMapper;

    @Override
    public ResponseDto<Role> listAll() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .orderByAsc(Role::getId);
        List<Role> list = baseMapper.selectList(wrapper);
        return ResponseDto.success(list);
    }

    @Override
    public ResponseDto<Role> add(Role role) {
        if (role.getCode() == null || role.getName() == null) {
            return ResponseDto.error("编码和名称必填");
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        role.setCreateTime(new Date());
        role.setUpdateTime(new Date());
        baseMapper.insert(role);
        return ResponseDto.success(role);
    }

    @Override
    public ResponseDto<Role> update(Role role) {
        if (role.getId() == null) {
            return ResponseDto.error("ID不能为空");
        }
        role.setUpdateTime(new Date());
        baseMapper.updateById(role);
        return ResponseDto.success(role);
    }

    @Override
    @Transactional
    public ResponseDto<Role> remove(Integer id) {
        if (id == null) {
            return ResponseDto.error("ID不能为空");
        }
        // 先清空该角色的权限关联
        LambdaQueryWrapper<RoleModule> wrapper = new LambdaQueryWrapper<RoleModule>()
                .eq(RoleModule::getRoleId, id);
        roleModuleMapper.delete(wrapper);
        baseMapper.deleteById(id);
        return ResponseDto.success(null);
    }

    @Override
    @Transactional
    public ResponseDto<Void> assignModules(Integer roleId, List<Integer> moduleIds) {
        if (roleId == null) {
            return ResponseDto.error("角色ID不能为空");
        }
        LambdaQueryWrapper<RoleModule> wrapper = new LambdaQueryWrapper<RoleModule>()
                .eq(RoleModule::getRoleId, roleId);
        roleModuleMapper.delete(wrapper);
        if (moduleIds != null && !moduleIds.isEmpty()) {
            Date now = new Date();
            for (Integer moduleId : moduleIds) {
                if (moduleId == null) {
                    continue;
                }
                RoleModule rm = new RoleModule();
                rm.setRoleId(roleId);
                rm.setModuleId(moduleId);
                rm.setCreateTime(now);
                roleModuleMapper.insert(rm);
            }
        }
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<Integer> listModuleIdsByRoleId(Integer roleId) {
        if (roleId == null) {
            return ResponseDto.error("角色ID不能为空");
        }
        LambdaQueryWrapper<RoleModule> wrapper = new LambdaQueryWrapper<RoleModule>()
                .eq(RoleModule::getRoleId, roleId);
        List<RoleModule> list = roleModuleMapper.selectList(wrapper);
        List<Integer> ids = new ArrayList<Integer>();
        for (RoleModule rm : list) {
            ids.add(rm.getModuleId());
        }
        return ResponseDto.success(ids);
    }
}

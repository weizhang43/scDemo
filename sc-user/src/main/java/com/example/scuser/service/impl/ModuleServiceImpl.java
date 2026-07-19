package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Module;
import com.example.scuser.mapper.ModuleMapper;
import com.example.scuser.service.ModuleService;
import org.springframework.stereotype.Service;
import response.ResponseDto;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModuleServiceImpl extends ServiceImpl<ModuleMapper, Module> implements ModuleService {

    @Override
    public ResponseDto<Module> listAll() {
        LambdaQueryWrapper<Module> wrapper = new LambdaQueryWrapper<Module>()
                .orderByAsc(Module::getSort)
                .orderByAsc(Module::getId);
        List<Module> list = baseMapper.selectList(wrapper);
        return ResponseDto.success(list);
    }

    @Override
    public ResponseDto<Module> listTree() {
        LambdaQueryWrapper<Module> wrapper = new LambdaQueryWrapper<Module>()
                .orderByAsc(Module::getSort)
                .orderByAsc(Module::getId);
        List<Module> all = baseMapper.selectList(wrapper);
        List<Module> roots = buildTree(all);
        return ResponseDto.success(roots);
    }

    private List<Module> buildTree(List<Module> all) {
        Map<Integer, Module> map = new HashMap<Integer, Module>();
        for (Module m : all) {
            m.setChildren(new ArrayList<Module>());
            map.put(m.getId(), m);
        }
        List<Module> roots = new ArrayList<Module>();
        for (Module m : all) {
            Integer pid = m.getParentId();
            if (pid == null || pid == 0 || !map.containsKey(pid)) {
                roots.add(m);
            } else {
                map.get(pid).getChildren().add(m);
            }
        }
        return roots;
    }

    @Override
    public ResponseDto<Module> add(Module module) {
        if (module.getName() == null) {
            return ResponseDto.error("名称必填");
        }
        if (module.getParentId() == null) {
            module.setParentId(0);
        }
        if (module.getType() == null) {
            module.setType("MENU");
        }
        if (module.getSort() == null) {
            module.setSort(0);
        }
        if (module.getStatus() == null) {
            module.setStatus(1);
        }
        module.setCreateTime(new Date());
        module.setUpdateTime(new Date());
        baseMapper.insert(module);
        return ResponseDto.success(module);
    }

    @Override
    public ResponseDto<Module> update(Module module) {
        if (module.getId() == null) {
            return ResponseDto.error("ID不能为空");
        }
        module.setUpdateTime(new Date());
        baseMapper.updateById(module);
        return ResponseDto.success(module);
    }

    @Override
    public ResponseDto<Module> remove(Integer id) {
        if (id == null) {
            return ResponseDto.error("ID不能为空");
        }
        baseMapper.deleteById(id);
        return ResponseDto.success(null);
    }
}

package com.example.scuser.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.curry.model.Region;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.mapper.RegionMapper;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 区域管理：省市区三级级联树查询、CRUD 及中国主要省市初始化。
 */
@RestController
@RequestMapping("/user/region")
public class RegionController {

    private static final String MSG_NO_PERMISSION = "无权限";
    private static final int LEVEL_CITY = 2;
    private static final int LEVEL_DISTRICT = 3;

    @Autowired
    private RegionMapper regionMapper;

    /** 返回完整省市区级联树 */
    @GetMapping("/tree")
    public ResponseDto<Object> tree() {
        List<Region> all = regionMapper.selectList(new LambdaQueryWrapper<Region>().orderByAsc(Region::getSortOrder));
        List<Map<String, Object>> tree = buildTree(all, 0L);
        ResponseDto<Object> resp = new ResponseDto<>();
        resp.setCode(ResponseDto.SUCCESS_CODE);
        resp.setDaoResult(tree);
        return resp;
    }

    /** 按父级ID查子节点 */
    @GetMapping("/children")
    public ResponseDto<Region> children(@RequestParam(value = "parentId", defaultValue = "0") Long parentId) {
        List<Region> list = regionMapper.selectList(
                new LambdaQueryWrapper<Region>().eq(Region::getParentId, parentId).orderByAsc(Region::getSortOrder));
        return ResponseDto.success(list);
    }

    /** 管理员新增 */
    @PostMapping
    public ResponseDto<Region> add(@RequestBody Region region,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        regionMapper.insert(region);
        return ResponseDto.success(null);
    }

    /** 管理员修改 */
    @PutMapping("/{id}")
    public ResponseDto<Region> update(@PathVariable Long id, @RequestBody Region region,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        region.setId(id);
        regionMapper.updateById(region);
        return ResponseDto.success(null);
    }

    /** 管理员删除（含子节点） */
    @DeleteMapping("/{id}")
    public ResponseDto<Region> delete(@PathVariable Long id,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        deleteCascade(id);
        return ResponseDto.success(null);
    }

    /** 初始化省市区数据（管理员触发）。表已有数据时返回提示但不算失败。 */
    @PostMapping("/init")
    public ResponseDto<Region> init(@RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        long exist = regionMapper.selectCount(null);
        if (exist > 0) {
            ResponseDto<Region> resp = new ResponseDto<>();
            resp.setCode(ResponseDto.SUCCESS_CODE);
            resp.setDaoResult("已存在 " + exist + " 条区域数据，无需重复初始化");
            return resp;
        }
        seedChinaRegions();
        ResponseDto<Region> resp = new ResponseDto<>();
        resp.setCode(ResponseDto.SUCCESS_CODE);
        resp.setDaoResult("初始化成功");
        return resp;
    }

    private List<Map<String, Object>> buildTree(List<Region> all, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Region r : all) {
            if (r.getParentId() == null ? parentId == null : r.getParentId().equals(parentId)) {
                Map<String, Object> node = new HashMap<>();
                node.put("value", r.getId());
                node.put("label", r.getName());
                node.put("level", r.getLevel());
                List<Map<String, Object>> children = buildTree(all, r.getId());
                if (!children.isEmpty()) node.put("children", children);
                nodes.add(node);
            }
        }
        return nodes;
    }

    private void deleteCascade(Long parentId) {
        List<Region> children = regionMapper.selectList(
                new LambdaQueryWrapper<Region>().eq(Region::getParentId, parentId));
        for (Region child : children) deleteCascade(child.getId());
        regionMapper.deleteById(parentId);
    }

    /** 预置中国主要省市区数据（示例：含主流省市及对应城市区县） */
    private void seedChinaRegions() {
        long sort = 0;
        long beijing = insertRegion("北京市", 0L, 1, ++sort);
        long bjCity = insertRegion("北京市", beijing, LEVEL_CITY, 1);
        insertRegion("东城区", bjCity, LEVEL_DISTRICT, 1);
        insertRegion("西城区", bjCity, LEVEL_DISTRICT, 2);
        insertRegion("朝阳区", bjCity, LEVEL_DISTRICT, 3);
        insertRegion("海淀区", bjCity, LEVEL_DISTRICT, 4);
        long shanghai = insertRegion("上海市", 0L, 1, ++sort);
        long shCity = insertRegion("上海市", shanghai, LEVEL_CITY, 1);
        insertRegion("黄浦区", shCity, LEVEL_DISTRICT, 1);
        insertRegion("徐汇区", shCity, LEVEL_DISTRICT, 2);
        insertRegion("浦东新区", shCity, LEVEL_DISTRICT, 3);
        long guangdong = insertRegion("广东省", 0L, 1, ++sort);
        long gz = insertRegion("广州市", guangdong, LEVEL_CITY, 1);
        insertRegion("越秀区", gz, LEVEL_DISTRICT, 1);
        insertRegion("天河区", gz, LEVEL_DISTRICT, 2);
        long sz = insertRegion("深圳市", guangdong, LEVEL_CITY, 2);
        insertRegion("福田区", sz, LEVEL_DISTRICT, 1);
        insertRegion("南山区", sz, LEVEL_DISTRICT, 2);
        insertRegion("宝安区", sz, LEVEL_DISTRICT, 3);
        long zhejiang = insertRegion("浙江省", 0L, 1, ++sort);
        long hz = insertRegion("杭州市", zhejiang, LEVEL_CITY, 1);
        insertRegion("西湖区", hz, LEVEL_DISTRICT, 1);
        insertRegion("余杭区", hz, LEVEL_DISTRICT, 2);
        long sichuan = insertRegion("四川省", 0L, 1, ++sort);
        long cd = insertRegion("成都市", sichuan, LEVEL_CITY, 1);
        insertRegion("武侯区", cd, LEVEL_DISTRICT, 1);
        insertRegion("高新区", cd, LEVEL_DISTRICT, 2);
        long hubei = insertRegion("湖北省", 0L, 1, ++sort);
        long wh = insertRegion("武汉市", hubei, LEVEL_CITY, 1);
        insertRegion("洪山区", wh, LEVEL_DISTRICT, 1);
        insertRegion("武昌区", wh, LEVEL_DISTRICT, 2);
        long jiangsu = insertRegion("江苏省", 0L, 1, ++sort);
        long nj = insertRegion("南京市", jiangsu, LEVEL_CITY, 1);
        insertRegion("鼓楼区", nj, LEVEL_DISTRICT, 1);
        insertRegion("江宁区", nj, LEVEL_DISTRICT, 2);
    }

    private long insertRegion(String name, Long parentId, Integer level, long sortOrder) {
        Region r = new Region();
        r.setName(name);
        r.setParentId(parentId);
        r.setLevel(level);
        r.setSortOrder((int) sortOrder);
        regionMapper.insert(r);
        return r.getId();
    }
}
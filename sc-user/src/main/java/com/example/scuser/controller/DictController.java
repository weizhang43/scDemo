package com.example.scuser.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.curry.model.SysDictData;
import com.curry.model.SysDictType;
import com.curry.model.auth.AuthConstant;
import com.example.scuser.mapper.SysDictDataMapper;
import com.example.scuser.mapper.SysDictTypeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 数据字典：管理员可维护字典类型与字典数据，其他页面可通过 /batch 接口批量获取下拉框选项。
 */
@RestController
@RequestMapping("/user/dict")
public class DictController {

    private static final Logger LOG = LoggerFactory.getLogger(DictController.class);
    private static final String MSG_NO_PERMISSION = "无权限";
    private static final String DICT_CACHE_KEY_PREFIX = "dict:batch:";
    private static final long DICT_CACHE_TTL_MINUTES = 30;

    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    @Autowired
    private SysDictDataMapper dictDataMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ────────── 字典类型 ──────────

    /** 字典类型分页查询，支持按名称模糊筛选。仅管理员可操作。 */
    @GetMapping("/type/page")
    public ResponseDto<SysDictType> typePage(
            @RequestParam(value = "dictName", required = false) String dictName,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        LambdaQueryWrapper<SysDictType> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dictName)) w.like(SysDictType::getDictName, dictName);
        w.orderByDesc(SysDictType::getDictId);
        Page<SysDictType> page = dictTypeMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return ResponseDto.success(page);
    }

    /** 新增字典类型，默认启用。仅管理员可操作。 */
    @PostMapping("/type")
    public ResponseDto<SysDictType> addType(@RequestBody SysDictType t,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        t.setCreateTime(new Date());
        t.setStatus(1);
        dictTypeMapper.insert(t);
        return ResponseDto.success(null);
    }

    /** 更新字典类型，同时驱逐该类型下的字典数据缓存。仅管理员可操作。 */
    @PutMapping("/type")
    public ResponseDto<SysDictType> updateType(@RequestBody SysDictType t,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        t.setUpdateTime(new Date());
        dictTypeMapper.updateById(t);
        evictDictDataCache(t.getDictType());
        return ResponseDto.success(null);
    }

    /** 删除字典类型及其所有字典数据，并驱逐缓存。仅管理员可操作。 */
    @DeleteMapping("/type/{id}")
    public ResponseDto<SysDictType> deleteType(@PathVariable Long id,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        SysDictType type = dictTypeMapper.selectById(id);
        if (type != null) {
            dictTypeMapper.deleteById(id);
            dictDataMapper.delete(new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictType,
                    type.getDictType()));
            evictDictDataCache(type.getDictType());
        }
        return ResponseDto.success(null);
    }

    // ────────── 字典数据 ──────────

    /** 按字典类型查询字典数据列表。 */
    @GetMapping("/data/list")
    public ResponseDto<SysDictData> dataList(@RequestParam("dictType") String dictType) {
        LambdaQueryWrapper<SysDictData> w = new LambdaQueryWrapper<>();
        w.eq(SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getSortOrder);
        return ResponseDto.success(dictDataMapper.selectList(w));
    }

    /** 批量查询多个字典类型的数据，优先从 Redis 缓存读取，缓存未命中再查库并回填缓存。 */
    @GetMapping("/data/batch")
    public ResponseDto<Object> dataBatch(@RequestParam("types") String types) {
        Map<String, List<SysDictData>> result = new HashMap<>();
        for (String type : types.split(",")) {
            String t = type.trim();
            if (t.isEmpty()) continue;
            List<SysDictData> cached = getDictDataFromCache(t);
            if (cached != null) {
                result.put(t, cached);
                continue;
            }
            List<SysDictData> list = dictDataMapper.selectList(
                    new LambdaQueryWrapper<SysDictData>()
                            .eq(SysDictData::getDictType, t)
                            .eq(SysDictData::getStatus, 1)
                            .orderByAsc(SysDictData::getSortOrder));
            result.put(t, list);
            putDictDataToCache(t, list);
        }
        ResponseDto<Object> resp = new ResponseDto<>();
        resp.setCode(ResponseDto.SUCCESS_CODE);
        resp.setDaoResult(result);
        return resp;
    }

    /** 新增字典数据，默认启用，新增后驱逐该类型的缓存。仅管理员可操作。 */
    @PostMapping("/data")
    public ResponseDto<SysDictData> addData(@RequestBody SysDictData d,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        d.setCreateTime(new Date());
        d.setStatus(1);
        dictDataMapper.insert(d);
        evictDictDataCache(d.getDictType());
        return ResponseDto.success(null);
    }

    /** 更新字典数据并驱逐缓存。仅管理员可操作。 */
    @PutMapping("/data")
    public ResponseDto<SysDictData> updateData(@RequestBody SysDictData d,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        d.setUpdateTime(new Date());
        dictDataMapper.updateById(d);
        evictDictDataCache(d.getDictType());
        return ResponseDto.success(null);
    }

    /** 删除字典数据并驱逐缓存。仅管理员可操作。 */
    @DeleteMapping("/data/{id}")
    public ResponseDto<SysDictData> deleteData(@PathVariable Long id,
            @RequestHeader(value = AuthConstant.HEADER_X_USER_TYPE, required = false) Integer uType) {
        if (uType == null || uType != AuthConstant.U_TYPE_ADMIN) return ResponseDto.error(MSG_NO_PERMISSION);
        SysDictData data = dictDataMapper.selectById(id);
        if (data != null) {
            dictDataMapper.deleteById(id);
            evictDictDataCache(data.getDictType());
        }
        return ResponseDto.success(null);
    }

    // ────────── Redis 缓存辅助 ──────────

    /**
     * 从 Redis 读取字典数据缓存，key = dict:batch:{dictType}。
     * Redis 不可用或反序列化失败时返回 null，由调用方回退查库。
     */
    private List<SysDictData> getDictDataFromCache(String dictType) {
        if (stringRedisTemplate == null) return null;
        try {
            String json = stringRedisTemplate.opsForValue().get(DICT_CACHE_KEY_PREFIX + dictType);
            if (json == null || json.isEmpty()) return null;
            return objectMapper.readValue(json, new TypeReference<List<SysDictData>>() {});
        } catch (Exception e) {
            LOG.warn("读取字典缓存失败 dictType={}", dictType, e);
            return null;
        }
    }

    /** 将字典数据写入 Redis 缓存，序列化失败仅记录日志不中断业务流程。 */
    private void putDictDataToCache(String dictType, List<SysDictData> list) {
        if (stringRedisTemplate == null) return;
        try {
            String json = objectMapper.writeValueAsString(list);
            stringRedisTemplate.opsForValue().set(DICT_CACHE_KEY_PREFIX + dictType, json,
                    DICT_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            LOG.warn("写入字典缓存失败 dictType={}", dictType, e);
        }
    }

    /** 删除指定字典类型的 Redis 缓存。 */
    private void evictDictDataCache(String dictType) {
        if (stringRedisTemplate == null || dictType == null) return;
        try {
            stringRedisTemplate.delete(DICT_CACHE_KEY_PREFIX + dictType);
        } catch (Exception e) {
            LOG.warn("删除字典缓存失败 dictType={}", dictType, e);
        }
    }
}
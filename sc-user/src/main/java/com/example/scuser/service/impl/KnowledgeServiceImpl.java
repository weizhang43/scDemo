package com.example.scuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Knowledge;
import com.curry.model.KnowledgeNote;
import com.example.scuser.mapper.KnowledgeMapper;
import com.example.scuser.mapper.KnowledgeNoteMapper;
import com.example.scuser.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import java.util.Date;
import java.util.List;

/**
 * 知识速记服务：知识点的新增、顺序浏览、查看进度、收藏、忽略与笔记。
 */
@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {

    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_FAVORITE = 2;

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    @Autowired
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Override
    public ResponseDto<Knowledge> add(Knowledge knowledge) {
        if (!StringUtils.hasText(knowledge.getQuestion())) {
            return ResponseDto.error("题干不能为空");
        }
        if (!StringUtils.hasText(knowledge.getAnswer())) {
            return ResponseDto.error("答案不能为空");
        }
        if (knowledge.getTag() == null || knowledge.getTag() < 1 || knowledge.getTag() > 7) {
            return ResponseDto.error("标签不合法");
        }
        Date now = new Date();
        knowledge.setId(null);
        knowledge.setStatus(STATUS_NORMAL);
        knowledge.setDelFlag(NOT_DELETED);
        knowledge.setViewCount(0);
        knowledge.setLastViewTime(null);
        knowledge.setAddTime(now);
        baseMapper.insert(knowledge);
        return ResponseDto.success(knowledge);
    }

    @Override
    public ResponseDto<Knowledge> next(Long currentId, Integer tag) {
        Long cursor = currentId == null ? 0L : currentId;
        Knowledge next = selectNextAfter(cursor, tag);
        // 已经刷到最后一题，则回到开头继续循环
        if (next == null) {
            next = selectNextAfter(0L, tag);
        }
        return next == null ? ResponseDto.error("暂无知识点，请先添加") : ResponseDto.success(next);
    }

    @Override
    public ResponseDto<Knowledge> getById(Long id) {
        Knowledge knowledge = requireNotDeleted(id);
        return knowledge == null ? ResponseDto.error("知识点不存在") : ResponseDto.success(knowledge);
    }

    @Override
    public ResponseDto<Knowledge> search(String keyword, Integer tag) {
        if (!StringUtils.hasText(keyword)) {
            return ResponseDto.error("关键字不能为空");
        }
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getDelFlag, NOT_DELETED)
                .like(Knowledge::getQuestion, keyword.trim())
                .orderByAsc(Knowledge::getId)
                .last("LIMIT 20");
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        return ResponseDto.success(baseMapper.selectList(wrapper));
    }

    @Override
    public ResponseDto<Knowledge> prev(Long currentId, Integer tag) {
        Long cursor = currentId == null ? Long.MAX_VALUE : currentId;
        Knowledge prev = selectPrevBefore(cursor, tag);
        // 已经刷到第一题，则跳到最后一题
        if (prev == null) {
            prev = selectPrevBefore(Long.MAX_VALUE, tag);
        }
        return prev == null ? ResponseDto.error("暂无知识点，请先添加") : ResponseDto.success(prev);
    }

    @Override
    public ResponseDto<Knowledge> view(Long id) {
        Knowledge exist = requireNotDeleted(id);
        if (exist == null) {
            return ResponseDto.error("知识点不存在");
        }
        Knowledge update = new Knowledge();
        update.setId(id);
        update.setViewCount((exist.getViewCount() == null ? 0 : exist.getViewCount()) + 1);
        update.setLastViewTime(new Date());
        baseMapper.updateById(update);
        return ResponseDto.success(baseMapper.selectById(id));
    }

    @Override
    public ResponseDto<Knowledge> toggleFavorite(Long id) {
        Knowledge exist = requireNotDeleted(id);
        if (exist == null) {
            return ResponseDto.error("知识点不存在");
        }
        Knowledge update = new Knowledge();
        update.setId(id);
        update.setStatus(exist.getStatus() != null && exist.getStatus() == STATUS_FAVORITE
                ? STATUS_NORMAL : STATUS_FAVORITE);
        baseMapper.updateById(update);
        return ResponseDto.success(baseMapper.selectById(id));
    }

    @Override
    public ResponseDto<Knowledge> ignore(Long id) {
        Knowledge exist = requireNotDeleted(id);
        if (exist == null) {
            return ResponseDto.error("知识点不存在");
        }
        Knowledge update = new Knowledge();
        update.setId(id);
        update.setDelFlag(DELETED);
        baseMapper.updateById(update);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<KnowledgeNote> addNote(Long knowledgeId, String content) {
        if (!StringUtils.hasText(content)) {
            return ResponseDto.error("笔记内容不能为空");
        }
        if (requireNotDeleted(knowledgeId) == null) {
            return ResponseDto.error("知识点不存在");
        }
        KnowledgeNote note = new KnowledgeNote();
        note.setKnowledgeId(knowledgeId);
        note.setContent(content.trim());
        note.setCreateTime(new Date());
        knowledgeNoteMapper.insert(note);
        return ResponseDto.success(note);
    }

    @Override
    public ResponseDto<KnowledgeNote> listNotes(Long knowledgeId) {
        List<KnowledgeNote> notes = knowledgeNoteMapper.selectList(new LambdaQueryWrapper<KnowledgeNote>()
                .eq(KnowledgeNote::getKnowledgeId, knowledgeId)
                .orderByDesc(KnowledgeNote::getCreateTime));
        return ResponseDto.success(notes);
    }

    /** 查询 id 大于 cursor、未删除（tag 非空时同标签）的第一条知识点 */
    private Knowledge selectNextAfter(Long cursor, Integer tag) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getDelFlag, NOT_DELETED)
                .gt(Knowledge::getId, cursor);
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        return baseMapper.selectOne(wrapper.orderByAsc(Knowledge::getId).last("LIMIT 1"));
    }

    /** 查询 id 小于 cursor、未删除（tag 非空时同标签）的第一条知识点（按 id 倒序） */
    private Knowledge selectPrevBefore(Long cursor, Integer tag) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getDelFlag, NOT_DELETED)
                .lt(Knowledge::getId, cursor);
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        return baseMapper.selectOne(wrapper.orderByDesc(Knowledge::getId).last("LIMIT 1"));
    }

    private Knowledge requireNotDeleted(Long id) {
        if (id == null) {
            return null;
        }
        Knowledge knowledge = baseMapper.selectById(id);
        if (knowledge == null || (knowledge.getDelFlag() != null && knowledge.getDelFlag() == DELETED)) {
            return null;
        }
        return knowledge;
    }
}

package com.example.scuser.service.impl;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.curry.model.Knowledge;
import com.curry.model.KnowledgeAction;
import com.curry.model.KnowledgeDailyStat;
import com.curry.model.KnowledgeNote;
import com.curry.model.KnowledgeQuestionTopItem;
import com.curry.model.KnowledgeQuestionTopStat;
import com.curry.model.KnowledgeTagDetailStat;
import com.curry.model.KnowledgeTagStat;
import com.example.scuser.mapper.KnowledgeActionMapper;
import com.example.scuser.mapper.KnowledgeMapper;
import com.example.scuser.mapper.KnowledgeNoteMapper;
import com.example.scuser.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import response.ResponseDto;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识速记服务：知识点的新增、顺序浏览、查看进度、收藏、忽略与笔记。
 */
@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {

    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_FAVORITE = 2;

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    /**
     * 已收藏类型
     */
    private static final String FAVORITE_TYPE = "favorite";
    /**
     * 已添加笔记类型
     */
    private static final String NOTE_TYPE = "note";
    /**
     * 已忽略类型
     */
    private static final String IGNORED_TYPE = "ignored";
    /**
     * 所有题（含已掌握）
     */
    private static final String ALL_TYPE = "all";
    /**
     * 已收藏状态
     */
    private static final Integer COLLECT_STATUS = 2;

    /**
     * 已忽略试题
     */
    private static final Integer DEL_STATUS = 1;

    @Autowired
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Autowired
    private KnowledgeActionMapper knowledgeActionMapper;

    @Override
    public ResponseDto<Knowledge> add(Knowledge knowledge) {
        if (!StringUtils.hasText(knowledge.getQuestion())) {
            return ResponseDto.error("题干不能为空");
        }
        if (!StringUtils.hasText(knowledge.getAnswer())) {
            return ResponseDto.error("答案不能为空");
        }
        if (knowledge.getTag() == null || knowledge.getTag() < 1 || knowledge.getTag() > 12) {
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
    public ResponseDto<Knowledge> next(Long currentId, Integer tag, String type) {
        Long cursor = currentId == null ? 0L : currentId;
        Knowledge next = selectNextAfter(cursor, tag, type);
        // 已经刷到最后一题，则回到开头继续循环
        if (next == null) {
            next = selectNextAfter(0L, tag, type);
        }
        fillPosition(next, tag, type);
        return next == null ? ResponseDto.error("暂无知识点，请先添加") : ResponseDto.success(next);
    }

    @Override
    public ResponseDto<Knowledge> getById(Long id, String type) {
        Knowledge knowledge = requireByType(id, type);
        if (knowledge != null) {
            fillPosition(knowledge, null, type);
        }
        return knowledge == null ? ResponseDto.error("知识点不存在") : ResponseDto.success(knowledge);
    }

    @Override
    public ResponseDto<Knowledge> search(String keyword, Integer tag, String type) {
        if (!StringUtils.hasText(keyword)) {
            return ResponseDto.error("关键字不能为空");
        }
        LambdaQueryWrapper<Knowledge> wrapper = baseQueryWrapper(type)
                .like(Knowledge::getQuestion, keyword.trim())
                .orderByAsc(Knowledge::getId)
                .last("LIMIT 20");
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        appendTypeCondition(wrapper, type);
        return ResponseDto.success(baseMapper.selectList(wrapper));
    }

    @Override
    public ResponseDto<Knowledge> prev(Long currentId, Integer tag, String type) {
        Long cursor = currentId == null ? Long.MAX_VALUE : currentId;
        Knowledge prev = selectPrevBefore(cursor, tag, type);
        // 已经刷到第一题，则跳到最后一题
        if (prev == null) {
            prev = selectPrevBefore(Long.MAX_VALUE, tag, type);
        }
        fillPosition(prev, tag, type);
        return prev == null ? ResponseDto.error("暂无知识点，请先添加") : ResponseDto.success(prev);
    }

    @Override
    public ResponseDto<Knowledge> random(Integer tag, String type) {
        LambdaQueryWrapper<Knowledge> wrapper = baseQueryWrapper(type);
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        appendTypeCondition(wrapper, type);
        Knowledge knowledge = baseMapper.selectOne(wrapper.last("ORDER BY RAND() LIMIT 1"));
        fillPosition(knowledge, tag, type);
        return knowledge == null ? ResponseDto.error("暂无知识点，请先添加") : ResponseDto.success(knowledge);
    }

    @Override
    public ResponseDto<Knowledge> byIndex(Integer index, Integer tag, String type) {
        if (index == null || index < 1) {
            return ResponseDto.error("题号不合法");
        }
        LambdaQueryWrapper<Knowledge> wrapper = baseQueryWrapper(type)
                .orderByAsc(Knowledge::getId);
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        appendTypeCondition(wrapper, type);
        wrapper.last("LIMIT 1 OFFSET " + (index - 1));
        Knowledge knowledge = baseMapper.selectOne(wrapper);
        if (knowledge == null) {
            return ResponseDto.error("题号超出范围");
        }
        fillPosition(knowledge, tag, type);
        return ResponseDto.success(knowledge);
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
        recordAction(exist, KnowledgeAction.ACTION_VIEW, 0);
        return ResponseDto.success(baseMapper.selectById(id));
    }

    @Override
    public ResponseDto<Knowledge> toggleFavorite(Long id) {
        Knowledge exist = requireNotDeleted(id);
        if (exist == null) {
            return ResponseDto.error("知识点不存在");
        }
        boolean toFavorite = exist.getStatus() == null || exist.getStatus() != STATUS_FAVORITE;
        Knowledge update = new Knowledge();
        update.setId(id);
        update.setStatus(toFavorite ? STATUS_FAVORITE : STATUS_NORMAL);
        baseMapper.updateById(update);
        recordAction(exist, toFavorite ? KnowledgeAction.ACTION_FAVORITE : KnowledgeAction.ACTION_UNFAVORITE, 0);
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
        recordAction(exist, KnowledgeAction.ACTION_MASTER, 0);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<KnowledgeNote> addNote(Long knowledgeId, String content) {
        if (!StringUtils.hasText(content)) {
            return ResponseDto.error("笔记内容不能为空");
        }
        Knowledge exist = requireNotDeleted(knowledgeId);
        if (exist == null) {
            return ResponseDto.error("知识点不存在");
        }
        KnowledgeNote note = new KnowledgeNote();
        note.setKnowledgeId(knowledgeId);
        note.setContent(content.trim());
        note.setImportant(0);
        note.setCreateTime(new Date());
        knowledgeNoteMapper.insert(note);
        recordAction(exist, KnowledgeAction.ACTION_NOTE, 0);
        return ResponseDto.success(note);
    }

    @Override
    public ResponseDto<KnowledgeNote> updateNote(Long noteId, KnowledgeNote note) {
        KnowledgeNote exist = knowledgeNoteMapper.selectById(noteId);
        if (exist == null) {
            return ResponseDto.error("笔记不存在");
        }
        KnowledgeNote update = new KnowledgeNote();
        update.setId(noteId);
        if (note != null && note.getContent() != null) {
            if (!StringUtils.hasText(note.getContent())) {
                return ResponseDto.error("笔记内容不能为空");
            }
            update.setContent(note.getContent().trim());
        }
        if (note != null && note.getImportant() != null) {
            update.setImportant(note.getImportant() == 1 ? 1 : 0);
        }
        knowledgeNoteMapper.updateById(update);
        return ResponseDto.success(knowledgeNoteMapper.selectById(noteId));
    }

    @Override
    public ResponseDto<KnowledgeNote> deleteNote(Long noteId) {
        KnowledgeNote exist = knowledgeNoteMapper.selectById(noteId);
        if (exist == null) {
            return ResponseDto.error("笔记不存在");
        }
        knowledgeNoteMapper.deleteById(noteId);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<KnowledgeNote> listNotes(Long knowledgeId) {
        List<KnowledgeNote> notes = knowledgeNoteMapper.selectList(new LambdaQueryWrapper<KnowledgeNote>()
                .eq(KnowledgeNote::getKnowledgeId, knowledgeId)
                .orderByDesc(KnowledgeNote::getImportant)
                .orderByDesc(KnowledgeNote::getCreateTime));
        return ResponseDto.success(notes);
    }

    @Override
    public ResponseDto<KnowledgeTagStat> tagStats() {
        QueryWrapper<Knowledge> wrapper = new QueryWrapper<>();
        wrapper.select(
                        "tag",
                        "count(*) as total",
                        "sum(case when view_count > 0 then 1 else 0 end) as learned")
                .eq("del_flag", NOT_DELETED)
                .groupBy("tag");
        List<KnowledgeTagStat> stats = baseMapper.selectMaps(wrapper).stream().map(row -> {
            KnowledgeTagStat stat = new KnowledgeTagStat();
            stat.setTag(toInt(row.get("tag"), 0));
            stat.setTotal(toInt(row.get("total"), 0));
            stat.setLearned(toInt(row.get("learned"), 0));
            return stat;
        }).collect(Collectors.toList());
        return ResponseDto.success(stats);
    }

    /** selectMaps 返回的聚合列可能是 String/Number，统一转 int */
    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 插入一条行为记录（异步无关紧要的统计埋点，失败不影响主流程） */
    private void recordAction(Knowledge knowledge, String action, int duration) {
        if (knowledge == null || knowledge.getId() == null) return;
        try {
            KnowledgeAction record = new KnowledgeAction();
            record.setKnowledgeId(knowledge.getId());
            record.setAction(action);
            record.setTag(knowledge.getTag());
            record.setDuration(Math.max(0, duration));
            record.setCreateTime(new Date());
            knowledgeActionMapper.insert(record);
        } catch (Exception ignored) {
            // 统计埋点失败不影响业务
        }
    }

    @Override
    public ResponseDto<Void> recordViewDuration(Long id, Integer duration) {
        if (id == null || duration == null || duration <= 0) {
            return ResponseDto.success(null);
        }
        Knowledge exist = baseMapper.selectById(id);
        if (exist == null) {
            return ResponseDto.success(null);
        }
        // 限制单次最长 30 分钟，避免前端异常导致的脏数据
        int safe = Math.min(duration, 30 * 60);
        recordAction(exist, KnowledgeAction.ACTION_VIEW, safe);
        return ResponseDto.success(null);
    }

    @Override
    public ResponseDto<KnowledgeDailyStat> dailyStats() {
        // 近 7 天（含今天）
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, -6);
        Date startDate = cal.getTime();

        QueryWrapper<KnowledgeAction> wrapper = new QueryWrapper<>();
        wrapper.select(
                        "DATE(create_time) as day",
                        "sum(case when action = 'view' then 1 else 0 end) as viewCount",
                        "sum(case when action = 'favorite' then 1 else 0 end) as favoriteCount",
                        "sum(case when action = 'note' then 1 else 0 end) as noteCount",
                        "sum(case when action = 'master' then 1 else 0 end) as masterCount",
                        "sum(case when action = 'view' then duration else 0 end) as durationSec")
                .ge("create_time", startDate)
                .groupBy("DATE(create_time)")
                .orderByAsc("DATE(create_time)");

        List<Map<String, Object>> rows = knowledgeActionMapper.selectMaps(wrapper);
        List<KnowledgeDailyStat> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            KnowledgeDailyStat stat = new KnowledgeDailyStat();
            Object day = row.get("day");
            if (day instanceof java.sql.Date) {
                stat.setDate(new Date(((java.sql.Date) day).getTime()));
            } else if (day instanceof java.sql.Timestamp) {
                stat.setDate(new Date(((java.sql.Timestamp) day).getTime()));
            } else if (day != null) {
                try {
                    stat.setDate(new java.text.SimpleDateFormat("yyyy-MM-dd").parse(day.toString()));
                } catch (Exception ignored) {}
            }
            stat.setCount(toInt(row.get("viewCount"), 0));
            stat.setFavorite(toInt(row.get("favoriteCount"), 0));
            stat.setNote(toInt(row.get("noteCount"), 0));
            stat.setMastered(toInt(row.get("masterCount"), 0));
            // 耗时（秒），前端按秒展示
            stat.setDuration(toInt(row.get("durationSec"), 0));
            list.add(stat);
        }
        return ResponseDto.success(list);
    }

    @Override
    public ResponseDto<KnowledgeTagDetailStat> tagDetailStats() {
        Map<Integer, KnowledgeTagDetailStat> map = new HashMap<>();

        // 题目数 / 当前收藏数 / 已掌握数（按 t_knowledge 当前状态）
        QueryWrapper<Knowledge> kWrapper = new QueryWrapper<>();
        kWrapper.select(
                        "tag",
                        "sum(case when del_flag = 0 then 1 else 0 end) as total",
                        "sum(case when del_flag = 0 and status = 2 then 1 else 0 end) as favorite",
                        "sum(case when del_flag = 1 then 1 else 0 end) as mastered")
                .groupBy("tag");
        for (Map<String, Object> row : baseMapper.selectMaps(kWrapper)) {
            Integer tag = toInt(row.get("tag"), 0);
            KnowledgeTagDetailStat stat = map.computeIfAbsent(tag, t -> {
                KnowledgeTagDetailStat s = new KnowledgeTagDetailStat();
                s.setTag(t);
                s.setCount(0);
                s.setDuration(0);
                s.setFavorite(0);
                s.setNote(0);
                s.setMastered(0);
                return s;
            });
            stat.setCount(toInt(row.get("total"), 0));
            stat.setFavorite(toInt(row.get("favorite"), 0));
            stat.setMastered(toInt(row.get("mastered"), 0));
        }

        // 笔记数：当前存在的笔记条数（join t_knowledge 取 tag），实时统计能反映历史数据
        List<Map<String, Object>> noteRows = knowledgeNoteMapper.countByTag();
        for (Map<String, Object> row : noteRows) {
            Integer tag = toInt(row.get("tag"), 0);
            map.computeIfAbsent(tag, t -> {
                KnowledgeTagDetailStat s = new KnowledgeTagDetailStat();
                s.setTag(t);
                s.setCount(0);
                s.setDuration(0);
                s.setFavorite(0);
                s.setNote(0);
                s.setMastered(0);
                return s;
            }).setNote(toInt(row.get("noteCount"), 0));
        }

        // 耗时：从 t_knowledge_action 中 view 的 duration 按 tag 汇总
        QueryWrapper<KnowledgeAction> aWrapper = new QueryWrapper<>();
        aWrapper.select("tag", "sum(duration) as durationSec")
                .eq("action", KnowledgeAction.ACTION_VIEW)
                .groupBy("tag");
        for (Map<String, Object> row : knowledgeActionMapper.selectMaps(aWrapper)) {
            Integer tag = toInt(row.get("tag"), 0);
            map.computeIfAbsent(tag, t -> {
                KnowledgeTagDetailStat s = new KnowledgeTagDetailStat();
                s.setTag(t);
                s.setCount(0);
                s.setDuration(0);
                s.setFavorite(0);
                s.setNote(0);
                s.setMastered(0);
                return s;
            }).setDuration(toInt(row.get("durationSec"), 0));
        }

        List<KnowledgeTagDetailStat> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Integer.compare(a.getTag() == null ? 0 : a.getTag(), b.getTag() == null ? 0 : b.getTag()));
        return ResponseDto.success(list);
    }

    @Override
    public ResponseDto<KnowledgeQuestionTopStat> questionTopStats() {
        KnowledgeQuestionTopStat stat = new KnowledgeQuestionTopStat();
        stat.setNoteTop(topByNote());
        stat.setDurationTop(topByDuration());
        stat.setViewTop(topByView());
        return ResponseDto.success(stat);
    }

    /** 笔记数 TOP10（来自 t_knowledge_note 关联题干） */
    private List<KnowledgeQuestionTopItem> topByNote() {
        List<Map<String, Object>> rows = knowledgeNoteMapper.topByKnowledge();
        return buildTopItems(rows, "kid", "cnt");
    }

    /** 耗时 TOP10（秒） */
    private List<KnowledgeQuestionTopItem> topByDuration() {
        List<Map<String, Object>> rows = knowledgeActionMapper.topDurationByKnowledge();
        return buildTopItems(rows, "kid", "sec");
    }

    /** 查看次数 TOP10（直接用 t_knowledge.view_count） */
    private List<KnowledgeQuestionTopItem> topByView() {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Knowledge::getId, Knowledge::getQuestion, Knowledge::getViewCount)
                .gt(Knowledge::getViewCount, 0)
                .orderByDesc(Knowledge::getViewCount)
                .last("LIMIT 10");
        List<Knowledge> list = baseMapper.selectList(wrapper);
        List<KnowledgeQuestionTopItem> items = new ArrayList<>();
        for (Knowledge k : list) {
            KnowledgeQuestionTopItem item = new KnowledgeQuestionTopItem();
            item.setId(k.getId());
            item.setQuestion(k.getQuestion());
            item.setValue(k.getViewCount());
            items.add(item);
        }
        return items;
    }

    /** 把 group 聚合结果（kid/cnt）拼成带题干与数值的列表 */
    private List<KnowledgeQuestionTopItem> buildTopItems(List<Map<String, Object>> rows, String idKey, String valueKey) {
        List<KnowledgeQuestionTopItem> items = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return items;
        List<Long> ids = rows.stream()
                .map(r -> toLong(getIgnoreCase(r, idKey)))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, String> questionMap = new HashMap<>();
        if (!ids.isEmpty()) {
            LambdaQueryWrapper<Knowledge> qw = new LambdaQueryWrapper<>();
            qw.select(Knowledge::getId, Knowledge::getQuestion).in(Knowledge::getId, ids);
            for (Knowledge k : baseMapper.selectList(qw)) {
                questionMap.put(k.getId(), k.getQuestion());
            }
        }
        for (Map<String, Object> row : rows) {
            Long kid = toLong(getIgnoreCase(row, idKey));
            if (kid == null) continue;
            KnowledgeQuestionTopItem item = new KnowledgeQuestionTopItem();
            item.setId(kid);
            item.setQuestion(questionMap.getOrDefault(kid, ""));
            item.setValue(toInt(getIgnoreCase(row, valueKey), 0));
            items.add(item);
        }
        return items;
    }

    /** Map 取值不区分大小写（应对 MyBatis selectMaps 在不同 JDBC 下大小写不一致的问题） */
    private Object getIgnoreCase(Map<String, Object> row, String key) {
        if (row == null || key == null) return null;
        if (row.containsKey(key)) return row.get(key);
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 查询 id 大于 cursor、未删除（tag 非空时同标签）的第一条知识点 */
    private Knowledge selectNextAfter(Long cursor, Integer tag, String type) {
        LambdaQueryWrapper<Knowledge> wrapper = baseQueryWrapper(type)
                .gt(Knowledge::getId, cursor);
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        appendTypeCondition(wrapper, type);
        return baseMapper.selectOne(wrapper.orderByAsc(Knowledge::getId).last("LIMIT 1"));
    }

    /** 查询 id 小于 cursor、未删除（tag 非空时同标签）的第一条知识点（按 id 倒序） */
    private Knowledge selectPrevBefore(Long cursor, Integer tag, String type) {
        LambdaQueryWrapper<Knowledge> wrapper = baseQueryWrapper(type)
                .lt(Knowledge::getId, cursor);
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        appendTypeCondition(wrapper, type);
        return baseMapper.selectOne(wrapper.orderByDesc(Knowledge::getId).last("LIMIT 1"));
    }

    private LambdaQueryWrapper<Knowledge> baseQueryWrapper(String type) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (!ALL_TYPE.equals(type)) {
            wrapper.eq(Knowledge::getDelFlag, IGNORED_TYPE.equals(type) ? DELETED : NOT_DELETED);
        }
        return wrapper;
    }

    private void appendTypeCondition(LambdaQueryWrapper<Knowledge> wrapper, String type) {
        if (FAVORITE_TYPE.equals(type)) {
            wrapper.eq(Knowledge::getStatus, COLLECT_STATUS);
        }
        if (NOTE_TYPE.equals(type)) {
            List<KnowledgeNote> knowledgeNoteList = knowledgeNoteMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeNote>().select(KnowledgeNote::getKnowledgeId));
            if (CollectionUtils.isNotEmpty(knowledgeNoteList)) {
                List<Long> idList = knowledgeNoteList.stream().map(KnowledgeNote::getKnowledgeId)
                        .distinct().collect(Collectors.toList());
                wrapper.in(Knowledge::getId, idList);
            }
        }
    }

    /** 计算题目在当前筛选范围内的序号与总数，写入瞬态字段 index/total */
    private void fillPosition(Knowledge knowledge, Integer tag, String type) {
        if (knowledge == null || knowledge.getId() == null) {
            return;
        }
        LambdaQueryWrapper<Knowledge> wrapper = baseQueryWrapper(type);
        if (tag != null) {
            wrapper.eq(Knowledge::getTag, tag);
        }
        appendTypeCondition(wrapper, type);
        Long total = baseMapper.selectCount(wrapper);
        LambdaQueryWrapper<Knowledge> indexWrapper = baseQueryWrapper(type)
                .le(Knowledge::getId, knowledge.getId());
        if (tag != null) {
            indexWrapper.eq(Knowledge::getTag, tag);
        }
        appendTypeCondition(indexWrapper, type);
        Long index = baseMapper.selectCount(indexWrapper);
        knowledge.setIndex(total == 0 ? 0 : index.intValue());
        knowledge.setTotal(total.intValue());
    }

    private Knowledge requireByType(Long id, String type) {
        if (id == null) {
            return null;
        }
        Knowledge knowledge = baseMapper.selectById(id);
        if (knowledge == null) {
            return null;
        }
        Integer delFlag = knowledge.getDelFlag();
        boolean ignored = delFlag != null && delFlag == DELETED;
        if (IGNORED_TYPE.equals(type)) {
            return ignored ? knowledge : null;
        }
        if (ALL_TYPE.equals(type)) {
            return knowledge;
        }
        return ignored ? null : knowledge;
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

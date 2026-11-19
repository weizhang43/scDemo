package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.KnowledgeNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeNoteMapper extends BaseMapper<KnowledgeNote> {

    /** 按标签统计当前存在的笔记条数（关联 t_knowledge 取 tag） */
    @Select("select k.tag as tag, count(*) as noteCount " +
            "from t_knowledge_note n " +
            "join t_knowledge k on k.id = n.knowledge_id " +
            "group by k.tag")
    List<Map<String, Object>> countByTag();

    /** 笔记数 TOP10：按 knowledge_id 分组取前 10 */
    @Select("select knowledge_id as kid, count(*) as cnt " +
            "from t_knowledge_note " +
            "group by knowledge_id " +
            "order by cnt desc " +
            "limit 10")
    List<Map<String, Object>> topByKnowledge();
}

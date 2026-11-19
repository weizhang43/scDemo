package com.example.scuser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.curry.model.KnowledgeAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeActionMapper extends BaseMapper<KnowledgeAction> {

    /** 耗时 TOP10：按 knowledge_id 汇总 view 行为的 duration */
    @Select("select knowledge_id as kid, sum(duration) as sec " +
            "from t_knowledge_action " +
            "where action = 'view' " +
            "group by knowledge_id " +
            "having sum(duration) > 0 " +
            "order by sec desc " +
            "limit 10")
    List<Map<String, Object>> topDurationByKnowledge();
}

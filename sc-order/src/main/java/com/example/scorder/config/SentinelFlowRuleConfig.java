package com.example.scorder.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流规则：代码方式初始化，服务启动即生效（无需依赖控制台推送）
 */
@Configuration
public class SentinelFlowRuleConfig {

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule pageQueryRule = new FlowRule();
        pageQueryRule.setResource("order-queryOrder");
        pageQueryRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        pageQueryRule.setCount(2);
        rules.add(pageQueryRule);

        FlowRuleManager.loadRules(rules);
    }
}

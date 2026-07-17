package com.example.scproduct.config;

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

    /**
     * 容器初始化后加载 Sentinel 限流规则：对 product-pageQuery 资源设置 QPS=2 的限流。
     */
    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule pageQueryRule = new FlowRule();
        pageQueryRule.setResource("product-pageQuery");
        pageQueryRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        pageQueryRule.setCount(2);
        rules.add(pageQueryRule);

        FlowRuleManager.loadRules(rules);
    }
}

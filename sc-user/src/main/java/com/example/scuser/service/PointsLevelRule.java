package com.example.scuser.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会员等级规则：按累计获取积分划分，不随消耗下降。
 */
@Component
public class PointsLevelRule {

    /** 等级阈值：累计获取积分下限、下一级下限（-1 表示封顶） */
    private static final int[][] LEVELS = {
            {0, 100},      // 普通
            {100, 500},    // 白银
            {500, 2000},   // 黄金
            {2000, -1}     // 铂金：封顶
    };

    private static final String[] LEVEL_NAMES = {"普通会员", "白银会员", "黄金会员", "铂金会员"};

    /** 积分兑换选项：key、所需积分、使用门槛(元)、减免额(元)、名称 */
    public static final String[][] REDEEM_OPTIONS = {
            {"A", "200", "30", "5", "满30减5元券"},
            {"B", "500", "100", "15", "满100减15元券"},
            {"C", "1000", "200", "35", "满200减35元券"}
    };

    /**
     * 按累计获取积分解析等级：返回 {levelCode, levelName, currentThreshold, nextThreshold}。
     */
    public Map<String, Object> resolve(int totalEarned) {
        int index = 0;
        for (int i = 0; i < LEVELS.length; i++) {
            if (totalEarned >= LEVELS[i][0]) {
                index = i;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("levelCode", index);
        result.put("levelName", LEVEL_NAMES[index]);
        result.put("currentThreshold", LEVELS[index][0]);
        result.put("nextThreshold", LEVELS[index][1]);
        return result;
    }

    /**
     * 查找兑换选项，非法 key 返回 null。
     */
    public String[] findOption(String optionKey) {
        for (String[] option : REDEEM_OPTIONS) {
            if (option[0].equals(optionKey)) {
                return option;
            }
        }
        return null;
    }

    /**
     * 兑换流水号：RD + 时间戳 + 随机数，保证每次兑换唯一。
     */
    public static String newRedeemNo() {
        return "RD" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}

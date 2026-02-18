package com.mark.knowledge.rag.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 领域枚举
 *
 * @author mark
 */
public enum Domain {
    
    /**
     * 医疗健康
     */
    MEDICAL("医疗", "medical"),
    
    /**
     * 法律法规
     */
    LAW("法律", "law"),
    
    /**
     * 金融服务
     */
    FINANCE("金融", "finance"),
    
    /**
     * 教育培训
     */
    EDUCATION("教育", "education"),
    
    /**
     * 科技技术
     */
    TECHNOLOGY("科技", "technology"),
    
    /**
     * 人文历史
     */
    HISTORY("历史", "history"),
    
    /**
     * 自然科学
     */
    SCIENCE("科学", "science"),

    /**
     * 代码
     */
    CODE("代码", "code"),

    /**
     * 小说
     */
    STORY("小说", "story"),

    /**
     * 论文
     */
    PAPER("论文", "paper"),
    /**
     * 其他领域
     */
    OTHER("其他", "other");

    private final String displayName;
    private final String code;

    Domain(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据显示名称获取枚举
     */
    public static Domain fromDisplayName(String displayName) {
        for (Domain domain : values()) {
            if (domain.displayName.equals(displayName)) {
                return domain;
            }
        }
        return OTHER;
    }

    /**
     * 根据代码获取枚举
     */
    public static Domain fromCode(String code) {
        for (Domain domain : values()) {
            if (domain.code.equals(code)) {
                return domain;
            }
        }
        return OTHER;
    }

    /**
     * JSON 反序列化：根据枚举名称（如 "TECHNOLOGY"）创建枚举
     */
    @JsonCreator
    public static Domain fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OTHER;
        }
        try {
            return Domain.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试通过显示名称匹配
            return fromDisplayName(value);
        }
    }

    /**
     * JSON 序列化：返回枚举名称
     */
    @JsonValue
    public String toValue() {
        return this.name();
    }

    @Override
    public String toString() {
        return displayName;
    }
}

package com.mark.knowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson配置类 - 提供ObjectMapper Bean
 *
 * 配置Jackson序列化和反序列化行为
 * - 注册Java 8和Java Time模块
 * - 配置日期格式化
 * - 配置未知属性处理
 */
@Configuration
public class JacksonConfig {

    /**
     * 创建主ObjectMapper Bean
     *
     * @return 配置好的ObjectMapper实例
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册模块
        mapper.registerModule(new Jdk8Module());           // Java 8特性支持（如Optional）
        mapper.registerModule(new JavaTimeModule());       // Java 8日期时间API支持
        mapper.registerModule(new ParameterNamesModule());  // 构造函数参数名支持

        // 配置序列化行为
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);  // 不将日期写为时间戳
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);       // 允许序列化空Bean

        // 配置反序列化行为
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);  // 忽略未知属性
        mapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);  // 允许忽略属性

        return mapper;
    }
}

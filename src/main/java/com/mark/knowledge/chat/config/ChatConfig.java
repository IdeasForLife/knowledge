package com.mark.knowledge.chat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * 聊天配置类 - SQLite + LangChain4j + Qdrant
 *
 * 配置组件：
 * - Ollama聊天模型
 * - Ollama嵌入模型
 * - Qdrant向量存储
 * - SQLite数据源
 * - JPA实体管理器
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.mark.knowledge.chat.repository")
public class ChatConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatConfig.class);

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.chat-model:qwen2.5:7b}")
    private String chatModelName;

    @Value("${ollama.embedding-model:qwen3-embedding:0.6b}")
    private String embeddingModelName;

    @Value("${ollama.timeout:120s}")
    private String ollamaTimeout;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${qdrant.collection-name:knowledge-base}")
    private String collectionName;

    @Value("${qdrant.vector-size:1024}")
    private int vectorSize;

    /**
     * 创建Ollama聊天模型Bean
     * 用于生成回复
     */
    @Bean
    public ChatModel chatModel() {
        log.info("==========================================");
        log.info("初始化Ollama聊天模型");
        log.info("  模型: {}", chatModelName);
        log.info("  URL: {}", ollamaBaseUrl);
        log.info("==========================================");

        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(0.7)
                .timeout(parseTimeout(ollamaTimeout))
                .build();
    }

    /**
     * 创建Ollama嵌入模型Bean
     * 用于生成文本向量
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化Ollama嵌入模型: {} @ {}", embeddingModelName, ollamaBaseUrl);

        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .timeout(parseTimeout(ollamaTimeout))
                .build();
    }

    /**
     * 创建Qdrant向量存储Bean
     * 注意：集合会在首次使用时自动创建（如果不存在）
     * 确保向量维度与嵌入模型的输出维度匹配
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("==========================================");
        log.info("初始化Qdrant向量存储");
        log.info("  主机: {}", qdrantHost);
        log.info("  端口: {}", qdrantPort);
        log.info("  集合: {}", collectionName);
        log.info("  向量维度: {}", vectorSize);
        log.info("==========================================");
        log.info("💡 提示: 如果集合 '{}' 不存在，将自动创建", collectionName);
        log.info("💡 确保向量维度与嵌入模型的输出维度匹配");

        return QdrantEmbeddingStore.builder()
                .host(qdrantHost)
                .port(qdrantPort)
                .collectionName(collectionName)
                .build();
    }

    /**
     * 创建SQLite数据源Bean
     */
    @Bean
    public DataSource dataSource() {
        try {
            log.info("初始化SQLite数据源: knowledge.db");
            SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
            dataSource.setDriverClass(org.sqlite.JDBC.class);
            dataSource.setUrl("jdbc:sqlite:knowledge.db");
            return dataSource;
        } catch (Exception e) {
            log.error("创建数据源失败", e);
            throw new RuntimeException("数据源创建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建JPA实体管理器工厂Bean
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        log.info("初始化JPA实体管理器工厂");

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("com.mark.knowledge.chat.entity", "com.mark.knowledge.rag.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(false);
        emf.setJpaVendorAdapter(vendorAdapter);

        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        jpaProperties.put("hibernate.hbm2ddl.auto", "update");
        jpaProperties.put("hibernate.format_sql", "false");
        emf.setJpaProperties(jpaProperties);

        return emf;
    }

    /**
     * 创建事务管理器Bean
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager();
    }

    /**
     * 解析超时时间字符串为Duration对象
     */
    private java.time.Duration parseTimeout(String timeout) {
        try {
            if (timeout.endsWith("s")) {
                long seconds = Long.parseLong(timeout.substring(0, timeout.length() - 1));
                return java.time.Duration.ofSeconds(seconds);
            }
            return java.time.Duration.ofSeconds(120);
        } catch (Exception e) {
            return java.time.Duration.ofSeconds(120);
        }
    }
}

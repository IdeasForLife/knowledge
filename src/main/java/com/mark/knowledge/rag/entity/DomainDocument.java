package com.mark.knowledge.rag.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 领域文档实体
 *
 * @author mark
 */
@Entity
@Table(name = "domain_documents")
public class DomainDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 领域名称
     */
    @Column(name = "domain", nullable = false)
    private String domain;

    /**
     * 标题
     */
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * 来源
     */
    @Column(name = "source")
    private String source;

    /**
     * 文档内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 处理状态：pending-处理中，success-成功，failed-失败
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * 错误信息
     */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * 向量数量
     */
    @Column(name = "vector_count")
    private Integer vectorCount;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 默认构造函数
    public DomainDocument() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = "pending";
    }

    // 全参构造函数
    public DomainDocument(String domain, String title, String source, String content) {
        this();
        this.domain = domain;
        this.title = title;
        this.source = source;
        this.content = content;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getVectorCount() {
        return vectorCount;
    }

    public void setVectorCount(Integer vectorCount) {
        this.vectorCount = vectorCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}

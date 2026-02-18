package com.mark.knowledge.rag.repository;

import com.mark.knowledge.rag.entity.DomainDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 领域文档 Repository
 *
 * @author mark
 */
@Repository
public interface DomainDocumentRepository extends JpaRepository<DomainDocument, Long> {

    /**
     * 根据领域查询文档
     */
    Page<DomainDocument> findByDomain(String domain, Pageable pageable);

    /**
     * 根据状态查询文档
     */
    Page<DomainDocument> findByStatus(String status, Pageable pageable);

    /**
     * 根据领域和状态查询文档
     */
    Page<DomainDocument> findByDomainAndStatus(String domain, String status, Pageable pageable);

    /**
     * 获取所有不重复的领域
     */
    @Query("SELECT DISTINCT d.domain FROM DomainDocument d ORDER BY d.domain")
    List<String> findAllDistinctDomains();
}

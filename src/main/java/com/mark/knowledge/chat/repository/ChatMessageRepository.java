package com.mark.knowledge.chat.repository;

import com.mark.knowledge.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天消息Repository
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 根据会话ID查询消息列表，按时间排序
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    /**
     * 查询所有会话ID
     */
    @Query("SELECT DISTINCT m.conversationId FROM ChatMessage m ORDER BY m.createdAt DESC")
    List<String> findAllConversationIds();

    /**
     * 删除指定会话的所有消息
     */
    void deleteByConversationId(String conversationId);

    /**
     * 统计指定会话的消息数量
     */
    long countByConversationId(String conversationId);

    /**
     * 查询指定会话的最后N条消息
     */
    @Query(value = "SELECT * FROM chat_messages WHERE conversation_id = :conversationId " +
                   "ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<ChatMessage> findLastNMessagesByConversationId(
        @Param("conversationId") String conversationId,
        @Param("limit") int limit
    );
}

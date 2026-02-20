package com.mark.knowledge.chat.config;

import com.mark.knowledge.chat.entity.ChatMessage;
import com.mark.knowledge.chat.repository.ChatMessageRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据初始化器
 *
 * 功能：
 * 将所有历史数据关联到 mark 用户（userId=1）
 *
 * @author mark
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final Long MARK_USER_ID = 1L;

    private final ChatMessageRepository chatMessageRepository;

    public DataInitializer(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostConstruct
    public void initialize() {
        // 将所有历史数据关联到 mark (userId=1)
        List<ChatMessage> orphanMessages = chatMessageRepository.findAll().stream()
                .filter(msg -> msg.getUserId() == null)
                .collect(Collectors.toList());

        if (!orphanMessages.isEmpty()) {
            orphanMessages.forEach(msg -> msg.setUserId(MARK_USER_ID));
            chatMessageRepository.saveAll(orphanMessages);
            log.info("✅ 将 {} 条历史消息关联到用户 mark (userId=1)", orphanMessages.size());
        } else {
            log.info("ℹ️  所有消息已关联到用户");
        }

        long totalMessages = chatMessageRepository.count();
        log.info("ℹ️  当前系统中共有 {} 条聊天消息", totalMessages);
    }
}

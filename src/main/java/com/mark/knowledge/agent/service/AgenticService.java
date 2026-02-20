package com.mark.knowledge.agent.service;

import com.mark.knowledge.agent.dto.AgentExecutionInfo;
import com.mark.knowledge.agent.tool.VectorSearchTool;
import com.mark.knowledge.chat.entity.ChatMessage;
import com.mark.knowledge.chat.repository.ChatMessageRepository;
import com.mark.knowledge.chat.service.ModelRouterService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 统一的 Agentic Service
 *
 * 使用 LangChain4j 的 AiServices 创建智能 Agent
 * Agent 可以自主决定是否调用工具
 *
 * 特点：
 * - 使用 ChatMemory 管理对话状态
 * - Agent 自主决策是否调用工具
 * - 支持流式输出
 * - 记录执行历史
 *
 * @author mark
 */
@Service
public class AgenticService {

    private static final Logger log = LoggerFactory.getLogger(AgenticService.class);
    private static final String CONVERSATION_ID_PREFIX = "agent-";

    private final ChatModel defaultChatModel;
    private final ModelRouterService modelRouterService;
    private final VectorSearchTool vectorSearchTool;
    private final ToolAgent toolAgent;
    private final FinancialToolAgent financialToolAgent;
    private final McpFileService mcpFileService;
    private final ChatMessageRepository chatMessageRepository;
    private final int contextWindowSize;

    // 执行历史记录
    private final List<AgentExecutionInfo> executionHistory = new CopyOnWriteArrayList<>();

    public AgenticService(
            @Qualifier("chatModel") ChatModel defaultChatModel,
            ModelRouterService modelRouterService,
            VectorSearchTool vectorSearchTool,
            ToolAgent toolAgent,
            FinancialToolAgent financialToolAgent,
            McpFileService mcpFileService,
            ChatMessageRepository chatMessageRepository,
            @Value("${agent.context-window-size:10}") int contextWindowSize) {
        this.defaultChatModel = defaultChatModel;
        this.modelRouterService = modelRouterService;
        this.vectorSearchTool = vectorSearchTool;
        this.toolAgent = toolAgent;
        this.financialToolAgent = financialToolAgent;
        this.mcpFileService = mcpFileService;
        this.chatMessageRepository = chatMessageRepository;
        this.contextWindowSize = contextWindowSize;

        log.info("==========================================");
        log.info("🤖 AgenticService 初始化完成");
        log.info("  模型: {}", defaultChatModel.getClass().getSimpleName());
        log.info("  工具: 向量检索, 计算, 金融计算, 文件操作");
        log.info("  记忆: MessageWindowChatMemory (窗口大小: {})", contextWindowSize);
        log.info("==========================================");
    }

    /**
     * 异步聊天 - 使用 Agent 自主决策
     *
     * @param message 用户消息
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param onChunk 流式输出回调
     * @param onComplete 完成回调
     * @param onError 错误回调
     */
    public void chatAsync(
            String message,
            String conversationId,
            Long userId,
            Consumer<String> onChunk,
            Consumer<String> onComplete,
            Consumer<Exception> onError) {

        // 清空历史
        executionHistory.clear();

        CompletableFuture.runAsync(() -> {
            try {
                processChatWithAgent(message, conversationId, userId, onChunk, onComplete);
            } catch (Exception e) {
                log.error("Agent处理失败", e);
                onError.accept(e);
            }
        });
    }

    /**
     * 使用 Agent 处理聊天
     */
    @Transactional
    private void processChatWithAgent(
            String message,
            String conversationId,
            Long userId,
            Consumer<String> onChunk,
            Consumer<String> onComplete) {

        long startTime = System.currentTimeMillis();

        // 如果是新建对话，添加agent-前缀
        String finalConversationId = conversationId != null ? conversationId :
            CONVERSATION_ID_PREFIX + UUID.randomUUID().toString();

        log.info("==========================================");
        log.info("🤖 Agent 开始处理");
        log.info("  消息: {}", message);
        log.info("  会话: {}", finalConversationId);
        log.info("  用户: {}", userId);
        log.info("==========================================");

        addExecutionEvent(1, "AgentRouter", "STARTED", null, "分析请求类型...");

        // 步骤 1: 模型路由选择
        ModelRouterService.BusinessType businessType = modelRouterService.detectBusinessType(message);
        ChatModel selectedModel = (ChatModel) modelRouterService.routeModel(businessType);

        String modelClassName = selectedModel.getClass().getName();
        boolean isAliyunModel = modelClassName.contains("OpenAi");

        log.info("==========================================");
        log.info("🎯 模型路由结果");
        log.info("  模型类型: {}", isAliyunModel ? "☁️  阿里云DashScope (云端API)" : "💻 本地Ollama");
        log.info("  业务类型: {}", businessType);
        log.info("  模型类名: {}", modelClassName);
        log.info("==========================================");

        addExecutionEvent(2, "ModelRouter", "COMPLETED", null,
            "选择模型: " + (isAliyunModel ? "阿里云" : "本地"));

        // 步骤 3: 创建 ChatMemory（从数据库加载历史消息）
        addExecutionEvent(3, "AgentBuilder", "STARTED", null, "构建Agent...");

        // 创建内存 ChatMemory（用于本次对话）
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(contextWindowSize * 2)  // 用户消息+助手消息，所以乘2
                .build();

        // 从数据库加载历史消息到内存 ChatMemory
        List<ChatMessage> historyMessages = chatMessageRepository
                .findLastNMessagesByConversationId(finalConversationId, contextWindowSize);

        // 按时间顺序加载（从旧到新）
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = historyMessages.get(i);
            if ("user".equalsIgnoreCase(msg.getRole())) {
                chatMemory.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                chatMemory.add(new AiMessage(msg.getContent()));
            }
        }

        int historySize = historyMessages.size();
        log.info("📝 ChatMemory 已加载，历史消息数: {} 条", historySize);

        var agent = AiServices.builder(UnifiedAgent.class)
                .chatModel(selectedModel)
                .chatMemory(chatMemory)
                .tools(
                        vectorSearchTool,
                        toolAgent,
                        financialToolAgent,
                        mcpFileService
                )
                .build();

        addExecutionEvent(3, "AgentBuilder", "COMPLETED", null, "Agent构建完成");

        // 步骤 4: 执行 Agent 调用
        addExecutionEvent(4, "AgentExecution", "STARTED", null, "Agent思考中...");

        long agentStartTime = System.currentTimeMillis();

        // 设置向量检索工具的回调
        vectorSearchTool.setRecordCallback(record -> {
            addExecutionEvent(5, "Tool:" + record.toolName(),
                record.getStatus(),
                record.duration(),
                "输入: " + record.input() +
                ", 结果: " + (record.result() != null ? record.result() : "失败"));
        });

        // 调用 Agent - Agent会自主决定是否调用工具
        log.info("🤖 调用 agent.chat()，消息: {}", message);
        String response = agent.chat(message);
        log.info("🤖 agent.chat() 返回，响应: {}", response != null ? response.substring(0, Math.min(100, response.length())) : "null");

        long agentEndTime = System.currentTimeMillis();
        long agentDuration = agentEndTime - agentStartTime;

        log.info("==========================================");
        log.info("✅ Agent 响应完成");
        if (response != null) {
            log.info("  输出长度: {} 字符", response.length());
            log.info("  输出前200字符: {}", response.substring(0, Math.min(200, response.length())));
        } else {
            log.warn("  ⚠️ Agent 返回空响应");
        }
        log.info("  总耗时: {} ms", agentEndTime - startTime);
        log.info("==========================================");

        addExecutionEvent(4, "AgentExecution", "COMPLETED", agentDuration, "处理完成");

        // 处理空响应
        if (response == null || response.trim().isEmpty()) {
            log.warn("Agent 返回空响应，向用户返回提示信息");
            response = "抱歉，我暂时无法回答这个问题。可能是因为：\n" +
                      "1. 问题表述不够清晰\n" +
                      "2. 知识库中没有相关内容\n" +
                      "3. 需要更具体的上下文信息\n\n" +
                      "请尝试重新表述您的问题，或者提供更多背景信息。";
        }

        // 步骤 5: 保存消息到数据库（用户消息 + 助手消息）
        ChatMessage userMessage = new ChatMessage(userId, finalConversationId, "user", message, null);
        chatMessageRepository.save(userMessage);
        log.info("✅ 已保存用户消息到数据库");

        // 保存助手消息到数据库
        ChatMessage assistantMessage = new ChatMessage(
                userId,
                finalConversationId,
                "assistant",
                response,
                null
        );
        chatMessageRepository.save(assistantMessage);
        log.info("✅ 已保存助手消息到数据库");

        // 流式输出
        simulateStreamOutput(response, onChunk);

        // 完成，返回 conversationId
        onComplete.accept(finalConversationId);
    }

    /**
     * 模拟流式输出
     */
    private void simulateStreamOutput(String text, Consumer<String> onChunk) {
        // 按句子分割
        String[] sentences = text.split("(?<=[.!?。！？\\n])");

        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                onChunk.accept(sentence);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 添加执行事件
     */
    private void addExecutionEvent(int step, String agentName, String status,
                                   Long duration, String output) {
        AgentExecutionInfo event = new AgentExecutionInfo(
                step,
                agentName,
                status,
                duration,
                output
        );
        executionHistory.add(event);
    }

    /**
     * 获取执行历史
     */
    public List<AgentExecutionInfo> getExecutionHistory() {
        return new ArrayList<>(executionHistory);
    }

    /**
     * 清空执行历史
     */
    public void clearExecutionHistory() {
        executionHistory.clear();
    }

    /**
     * 获取聊天历史
     */
    public List<ChatMessage> getChatHistory(String conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * 获取用户的所有会话ID
     */
    public List<String> getAllConversationIds(Long userId) {
        return chatMessageRepository.findConversationIdsByUserIdAndPrefix(userId, "agent-");
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteConversation(String conversationId) {
        chatMessageRepository.deleteByConversationId(conversationId);
        log.info("已删除会话: {}", conversationId);
    }

    /**
     * 测试计算工具
     */
    public String testCalculation(String input) {
        return toolAgent.calculate(input);
    }

    /**
     * 测试天气工具
     */
    public String testWeather(String input) {
        return toolAgent.getWeather(input);
    }

    /**
     * 测试时间工具
     */
    public String testTime(String input) {
        return toolAgent.getCurrentTime();
    }
}

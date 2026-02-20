package com.mark.knowledge.chat.dto;

/**
 * 认证响应
 *
 * @param success 是否成功
 * @param username 用户名（成功时）
 * @param message 消息
 */
public record AuthResponse(boolean success, String username, String message) {}

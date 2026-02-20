package com.mark.knowledge.chat.dto;

/**
 * 登录请求
 *
 * @param username 用户名
 * @param password 密码
 */
public record LoginRequest(String username, String password) {}

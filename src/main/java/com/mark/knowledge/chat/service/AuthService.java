package com.mark.knowledge.chat.service;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 认证服务 - 简化版，使用内存存储
 *
 * 所有用户都使用mark账户（userId=1）
 * 重启后Session失效，需要重新登录
 *
 * @author mark
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String SESSION_USER_KEY = "currentUser";
    private static final String SESSION_USERNAME_KEY = "username";

    // 硬编码的mark用户信息
    private static final Long MARK_USER_ID = 1L;
    private static final String MARK_USERNAME = "mark";
    private static final String MARK_PASSWORD = "mark";

    public AuthService() {
        // 无需依赖注入
    }

    /**
     * 登录验证
     * @return 验证成功返回用户ID，失败返回null
     */
    public Long login(String username, String password) {
        log.info("尝试登录用户: {}", username);

        if (MARK_USERNAME.equals(username) && MARK_PASSWORD.equals(password)) {
            log.info("登录成功: username={}", username);
            return MARK_USER_ID;
        }

        log.warn("登录失败: 用户名或密码错误");
        return null;
    }

    /**
     * 获取当前登录用户ID（从 Session）
     */
    public Long getCurrentUserId(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_KEY);
        if (userId == null) {
            return null;
        }
        return (Long) userId;
    }

    /**
     * 获取当前登录用户名（从 Session）
     */
    public String getCurrentUsername(HttpSession session) {
        Object username = session.getAttribute(SESSION_USERNAME_KEY);
        if (username == null) {
            return null;
        }
        return (String) username;
    }

    /**
     * 设置当前登录用户到 Session
     */
    public void setCurrentUser(HttpSession session, Long userId, String username) {
        session.setAttribute(SESSION_USER_KEY, userId);
        session.setAttribute(SESSION_USERNAME_KEY, username);
        log.info("用户 {} (ID={}) 已登录，Session ID: {}, isNew={}",
                username, userId, session.getId(), session.isNew());
    }

    /**
     * 清除当前登录用户（退出登录）
     */
    public void clearCurrentUser(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_KEY);
        Object username = session.getAttribute(SESSION_USERNAME_KEY);
        if (userId != null) {
            log.info("用户 {} (ID={}) 已退出登录", username, userId);
            session.removeAttribute(SESSION_USER_KEY);
            session.removeAttribute(SESSION_USERNAME_KEY);
        }
    }

    /**
     * 检查是否已登录
     */
    public boolean isAuthenticated(HttpSession session) {
        return session.getAttribute(SESSION_USER_KEY) != null;
    }
}

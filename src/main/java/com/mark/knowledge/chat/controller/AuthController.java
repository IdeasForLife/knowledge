package com.mark.knowledge.chat.controller;

import com.mark.knowledge.chat.dto.AuthResponse;
import com.mark.knowledge.chat.dto.LoginRequest;
import com.mark.knowledge.chat.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 *
 * 处理登录、退出、检查认证状态等操作
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request,
            HttpSession session) {

        log.info("登录请求: username={}, sessionId={}", request.username(), session.getId());

        if (request.username() == null || request.username().isBlank() ||
            request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(false, null, "用户名和密码不能为空"));
        }

        Long userId = authService.login(request.username(), request.password());

        if (userId != null) {
            authService.setCurrentUser(session, userId, request.username());
            log.info("登录成功: username={}, userId={}, sessionId={}",
                    request.username(), userId, session.getId());
            return ResponseEntity.ok(new AuthResponse(true, request.username(), "登录成功"));
        } else {
            log.warn("登录失败: username={}", request.username());
            return ResponseEntity.ok(new AuthResponse(false, null, "用户名或密码错误"));
        }
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout(HttpSession session) {
        authService.clearCurrentUser(session);
        return ResponseEntity.ok(new AuthResponse(true, null, "退出成功"));
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/current-user")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        String username = authService.getCurrentUsername(session);
        if (username != null) {
            return ResponseEntity.ok(new AuthResponse(true, username, "已登录"));
        } else {
            return ResponseEntity.ok(new AuthResponse(false, null, "未登录"));
        }
    }

    /**
     * 检查认证状态（供前端页面加载时调用）
     */
    @GetMapping("/check-auth")
    public ResponseEntity<?> checkAuth(HttpSession session) {
        String username = authService.getCurrentUsername(session);
        log.info("检查认证状态: sessionId={}, user={}",
                session.getId(), username != null ? username : "null");
        if (username != null) {
            return ResponseEntity.ok(new AuthResponse(true, username, "已登录"));
        } else {
            return ResponseEntity.ok(new AuthResponse(false, null, "未登录"));
        }
    }
}

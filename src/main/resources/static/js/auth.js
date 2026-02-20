/**
 * 认证管理 - 多页面共享登录状态
 *
 * 功能：
 * 1. 自动检查登录状态
 * 2. 登录/退出功能
 * 3. 统一的 UI 显示
 * 4. 跨页面状态同步
 */

// ========== 全局状态 ==========
let currentUser = null;
let isAuthenticated = false;
let authInitialized = false;

// ========== DOM 元素获取函数 ==========
/**
 * 获取登录表单元素
 */
function getLoginForm() {
    return document.getElementById('loginForm');
}

/**
 * 获取用户信息元素
 */
function getUserInfo() {
    return document.getElementById('userInfo');
}

// ========== 初始化 ==========
/**
 * 页面加载时调用，初始化认证状态
 */
async function initAuth() {
    if (authInitialized) {
        console.log('[Auth] Already initialized, skipping...');
        return;
    }

    authInitialized = true;
    console.log('[Auth] Initializing...');

    // 等待 DOM 元素准备好
    await waitForElements();

    // 检查登录状态
    await checkAuth();

    // 监听页面可见性变化（从其他标签页切换回来时刷新状态）
    document.addEventListener('visibilitychange', async () => {
        if (!document.hidden) {
            console.log('[Auth] Page became visible, checking auth...');
            await checkAuth();
        }
    });

    // 监听存储变化（其他标签页登录/退出时同步）
    window.addEventListener('storage', async (e) => {
        if (e.key === 'auth-changed') {
            console.log('[Auth] Auth changed in another tab, syncing...');
            await checkAuth();
        }
    });

    console.log('[Auth] Initialization complete');
}

/**
 * 等待 DOM 元素准备好
 */
async function waitForElements() {
    const maxAttempts = 50;
    let attempts = 0;

    while (attempts < maxAttempts) {
        const loginForm = getLoginForm();
        const userInfo = getUserInfo();

        if (loginForm || userInfo) {
            console.log('[Auth] DOM elements ready');
            return;
        }

        console.log('[Auth] Waiting for DOM elements...');
        await new Promise(resolve => setTimeout(resolve, 100));
        attempts++;
    }

    console.warn('[Auth] DOM elements not found after maximum attempts');
}

/**
 * 检查认证状态
 */
async function checkAuth() {
    try {
        console.log('[Auth] Checking auth status...');

        // 显示所有Cookie用于调试
        console.log('[Auth] Cookies:', document.cookie);

        const response = await fetch('/api/auth/check-auth', {
            credentials: 'include',
            headers: {
                'Cache-Control': 'no-cache'
            }
        });

        const data = await response.json();
        console.log('[Auth] Check auth result:', data);

        if (data.success) {
            isAuthenticated = true;
            currentUser = data.username;
            showUserInfo(data.username);
        } else {
            isAuthenticated = false;
            currentUser = null;
            showLoginForm();
        }
    } catch (error) {
        console.error('[Auth] Failed to check auth:', error);
        showLoginForm();
    }
}

/**
 * 登录
 */
async function login() {
    const loginForm = getLoginForm();
    if (!loginForm) {
        showError('无法找到登录表单');
        return;
    }

    // 尝试多种方式获取用户名和密码输入框
    let usernameInput = loginForm.querySelector('#username') ||
                        loginForm.querySelector('#auth-username') ||
                        loginForm.querySelector('input[type="text"]');
    let passwordInput = loginForm.querySelector('#password') ||
                        loginForm.querySelector('#auth-password') ||
                        loginForm.querySelector('input[type="password"]');

    if (!usernameInput || !passwordInput) {
        showError('无法找到用户名或密码输入框');
        console.error('[Auth] usernameInput:', usernameInput, 'passwordInput:', passwordInput);
        return;
    }

    const username = usernameInput.value;
    const password = passwordInput.value;

    if (!username || !password) {
        showError('用户名和密码不能为空');
        return;
    }

    console.log('[Auth] Attempting login with username:', username);

    // 禁用登录按钮
    const loginBtn = loginForm.querySelector('button');
    if (loginBtn) {
        loginBtn.disabled = true;
        loginBtn.textContent = '登录中...';
    }

    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',  // 重要：包含Cookie
            body: JSON.stringify({ username, password })
        });

        const data = await response.json();
        console.log('[Auth] Login response:', data);

        if (data.success) {
            console.log('[Auth] Login successful:', data.username);

            // 等待一小段时间让Session保存
            await new Promise(resolve => setTimeout(resolve, 200));

            // 重新检查认证状态
            await checkAuth();

            // 通知其他标签页
            notifyAuthChange();

            // 刷新页面内容
            if (typeof window.onLoginSuccess === 'function') {
                window.onLoginSuccess();
            }
        } else {
            showError('登录失败：' + data.message);
            // 恢复登录按钮
            if (loginBtn) {
                loginBtn.disabled = false;
                loginBtn.textContent = '登录';
            }
        }
    } catch (error) {
        console.error('[Auth] Login error:', error);
        showError('登录失败：' + error.message);
        // 恢复登录按钮
        if (loginBtn) {
            loginBtn.disabled = false;
            loginBtn.textContent = '登录';
        }
    } finally {
        // 只有在失败时才恢复按钮（成功时按钮会被隐藏）
        if (loginBtn && loginBtn.style.display !== 'none') {
            loginBtn.disabled = false;
            loginBtn.textContent = '登录';
        }
    }
}

/**
 * 退出登录
 */
async function logout() {
    if (!confirm('确定要退出登录吗？')) {
        return;
    }

    try {
        await fetch('/api/auth/logout', {
            method: 'POST',
            credentials: 'include'
        });

        console.log('[Auth] Logout successful');

        // 通知其他标签页
        notifyAuthChange();

        isAuthenticated = false;
        currentUser = null;
        showLoginForm();

        // 执行页面特定的退出处理
        if (typeof window.onLogoutSuccess === 'function') {
            window.onLogoutSuccess();
        }

        // 重新加载页面
        location.reload();
    } catch (error) {
        console.error('[Auth] Logout error:', error);
        showError('退出失败：' + error.message);
    }
}

/**
 * 通知其他标签页认证状态已改变
 */
function notifyAuthChange() {
    localStorage.setItem('auth-changed', Date.now().toString());
}

/**
 * 显示登录表单
 */
function showLoginForm() {
    const loginForm = getLoginForm();
    const userInfo = getUserInfo();

    if (loginForm) {
        loginForm.style.display = 'flex';
    }
    if (userInfo) {
        userInfo.style.display = 'none';
    }

    console.log('[Auth] Showing login form');

    // 调用页面特定的处理
    if (typeof window.onShowLoginForm === 'function') {
        window.onShowLoginForm();
    }
}

/**
 * 显示用户信息
 */
function showUserInfo(username) {
    const loginForm = getLoginForm();
    const userInfo = getUserInfo();

    console.log('[Auth] showUserInfo called:', { username, loginForm, userInfo });

    if (loginForm) {
        loginForm.style.display = 'none';
        console.log('[Auth] Hidden login form');
    }
    if (userInfo) {
        userInfo.style.display = 'flex';
        const usernameEl = userInfo.querySelector('.username');
        console.log('[Auth] Found username element:', usernameEl);
        if (usernameEl) {
            usernameEl.textContent = username;
            console.log('[Auth] Set username to:', username, 'Element textContent is now:', usernameEl.textContent);
        }
    }

    console.log('[Auth] Showing user info:', username);

    // 调用页面特定的处理
    if (typeof window.onShowUserInfo === 'function') {
        window.onShowUserInfo(username);
    }
}

/**
 * 显示错误消息
 */
function showError(message) {
    const errorDiv = document.createElement('div');
    errorDiv.style.cssText = `
        position: fixed;
        top: 20px;
        left: 50%;
        transform: translateX(-50%);
        background: #dc3545;
        color: white;
        padding: 12px 24px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
        z-index: 10001;
        font-size: 14px;
        font-weight: 500;
        animation: slideDown 0.3s ease;
    `;
    errorDiv.textContent = message;
    document.body.appendChild(errorDiv);

    setTimeout(() => {
        errorDiv.style.animation = 'slideUp 0.3s ease';
        setTimeout(() => errorDiv.remove(), 300);
    }, 2000);
}

// ========== 页面特定回调（由各页面实现） ==========

/**
 * 登录成功回调（可选实现）
 */
if (typeof window.onLoginSuccess !== 'function') {
    window.onLoginSuccess = function() {
        // 默认实现：无操作
        console.log('[Auth] onLoginSuccess called');
    };
}

/**
 * 退出成功回调（可选实现）
 */
if (typeof window.onLogoutSuccess !== 'function') {
    window.onLogoutSuccess = function() {
        // 默认实现：无操作
        console.log('[Auth] onLogoutSuccess called');
    };
}

/**
 * 显示登录表单回调（可选实现）
 */
if (typeof window.onShowLoginForm !== 'function') {
    window.onShowLoginForm = function() {
        // 默认实现：无操作
        console.log('[Auth] onShowLoginForm called');
    };
}

/**
 * 显示用户信息回调（可选实现）
 */
if (typeof window.onShowUserInfo !== 'function') {
    window.onShowUserInfo = function(username) {
        // 默认实现：无操作
        console.log('[Auth] onShowUserInfo called:', username);
    };
}

// ========== 添加动画样式 ==========
const style = document.createElement('style');
style.textContent = `
    @keyframes slideDown {
        from {
            opacity: 0;
            transform: translateX(-50%) translateY(-20px);
        }
        to {
            opacity: 1;
            transform: translateX(-50%) translateY(0);
        }
    }

    @keyframes slideUp {
        from {
            opacity: 1;
            transform: translateX(-50%) translateY(0);
        }
        to {
            opacity: 0;
            transform: translateX(-50%) translateY(-20px);
        }
    }
`;
document.head.appendChild(style);

// ========== 延迟自动初始化 ==========
// 使用多个策略确保初始化执行
function scheduleInit() {
    if (authInitialized) return;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            setTimeout(initAuth, 100);
        });
    } else {
        // DOM 已就绪，延迟执行确保页面脚本加载完成
        setTimeout(initAuth, 300);
    }
}

// 立即调度初始化
scheduleInit();

// 同时监听 window.onload 作为后备
window.addEventListener('load', () => {
    setTimeout(() => {
        if (!authInitialized) {
            console.log('[Auth] Fallback initialization via window.onload');
            initAuth();
        }
    }, 500);
});

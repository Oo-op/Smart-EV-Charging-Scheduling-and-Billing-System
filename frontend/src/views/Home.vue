<template>
  <div class="home">
    <div class="background-decoration">
      <div class="decoration-circle circle-1"></div>
      <div class="decoration-circle circle-2"></div>
      <div class="decoration-circle circle-3"></div>
    </div>

    <section class="hero">
      <div class="hero-content">
        <div class="brand">
          <div class="logo">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="24" cy="24" r="20" fill="url(#gradient1)"/>
              <path d="M16 24h5v8h6v-8h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M24 16v5" stroke="white" stroke-width="2" stroke-linecap="round"/>
              <defs>
                <linearGradient id="gradient1" x1="24" y1="4" x2="24" y2="44" gradientUnits="userSpaceOnUse">
                  <stop stop-color="#5784FF"/>
                  <stop offset="1" stop-color="#FFC478"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h1>智能充电管理系统</h1>
          <p class="subtitle">便捷的充电预约与管理平台</p>
        </div>

        <aside class="auth-card">
          <div class="auth-tabs">
            <button type="button" :class="{ active: authMode === 'user-login' }" @click="authMode = 'user-login'">
              <span class="tab-icon">👤</span>
              <span>用户登录</span>
            </button>
            <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">
              <span class="tab-icon">📝</span>
              <span>用户注册</span>
            </button>
            <button type="button" :class="{ active: authMode === 'admin-login' }" @click="authMode = 'admin-login'">
              <span class="tab-icon">🔧</span>
              <span>管理员</span>
            </button>
          </div>

          <p v-if="message" class="message" :class="messageType">{{ message }}</p>

          <form v-if="authMode === 'register'" class="auth-form" @submit.prevent="handleRegister">
            <div class="form-group">
              <label>
                <span>用户名</span>
                <input v-model="registerForm.username" placeholder="请输入用户名" required />
              </label>
            </div>
            <div class="form-group">
              <label>
                <span>密码</span>
                <input v-model="registerForm.password" type="password" placeholder="至少 6 位" required />
              </label>
            </div>
            <div class="form-group">
              <label>
                <span>手机号</span>
                <input v-model="registerForm.phone" placeholder="用于接收通知" required />
              </label>
            </div>
            <button type="submit" class="btn-primary" :disabled="loading.register">
              <span v-if="loading.register" class="spinner"></span>
              {{ loading.register ? '注册中...' : '注册' }}
            </button>
          </form>

          <form v-else class="auth-form" @submit.prevent="handleLogin">
            <div class="form-group">
              <label>
                <span>{{ authMode === 'admin-login' ? '管理员账号' : '用户名' }}</span>
                <input v-model="loginForm.username" :placeholder="authMode === 'admin-login' ? 'admin' : '请输入用户名'" required />
              </label>
            </div>
            <div class="form-group">
              <label>
                <span>密码</span>
                <input v-model="loginForm.password" type="password" :placeholder="authMode === 'admin-login' ? 'admin123456' : '请输入密码'" required />
              </label>
            </div>
            <button type="submit" class="btn-primary" :disabled="loading.login">
              <span v-if="loading.login" class="spinner"></span>
              {{ loading.login ? '登录中...' : authMode === 'admin-login' ? '登录管理后台' : '登录' }}
            </button>
          </form>

          <div class="auth-note">
            <template v-if="authMode === 'admin-login'">
              <div class="note-icon">💡</div>
              <div class="note-content">
                <strong>演示账号</strong>
                <span>admin / admin123456</span>
              </div>
            </template>
            <template v-else>
              <div class="note-icon">📌</div>
              <div class="note-content">
                <span>还没有账号？点击上方"用户注册"创建</span>
              </div>
            </template>
          </div>
        </aside>
      </div>
    </section>
  </div>
</template>

<script>
import { loginUser, registerUser } from '../api';
import { authState, setSession } from '../session';

export default {
  name: 'Home',
  data() {
    return {
      authMode: 'user-login',
      loginForm: { username: '', password: '' },
      registerForm: { username: '', password: '', phone: '' },
      loading: { login: false, register: false },
      message: '',
      messageType: 'info'
    };
  },
  mounted() {
    const redirect = this.$route.query.redirect;
    if (authState.session.userId && !redirect) {
      this.redirectByRole(authState.session.role);
    }
  },
  methods: {
    openRole(mode) {
      this.authMode = mode;
      if (mode === 'admin-login') {
        this.loginForm.username ||= 'admin';
        this.loginForm.password ||= 'admin123456';
      }
    },
    notify(text, type = 'info') {
      this.message = text;
      this.messageType = type;
    },
    redirectByRole(role) {
      const redirect = this.$route.query.redirect;
      if (redirect) {
        this.$router.push(String(redirect));
        return;
      }
      this.$router.push(role === 'ADMIN' ? '/admin' : '/user');
    },
    async handleRegister() {
      this.loading.register = true;
      try {
        await registerUser(this.registerForm);
        this.notify('注册成功，请登录', 'success');
        this.loginForm.username = this.registerForm.username;
        this.loginForm.password = this.registerForm.password;
        this.authMode = 'user-login';
      } catch (error) {
        this.notify(error.message || '注册失败', 'error');
      } finally {
        this.loading.register = false;
      }
    },
    async handleLogin() {
      this.loading.login = true;
      try {
        const result = await loginUser(this.loginForm);
        if (this.authMode === 'admin-login' && result.role !== 'ADMIN') {
          throw new Error('当前账号不是管理员');
        }
        setSession(result);
        this.redirectByRole(result.role);
      } catch (error) {
        this.notify(error.message || '登录失败', 'error');
      } finally {
        this.loading.login = false;
      }
    }
  }
};
</script>

<style scoped>
.home {
  min-height: calc(100vh - 64px);
  padding: 40px 24px;
  color: #172033;
  background: linear-gradient(135deg, #f8fafc 0%, #e8f0fe 50%, #fef3e2 100%);
  position: relative;
  overflow: hidden;
}

.background-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.decoration-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, #5784FF 0%, transparent 70%);
  top: -100px;
  right: -100px;
  animation: float 8s ease-in-out infinite;
}

.circle-2 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, #FFC478 0%, transparent 70%);
  bottom: -50px;
  left: -50px;
  animation: float 6s ease-in-out infinite reverse;
}

.circle-3 {
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, #10B981 0%, transparent 70%);
  top: 50%;
  left: 10%;
  animation: float 10s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0px) rotate(0deg); }
  50% { transform: translateY(-20px) rotate(5deg); }
}

.hero {
  max-width: 500px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.hero-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.brand {
  margin-bottom: 40px;
}

.logo {
  margin-bottom: 16px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.brand h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: -0.5px;
}

.subtitle {
  margin: 12px 0 0;
  font-size: 16px;
  color: #64748b;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 32px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 
    0 25px 50px -12px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(0, 0, 0, 0.05);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.8);
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 6px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
}

.auth-tabs button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  border-radius: 12px;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.25s ease;
}

.auth-tabs button.active {
  background: #fff;
  color: #1e293b;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.tab-icon {
  font-size: 18px;
}

.message {
  margin: 20px 0 0;
  padding: 14px 16px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
}

.message.success {
  background: rgba(34, 197, 94, 0.1);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, 0.2);
}

.message.error {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.message.info {
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 24px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.auth-form label span {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  text-align: left;
}

.auth-form input {
  width: 100%;
  padding: 14px 16px;
  border: 2px solid #e2e8f0;
  border-radius: 14px;
  font-size: 15px;
  background: #f8fafc;
  color: #1e293b;
  transition: all 0.25s ease;
}

.auth-form input:focus {
  outline: none;
  border-color: #5784FF;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(87, 132, 255, 0.1);
}

.auth-form input::placeholder {
  color: #94a3b8;
}

.btn-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 14px 24px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  color: #fff;
  transition: all 0.25s ease;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(30, 41, 59, 0.3);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.auth-note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
  font-size: 13px;
  color: #64748b;
}

.note-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.note-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.note-content strong {
  color: #334155;
  font-weight: 600;
}

@media (max-width: 640px) {
  .home {
    padding: 32px 16px;
  }
  
  .brand h1 {
    font-size: 24px;
  }
  
  .auth-card {
    padding: 24px;
  }
  
  .auth-tabs button {
    padding: 10px 4px;
    font-size: 12px;
  }
  
  .tab-icon {
    font-size: 16px;
  }
}
</style>
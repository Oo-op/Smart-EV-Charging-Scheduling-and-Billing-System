<template>
  <div class="home">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">EV Charging System</p>
        <h1>智能充电管理系统</h1>
        <p class="subtitle">便捷的充电预约与管理平台</p>
      </div>

      <aside class="auth-card">
        <div class="auth-tabs">
          <button type="button" :class="{ active: authMode === 'user-login' }" @click="authMode = 'user-login'">用户登录</button>
          <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">用户注册</button>
          <button type="button" :class="{ active: authMode === 'admin-login' }" @click="authMode = 'admin-login'">管理员</button>
        </div>

        <p v-if="message" class="message" :class="messageType">{{ message }}</p>

        <form v-if="authMode === 'register'" class="auth-form" @submit.prevent="handleRegister">
          <label>
            <span>用户名</span>
            <input v-model="registerForm.username" placeholder="请输入用户名" required />
          </label>
          <label>
            <span>密码</span>
            <input v-model="registerForm.password" type="password" placeholder="至少 6 位" required />
          </label>
          <label>
            <span>手机号</span>
            <input v-model="registerForm.phone" placeholder="用于接收通知" required />
          </label>
          <button type="submit" class="primary wide" :disabled="loading.register">
            {{ loading.register ? '注册中...' : '注册' }}
          </button>
        </form>

        <form v-else class="auth-form" @submit.prevent="handleLogin">
          <label>
            <span>{{ authMode === 'admin-login' ? '管理员账号' : '用户名' }}</span>
            <input v-model="loginForm.username" :placeholder="authMode === 'admin-login' ? 'admin' : '请输入用户名'" required />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" :placeholder="authMode === 'admin-login' ? 'admin123456' : '请输入密码'" required />
          </label>
          <button type="submit" class="primary wide" :disabled="loading.login">
            {{ loading.login ? '登录中...' : authMode === 'admin-login' ? '登录管理后台' : '登录' }}
          </button>
        </form>

        <div class="auth-note">
          <template v-if="authMode === 'admin-login'">
            <span>默认账号：admin / admin123456</span>
          </template>
          <template v-else>
            <span>还没有账号？点击上方"用户注册"创建</span>
          </template>
        </div>
      </aside>
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
  padding: 42px 24px 72px;
  color: #172033;
  background:
    radial-gradient(circle at top left, rgba(255, 196, 120, 0.24), transparent 28%),
    radial-gradient(circle at top right, rgba(87, 132, 255, 0.18), transparent 24%),
    linear-gradient(180deg, #f7f5ef 0%, #eef4fb 100%);
}

.hero {
  max-width: 420px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #a05c1a;
}

.hero h1 {
  margin: 0;
  font-size: clamp(36px, 5vw, 62px);
  line-height: 1.06;
  max-width: 760px;
}

.subtitle {
  margin: 20px 0 0;
  max-width: 720px;
  font-size: 18px;
  line-height: 1.8;
  color: #49566f;
}

button {
  border: none;
  cursor: pointer;
}

.primary {
  padding: 12px 18px;
  border-radius: 999px;
  font-size: 14px;
  background: #172033;
  color: #fff;
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.primary:hover {
  transform: translateY(-1px);
}

.auth-card {
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 60px rgba(24, 36, 58, 0.08);
  backdrop-filter: blur(12px);
  padding: 22px;
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 6px;
  border-radius: 16px;
  background: #eef2f7;
}

.auth-tabs button {
  padding: 10px 0;
  border-radius: 12px;
  background: transparent;
  color: #647187;
}

.auth-tabs button.active {
  background: #fff;
  color: #172033;
  font-weight: 600;
}

.message {
  margin: 16px 0 0;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 14px;
}

.message.success {
  background: rgba(30, 107, 58, 0.1);
  color: #1e6b3a;
}

.message.error {
  background: rgba(188, 59, 47, 0.1);
  color: #bc3b2f;
}

.message.info {
  background: rgba(45, 93, 183, 0.1);
  color: #2d5db7;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 18px;
}

.auth-form label span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #67748a;
}

.auth-form input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(23, 32, 51, 0.12);
  border-radius: 14px;
  font-size: 15px;
}

.wide {
  width: 100%;
}

.auth-note {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(23, 32, 51, 0.08);
  font-size: 13px;
  color: #657189;
}

@media (max-width: 960px) {
  .hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home {
    padding: 28px 16px 48px;
  }
  .auth-tabs {
    grid-template-columns: 1fr;
  }
}
</style>

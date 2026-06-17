<template>
  <div class="home">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">Urban EV Charging Platform</p>
        <h1>把充电预约、排队调度、账单支付整合成一套真正可用的网页系统</h1>
        <p class="subtitle">
          用户侧完成注册、绑车、预约和支付；管理员侧查看运营数据、处理故障与队列。系统启动后会自动准备基础站点、充电桩、电价和管理员账号。
        </p>
        <div class="hero-actions">
          <button type="button" class="primary" @click="openRole('user-login')">用户登录</button>
          <button type="button" class="secondary" @click="openRole('register')">立即注册</button>
          <button type="button" class="ghost" @click="openRole('admin-login')">管理员登录</button>
        </div>
        <dl class="hero-metrics">
          <div>
            <dt>服务状态</dt>
            <dd :class="{ error: healthError }">{{ healthLabel }}</dd>
          </div>
          <div>
            <dt>默认管理账号</dt>
            <dd>`admin / admin123456`</dd>
          </div>
          <div>
            <dt>可用路由</dt>
            <dd>用户端 / 管理后台 / 站点总览</dd>
          </div>
        </dl>
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
            <input v-model="registerForm.username" placeholder="例如 zhangsan" required />
          </label>
          <label>
            <span>密码</span>
            <input v-model="registerForm.password" type="password" placeholder="至少 6 位" required />
          </label>
          <label>
            <span>手机号</span>
            <input v-model="registerForm.phone" placeholder="用于接收充电通知" required />
          </label>
          <button type="submit" class="primary wide" :disabled="loading.register">
            {{ loading.register ? '注册中...' : '创建用户账号' }}
          </button>
        </form>

        <form v-else class="auth-form" @submit.prevent="handleLogin">
          <label>
            <span>{{ authMode === 'admin-login' ? '管理员账号' : '用户名' }}</span>
            <input v-model="loginForm.username" :placeholder="authMode === 'admin-login' ? '默认 admin' : '请输入用户名'" required />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" :placeholder="authMode === 'admin-login' ? '默认 admin123456' : '请输入密码'" required />
          </label>
          <button type="submit" class="primary wide" :disabled="loading.login">
            {{ loading.login ? '登录中...' : authMode === 'admin-login' ? '登录管理后台' : '登录用户端' }}
          </button>
        </form>

        <div class="auth-note">
          <template v-if="authMode === 'admin-login'">
            <strong>管理员演示账号</strong>
            <span>系统已自动创建默认管理员，可直接登录进入后台。</span>
          </template>
          <template v-else>
            <strong>首次使用</strong>
            <span>用户只需要注册一次，之后系统会记住当前登录态。</span>
          </template>
        </div>
      </aside>
    </section>

    <section class="feature-grid">
      <article class="feature-card">
        <p class="feature-kicker">用户体验</p>
        <h2>少点运维按钮，多做业务动作</h2>
        <p>不再要求用户先健康检查、手动初始化、再分配桩位。系统启动即具备基础运行环境，用户直接进入核心流程。</p>
      </article>
      <article class="feature-card">
        <p class="feature-kicker">运营后台</p>
        <h2>管理员必须登录后才能看数据</h2>
        <p>后台路由和管理接口都挂在登录态之下，避免把运营数据和桩控操作裸露在公开页面。</p>
      </article>
      <article class="feature-card">
        <p class="feature-kicker">透明价格</p>
        <h2>分时电价直接可见</h2>
        <ul class="price-list">
          <li v-for="price in pricePreview" :key="`${price.period}-${price.mode}`">
            <strong>{{ price.period }} / {{ price.mode }}</strong>
            <span>电费 {{ price.chargingFee }} + 服务费 {{ price.serviceFee }}</span>
          </li>
        </ul>
      </article>
    </section>

    <section class="journey">
      <div class="journey-card">
        <h3>用户流程</h3>
        <ol>
          <li>注册并登录账号</li>
          <li>绑定车辆后直接预约</li>
          <li>系统自动尝试分配空闲桩位</li>
          <li>完成充电后自动生成账单并支付</li>
        </ol>
      </div>
      <div class="journey-card">
        <h3>管理流程</h3>
        <ol>
          <li>管理员登录后台</li>
          <li>查看今日收入、活动会话和排队情况</li>
          <li>处理故障桩与恢复调度</li>
          <li>持续观察站点运行状态</li>
        </ol>
      </div>
    </section>
  </div>
</template>

<script>
import { getHealth, getPrices, loginUser, registerUser } from '../api';
import { authState, setSession } from '../session';

export default {
  name: 'Home',
  data() {
    return {
      authMode: 'user-login',
      loginForm: { username: '', password: '' },
      registerForm: { username: '', password: '', phone: '' },
      loading: { login: false, register: false },
      pricePreview: [],
      healthText: '',
      healthError: '',
      message: '',
      messageType: 'info'
    };
  },
  computed: {
    healthLabel() {
      if (this.healthError) return this.healthError;
      return this.healthText || '检测中...';
    }
  },
  mounted() {
    this.fetchOverview();
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
    async fetchOverview() {
      try {
        const [health, prices] = await Promise.all([getHealth(), getPrices()]);
        this.healthText = health.status === 'UP' ? '运行中' : health.status;
        this.pricePreview = prices.slice(0, 4);
      } catch (error) {
        this.healthError = error.message || '后端未启动';
      }
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
        this.notify('注册成功，请直接登录开始使用', 'success');
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
        if (this.authMode !== 'admin-login' && result.role === 'ADMIN') {
          this.notify('该账号是管理员，已为你跳转到后台', 'info');
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

.hero,
.feature-grid,
.journey {
  max-width: 1180px;
  margin: 0 auto;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 380px;
  gap: 28px;
  align-items: start;
}

.eyebrow,
.feature-kicker {
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

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

button {
  border: none;
  cursor: pointer;
}

.primary,
.secondary,
.ghost {
  padding: 12px 18px;
  border-radius: 999px;
  font-size: 14px;
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.primary {
  background: #172033;
  color: #fff;
}

.secondary {
  background: #e6edf8;
  color: #172033;
}

.ghost {
  background: transparent;
  border: 1px solid rgba(23, 32, 51, 0.12);
  color: #172033;
}

.primary:hover,
.secondary:hover,
.ghost:hover {
  transform: translateY(-1px);
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 34px 0 0;
}

.hero-metrics div {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10px 30px rgba(32, 49, 78, 0.06);
}

.hero-metrics dt {
  margin-bottom: 8px;
  font-size: 12px;
  color: #6a7489;
}

.hero-metrics dd {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.hero-metrics dd.error {
  color: #bc3b2f;
}

.auth-card,
.feature-card,
.journey-card {
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 60px rgba(24, 36, 58, 0.08);
  backdrop-filter: blur(12px);
}

.auth-card {
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
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(23, 32, 51, 0.08);
  font-size: 13px;
  color: #657189;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 28px;
}

.feature-card {
  padding: 24px;
}

.feature-card h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.3;
}

.feature-card p,
.feature-card li,
.journey-card li {
  color: #566377;
  line-height: 1.7;
}

.price-list {
  padding: 0;
  margin: 18px 0 0;
  list-style: none;
}

.price-list li + li {
  margin-top: 12px;
}

.price-list strong {
  display: block;
  margin-bottom: 4px;
  color: #172033;
}

.journey {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.journey-card {
  padding: 24px;
}

.journey-card h3 {
  margin: 0 0 14px;
  font-size: 20px;
}

.journey-card ol {
  margin: 0;
  padding-left: 20px;
}

@media (max-width: 960px) {
  .hero,
  .feature-grid,
  .journey {
    grid-template-columns: 1fr;
  }
  .hero-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .home {
    padding: 28px 16px 48px;
  }
  .hero-actions {
    flex-direction: column;
  }
  .auth-tabs {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div id="app">
    <nav class="app-nav">
      <RouterLink to="/" class="brand">VoltFlow</RouterLink>
      <div class="links">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink v-if="session.role === 'USER'" to="/user">我的充电</RouterLink>
        <RouterLink v-if="session.role === 'ADMIN'" to="/admin">管理后台</RouterLink>
        <RouterLink to="/stations">充电站</RouterLink>
      </div>
      <div class="account">
        <template v-if="session.userId">
          <div class="user-chip">
            <span class="user-name">{{ session.username }}</span>
            <span class="user-role">{{ session.role === 'ADMIN' ? '管理员' : '用户' }}</span>
          </div>
          <button type="button" class="new-window-btn" title="在新窗口独立登录其他账号" @click="openNewWindow">
            新窗口
          </button>
          <button type="button" class="logout-btn" @click="logout">退出</button>
        </template>
        <RouterLink v-else class="login-link" to="/">登录</RouterLink>
      </div>
    </nav>
    <router-view />
  </div>
</template>

<script>
import { RouterLink } from 'vue-router';
import { authState, clearSession, openNewSessionWindow } from './session';

export default {
  name: 'App',
  components: { RouterLink },
  computed: {
    session() {
      return authState.session;
    }
  },
  methods: {
    openNewWindow() {
      openNewSessionWindow('/');
    },
    logout() {
      clearSession();
      if (this.$route.meta?.requiresAuth) {
        this.$router.push('/');
      }
    }
  }
};
</script>

<style>
body {
  margin: 0;
  background: #f4f7fb;
}

* {
  box-sizing: border-box;
}

#app {
  font-family: 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', sans-serif;
  min-height: 100vh;
}

.app-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 24px;
  background: rgba(15, 25, 43, 0.96);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(12px);
}

.app-nav .brand {
  font-weight: 700;
  text-decoration: none;
  color: #fff;
  letter-spacing: 0.04em;
}

.app-nav .links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  flex: 1;
}

.app-nav a {
  color: rgba(255, 255, 255, 0.82);
  text-decoration: none;
  font-size: 14px;
}

.app-nav a.router-link-active {
  color: #fff;
  font-weight: 600;
}

.account {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.user-name {
  font-size: 14px;
  color: #fff;
}

.user-role {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.66);
}

.new-window-btn,
.logout-btn,
.login-link {
  padding: 8px 14px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  background: transparent;
  color: #fff;
  cursor: pointer;
  text-decoration: none;
  font-size: 13px;
}

@media (max-width: 600px) {
  .app-nav {
    flex-direction: column;
    align-items: flex-start;
  }
  .account {
    width: 100%;
    justify-content: space-between;
  }
  .user-chip {
    align-items: flex-start;
  }
}
</style>

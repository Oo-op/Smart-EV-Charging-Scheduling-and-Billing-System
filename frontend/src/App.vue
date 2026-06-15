<template>
  <div id="app">
    <nav class="app-nav">
      <RouterLink to="/" class="brand">Smart Charging</RouterLink>
      <div class="links">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/user">用户端</RouterLink>
        <RouterLink to="/admin">管理端</RouterLink>
        <RouterLink to="/stations">充电站</RouterLink>
      </div>
    </nav>
    <router-view />
  </div>
</template>

<script>
import { RouterLink } from 'vue-router';

export default {
  name: 'App',
  components: { RouterLink }
};
</script>

<style>
body {
  margin: 0;
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
  gap: 16px;
  padding: 14px 24px;
  background: rgba(23, 32, 51, 0.96);
  color: #fff;
}

.app-nav .brand {
  font-weight: 600;
  text-decoration: none;
  color: #fff;
  letter-spacing: 0.02em;
}

.app-nav .links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
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

@media (max-width: 600px) {
  .app-nav {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

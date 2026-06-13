<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'App'
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
</style>

<template>
  <router-view />
</template>

<script setup>
// App is now just a router container
</script>

<style>
/* 全局样式 - 强制启用滚动 */
html {
  margin: 0;
  padding: 0;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  overflow: auto !important; /* 强制显示滚动条 */
  overflow-x: hidden; /* 只隐藏水平滚动条 */
  overflow-y: auto !important; /* 强制显示垂直滚动条 */
}

body {
  margin: 0;
  padding: 0;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  overflow: auto !important; /* 强制显示滚动条 */
  overflow-x: hidden; /* 只隐藏水平滚动条 */
  overflow-y: auto !important; /* 强制显示垂直滚动条 */
  min-height: 100vh;
}

#app {
  overflow: visible !important; /* 确保app容器不阻止滚动 */
  min-height: 100vh;
}

* {
  box-sizing: border-box;
}

/* 强制重置任何可能阻止滚动的样式 */
html, body, #app {
  height: auto !important;
  max-height: none !important;
}
</style>

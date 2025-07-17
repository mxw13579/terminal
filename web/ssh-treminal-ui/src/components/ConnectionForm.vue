<!-- /components/ConnectionForm.vue -->
<template>
  <div class="connection-view">
    <div class="connection-form-card">
      <div class="form-header">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="form-icon">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
        </svg>
        <h3>远程 Shell 连接</h3>
      </div>
      <div class="form-fields">
        <input v-model="host" placeholder="主机 (e.g., 192.168.1.100)" @keyup.enter="doConnect" autocomplete="host"/>
        <input v-model="port" placeholder="端口 (默认: 22)" type="number" @keyup.enter="doConnect" autocomplete="port"/>
        <input v-model="user" placeholder="用户名" @keyup.enter="doConnect" autocomplete="username"/>
        <input v-model="password" placeholder="密码" type="password" @keyup.enter="doConnect" autocomplete="current-password"/>
      </div>
      <div class="form-actions">
        <button type="button" class="btn btn-primary" @click="doConnect" :disabled="isConnecting">
          <svg v-if="!isConnecting" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.72"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.72-1.72"></path>
          </svg>
          <span v-if="!isConnecting">连接</span>
          <span v-if="isConnecting">连接中...</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

defineProps({
  isConnecting: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['connect']);

const host = ref('110.40.59.75');
const port = ref(37955);
const user = ref('root');
const password = ref('4KnHprKJp4c7xnLX');

const doConnect = () => {
  if (!host.value || !user.value || !password.value) {
    // 简单的前端提示，复杂的可以用emit通知父组件显示Modal
    alert("请填写所有连接信息！");
    return;
  }
  emit('connect', {
    host: host.value,
    port: port.value,
    user: user.value,
    password: password.value
  });
};
</script>

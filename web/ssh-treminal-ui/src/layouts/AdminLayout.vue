<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <h2>管理后台</h2>
      </div>
      <nav class="sidebar-nav">
        <router-link to="/admin/dashboard/script-groups" class="nav-item">
          <i class="icon">📁</i>
          脚本分组管理
        </router-link>
        <router-link to="/admin/dashboard/scripts" class="nav-item">
          <i class="icon">⚛️</i>
          原子脚本管理
        </router-link>
        <router-link to="/admin/dashboard/aggregated-scripts" class="nav-item">
          <i class="icon">🔗</i>
          聚合脚本管理
        </router-link>
        <router-link to="/admin/dashboard/aggregated-script-builder" class="nav-item">
          <i class="icon">🔧</i>
          聚合脚本构建器
        </router-link>
        <router-link to="/admin/dashboard/users" class="nav-item">
          <i class="icon">👥</i>
          用户管理
        </router-link>
      </nav>
    </aside>
    
    <div class="main-wrapper">
      <header class="header">
        <div class="header-content">
          <h1 class="page-title">{{ pageTitle }}</h1>
          <div class="header-actions">
            <button @click="logout" class="logout-btn">退出登录</button>
          </div>
        </div>
      </header>
      
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AdminLayout',
  computed: {
    pageTitle() {
      return this.$route.meta?.title || '管理后台'
    }
  },
  methods: {
    logout() {
      localStorage.removeItem('user')
      this.$router.push('/admin/login')
    }
  }
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background-color: #f5f5f5;
}

.sidebar {
  width: 250px;
  background-color: #2c3e50;
  color: white;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 1.5rem;
  border-bottom: 1px solid #34495e;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 1.25rem;
}

.sidebar-nav {
  flex: 1;
  padding: 1rem 0;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 0.75rem 1.5rem;
  color: white;
  text-decoration: none;
  transition: background-color 0.3s;
  gap: 0.75rem;
}

.nav-item:hover,
.nav-item.router-link-active {
  background-color: #34495e;
}

.icon {
  font-size: 1.1rem;
}

.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.header {
  background-color: white;
  border-bottom: 1px solid #e0e0e0;
  padding: 1rem 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 1.5rem;
  color: #2c3e50;
}

.logout-btn {
  background-color: #e74c3c;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.logout-btn:hover {
  background-color: #c0392b;
}

.main-content {
  flex: 1;
  padding: 2rem;
  overflow-y: auto;
}
</style>
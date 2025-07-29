<template>
  <div class="user-layout">
    <header class="header">
      <div class="container">
        <h1 class="logo">SSH管理工具</h1>
        <nav class="navigation">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/terminal" class="nav-link">终端</router-link>
        </nav>
      </div>
    </header>
    
    <main class="main-content">
      <div class="container">
        <router-view />
      </div>
    </main>
    
    <footer class="footer">
      <div class="container">
        <p>&copy; 2024 SSH管理工具</p>
      </div>
    </footer>
  </div>
</template>

<script>
export default {
  name: 'UserLayout'
}
</script>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background-color: #2c3e50;
  color: white;
  padding: 1rem 0;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  margin: 0;
  font-size: 1.5rem;
}

.navigation {
  display: flex;
  gap: 1rem;
}

.nav-link {
  color: white;
  text-decoration: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.nav-link:hover,
.nav-link.router-link-active {
  background-color: #34495e;
}

.main-content {
  flex: 1;
  padding: 2rem 0;
  background-color: #f8f9fa;
}

.footer {
  background-color: #34495e;
  color: white;
  padding: 1rem 0;
  text-align: center;
}

.footer p {
  margin: 0;
}
</style>
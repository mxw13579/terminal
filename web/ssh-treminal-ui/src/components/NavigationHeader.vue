<template>
  <header class="navigation-header">
    <div class="breadcrumb">
      <router-link to="/" class="breadcrumb-item" :class="{ active: $route.path === '/' }">
        <span class="breadcrumb-icon">🏠</span>
        控制面板
      </router-link>
      <template v-if="$route.path !== '/'">
        <span class="breadcrumb-separator">›</span>
        <span class="breadcrumb-item active">
          <span class="breadcrumb-icon">{{ getRouteIcon($route.name) }}</span>
          {{ $route.meta.title }}
        </span>
      </template>
    </div>
    <div class="header-actions">
      <slot name="actions"></slot>
    </div>
  </header>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()

const getRouteIcon = (routeName) => {
  switch (routeName) {
    case 'Terminal':
      return '💻'
    case 'SillyTavern':
      return '🤖'
    default:
      return '📄'
  }
}
</script>

<style scoped>
.navigation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 100;
}

.breadcrumb {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 500;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  text-decoration: none;
  color: rgba(255, 255, 255, 0.8);
  transition: color 0.2s ease;
  padding: 6px 10px;
  border-radius: 6px;
}

.breadcrumb-item:hover {
  color: white;
  background: rgba(255, 255, 255, 0.1);
}

.breadcrumb-item.active {
  color: white;
  background: rgba(255, 255, 255, 0.15);
}

.breadcrumb-icon {
  margin-right: 6px;
  font-size: 14px;
}

.breadcrumb-separator {
  margin: 0 8px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 18px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* Responsive design */
@media (max-width: 768px) {
  .navigation-header {
    padding: 10px 15px;
  }
  
  .breadcrumb {
    font-size: 14px;
  }
  
  .breadcrumb-item {
    padding: 4px 8px;
  }
  
  .breadcrumb-icon {
    font-size: 12px;
  }
}
</style>
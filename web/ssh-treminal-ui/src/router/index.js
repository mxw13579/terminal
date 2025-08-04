import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Terminal from '../views/Terminal.vue'
import SillyTavernConsole from '../views/SillyTavernConsole.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard,
    meta: {
      title: '控制面板'
    }
  },
  {
    path: '/terminal',
    name: 'Terminal',
    component: Terminal,
    meta: {
      title: 'SSH 终端'
    }
  },
  {
    path: '/sillytavern',
    name: 'SillyTavern',
    component: SillyTavernConsole,
    meta: {
      title: 'SillyTavern 管理'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Set page title based on route metadata
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 终端管理` : '终端管理'
  next()
})

export default router
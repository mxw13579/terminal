import { createRouter, createWebHistory } from 'vue-router'

// 用户端页面 (无需登录) 
import UserLayout from '@/layouts/UserLayout.vue'
import UserHome from '@/views/user/Home.vue'
import UserScriptExecution from '@/views/user/ScriptExecution.vue'
import Terminal from '@/views/Terminal.vue'

// 管理端页面 (需要登录)
import Login from '@/views/Login.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import AdminScriptGroups from '@/views/admin/ScriptGroups.vue'
import AdminScripts from '@/views/admin/Scripts.vue'
import AdminAggregatedScripts from '@/views/admin/AggregatedScripts.vue'  // 新增聚合脚本管理
import AdminUsers from '@/views/admin/Users.vue'
import ScriptBuilder from '@/views/ScriptBuilder.vue'

const routes = [
  // 用户端路由 (无需登录的独立应用)
  {
    path: '/',
    component: UserLayout,
    children: [
      {
        path: '',
        name: 'UserHome',
        component: UserHome,
        meta: { 
          title: 'SSH管理工具',
          description: '脚本分组和SSH连接'
        }
      },
      {
        path: 'script-execution/:groupId',
        name: 'UserScriptExecution',
        component: UserScriptExecution,
        props: true,
        meta: { 
          title: '脚本执行',
          description: '脚本执行和实时日志'
        }
      },
      {
        path: 'terminal',
        name: 'Terminal', 
        component: Terminal,
        meta: { 
          title: 'SSH终端',
          description: '专业用户使用的SSH终端界面'
        }
      }
    ]
  },
  
  // 管理端路由 (需要登录)
  {
    path: '/admin',
    redirect: (to) => {
      const user = getCurrentUser()
      return user && user.role === 'ADMIN' ? '/admin/dashboard' : '/admin/login'
    }
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: Login,
    meta: { 
      title: '管理员登录',
      hideForAuth: true
    }
  },
  {
    path: '/admin/dashboard',
    component: AdminLayout,
    meta: { requiresAuth: true, role: 'ADMIN' },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard/script-groups'
      },
      {
        path: 'script-groups',
        name: 'AdminScriptGroups',
        component: AdminScriptGroups,
        meta: { 
          title: '脚本分组管理',
          description: '管理脚本分组和初始化配置'
        }
      },
      {
        path: 'scripts',
        name: 'AdminScripts', 
        component: AdminScripts,
        meta: { 
          title: '原子脚本管理',
          description: '管理基础原子脚本'
        }
      },
      {
        path: 'aggregated-scripts',
        name: 'AdminAggregatedScripts',
        component: AdminAggregatedScripts,
        meta: { 
          title: '聚合脚本管理',
          description: '组合原子脚本为聚合脚本'
        }
      },
      {
        path: 'aggregated-script-builder',
        name: 'AggregatedScriptBuilder',
        component: ScriptBuilder,
        meta: { 
          title: '聚合脚本构建器',
          description: '可视化构建聚合脚本'
        }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: AdminUsers,
        meta: { 
          title: '用户管理',
          description: '用户权限和账户管理'
        }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 获取当前用户信息
const getCurrentUser = () => {
  const userStr = localStorage.getItem('user')
  return userStr ? JSON.parse(userStr) : null
}

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  
  const user = getCurrentUser()
  
  // 特殊处理：防止访问 /user 路径
  if (to.path === '/user' || to.path.startsWith('/user/')) {
    next('/')
    return
  }
  
  // 防止访问不存在的路径
  const isValidRoute = routes.some(route => {
    // 检查顶级路径
    if (route.path === to.path) return true
    
    // 检查子路径  
    if (route.children) {
      return route.children.some(child => {
        if (route.path === '/') {
          return child.path === to.path.substring(1) || child.path === to.path
        } else {
          const fullPath = route.path + (child.path.startsWith('/') ? '' : '/') + child.path
          return fullPath === to.path
        }
      })
    }
    
    // 检查动态路由匹配
    if (route.path.includes(':')) {
      const routeRegex = new RegExp('^' + route.path.replace(/:[^/]+/g, '[^/]+') + '$')
      return routeRegex.test(to.path)
    }
    
    return false
  })
  
  if (!isValidRoute) {
    // 如果路径不存在，根据路径判断应该跳转到用户端还是管理端
    if (to.path.startsWith('/admin')) {
      next('/admin/login')
    } else {
      next('/')
    }
    return
  }
  
  // 如果已登录管理员访问登录页，重定向到管理后台
  if (to.meta.hideForAuth && user && user.role === 'ADMIN') {
    next('/admin/dashboard')
    return
  }
  
  // 管理端需要登录验证
  if (to.meta.requiresAuth) {
    if (!user) {
      next('/admin/login')
      return
    }
    
    // 检查角色权限
    if (to.meta.role && to.meta.role !== user.role) {
      next('/admin/login')
      return
    }
  }
  
  next()
})

export default router
import { createRouter, createWebHistory } from 'vue-router'
import ScriptBuilder from '@/views/ScriptBuilder.vue'
import Terminal from '@/views/Terminal.vue'

const routes = [
  {
    path: '/',
    redirect: '/script-builder'
  },
  {
    path: '/script-builder',
    name: 'ScriptBuilder',
    component: ScriptBuilder,
    meta: { 
      title: '可视化环境配置',
      description: '小白用户友好的拖拉拽脚本编辑器'
    }
  },
  {
    path: '/terminal',
    name: 'Terminal', 
    component: Terminal,
    meta: { 
      title: 'SSH终端',
      description: '专业用户使用的SSH终端界面'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 设置页面标题
router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title + ' - SSH管理工具'
  }
  next()
})

export default router
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/home', component: () => import('../views/Home.vue') },
  { path: '/shop/:id', component: () => import('../views/ShopDetail.vue') },
  { path: '/blog', component: () => import('../views/BlogList.vue') },
  { path: '/blog/:id', component: () => import('../views/BlogDetail.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.token) {
    return { path: '/login' }
  }
})

export default router

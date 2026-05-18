<template>
  <header class="app-header">
    <div class="header-content">
      <div class="logo" @click="$router.push('/home')">
        <span class="logo-icon">🍜</span>
        <span class="logo-text serif">黑马点评</span>
      </div>
      <nav class="nav">
        <router-link to="/home" class="nav-link" active-class="active">
          🏪 商铺
        </router-link>
        <router-link to="/blog" class="nav-link" active-class="active">
          ✨ 探店
        </router-link>
      </nav>
      <div class="user-area">
        <template v-if="userStore.user">
          <div class="user-chip">
            <div class="avatar">
              {{ userStore.user.nickName?.[0]?.toUpperCase() || 'U' }}
            </div>
            <span class="nick">{{ userStore.user.nickName }}</span>
          </div>
        </template>
        <button class="logout-btn" @click="logout">退出</button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const router = useRouter()

function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  background: #fff;
  border-radius: 999px;
  margin: 20px auto 0;
  max-width: 1200px;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}
.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.logo-icon {
  font-size: 28px;
}
.logo-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--hm-black);
}
.nav {
  display: flex;
  gap: 6px;
  flex: 1;
  justify-content: center;
}
.nav-link {
  padding: 8px 18px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  color: #555;
  text-decoration: none;
  transition: all 0.15s;
}
.nav-link:hover {
  background: #f3f4ef;
  color: var(--hm-black);
}
.nav-link.active {
  background: var(--hm-black);
  color: var(--hm-lime);
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px 6px 6px;
  background: var(--hm-lime);
  border-radius: 999px;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--hm-black);
  color: var(--hm-lime);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
}
.nick {
  font-size: 14px;
  font-weight: 600;
  color: var(--hm-black);
}
.logout-btn {
  background: var(--hm-black);
  color: #fff;
  border: none;
  padding: 10px 20px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.logout-btn:hover {
  background: #1f1f1f;
}
</style>

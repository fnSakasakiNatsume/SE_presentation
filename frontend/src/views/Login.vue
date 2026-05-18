<template>
  <div class="login-page">
    <div class="login-grid">
      <!-- 左：插画/标语 -->
      <div class="brand-side">
        <div class="brand-logo">🍜 黑马点评</div>
        <h1 class="brand-title serif">
          Let's <br />
          Seckill!
        </h1>
        <p class="brand-desc">
          High-concurrency seckill platform powered by<br />
          Redis · Kafka · Lua · Distributed Lock
        </p>
        <div class="brand-bottom">
          <div class="dot"></div>
          软工课程展示 · 2026
        </div>
      </div>

      <!-- 右：登录表单 -->
      <div class="form-side">
        <div class="form-card">
          <div class="form-header">
            <span class="badge">Step 01 / 短信验证码登录</span>
            <h2 class="serif">登录账号</h2>
            <p class="subtitle">
              输入手机号 + 验证码即可登录，验证码会打印在后端控制台。
            </p>
          </div>

          <el-form :model="form" label-position="top" @submit.prevent="onLogin">
            <el-form-item label="手机号">
              <el-input
                v-model="form.phone"
                placeholder="11 位手机号"
                maxlength="11"
                size="large"
              >
                <template #append>
                  <el-button
                    :disabled="cooldown > 0 || !form.phone"
                    @click="onSendCode"
                  >
                    {{ cooldown > 0 ? `${cooldown}s 后重发` : '发送验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="验证码">
              <el-input
                v-model="form.code"
                placeholder="6 位数字验证码"
                maxlength="6"
                size="large"
                @keyup.enter="onLogin"
              />
            </el-form-item>

            <div class="actions">
              <el-button
                type="primary"
                :loading="loading"
                @click="onLogin"
                size="large"
                style="width: 100%"
              >
                登 录 →
              </el-button>
            </div>
          </el-form>

          <div class="tip-box">
            <div class="tip-icon">💡</div>
            <div>
              验证码不发短信，请在 <b>IDEA 控制台</b> 找：<br />
              <code>短信验证码发送成功：xxxxxx</code>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendCode, login, getMe } from '../api'
import { useUserStore } from '../stores/user'

const form = reactive({ phone: '', code: '' })
const loading = ref(false)
const cooldown = ref(0)
let timer = null

const router = useRouter()
const userStore = useUserStore()

async function onSendCode() {
  if (!/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不正确（11 位数字，1 开头）')
    return
  }
  try {
    await sendCode(form.phone)
    ElMessage.success('验证码已发送，请在后端控制台查看')
    cooldown.value = 60
    timer = setInterval(() => {
      cooldown.value--
      if (cooldown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {}
}

async function onLogin() {
  if (!form.phone || !form.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  loading.value = true
  try {
    const res = await login({ phone: form.phone, code: form.code })
    userStore.setToken(res.data)
    const me = await getMe()
    userStore.setUser(me.data)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) {
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: var(--hm-lime);
  padding: 40px 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  max-width: 1200px;
  width: 100%;
  background: #fff;
  border-radius: 32px;
  overflow: hidden;
  min-height: 640px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.08);
}
.brand-side {
  background: var(--hm-lime);
  padding: 56px 48px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: var(--hm-black);
  position: relative;
  overflow: hidden;
}
.brand-side::before {
  content: '';
  position: absolute;
  width: 320px;
  height: 320px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 50%;
  bottom: -80px;
  right: -80px;
}
.brand-logo {
  font-size: 22px;
  font-weight: 700;
}
.brand-title {
  font-size: 72px;
  line-height: 0.95;
  font-weight: 700;
  margin: 24px 0;
  position: relative;
  z-index: 1;
}
.brand-desc {
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.7;
  margin: 0;
  position: relative;
  z-index: 1;
}
.brand-bottom {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 500;
  position: relative;
  z-index: 1;
}
.dot {
  width: 8px;
  height: 8px;
  background: var(--hm-black);
  border-radius: 50%;
}

.form-side {
  padding: 56px 48px;
  display: flex;
  align-items: center;
}
.form-card {
  width: 100%;
}
.form-header {
  margin-bottom: 28px;
}
.badge {
  display: inline-block;
  padding: 4px 12px;
  background: var(--hm-lime);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--hm-black);
  margin-bottom: 16px;
}
.form-header h2 {
  font-size: 40px;
  margin: 8px 0;
  font-weight: 700;
}
.subtitle {
  color: var(--hm-muted);
  font-size: 14px;
  margin: 0;
  line-height: 1.6;
}
.actions {
  margin-top: 8px;
}
.tip-box {
  margin-top: 24px;
  padding: 16px;
  background: #f6f7f4;
  border-radius: 12px;
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #555;
  line-height: 1.6;
}
.tip-icon {
  font-size: 18px;
}
code {
  background: var(--hm-lime);
  padding: 2px 8px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: var(--hm-black);
  font-weight: 600;
}

@media (max-width: 900px) {
  .login-grid { grid-template-columns: 1fr; min-height: auto; }
  .brand-side { padding: 40px 32px; }
  .brand-title { font-size: 52px; }
  .form-side { padding: 40px 32px; }
}
</style>

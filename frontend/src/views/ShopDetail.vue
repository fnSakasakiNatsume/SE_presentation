<template>
  <div class="shop-detail">
    <AppHeader />
    <div class="content" v-loading="loading">
      <button class="back-btn" @click="$router.push('/home')">← Back to home</button>

      <!-- 商铺信息 -->
      <section v-if="shop" class="hero-card">
        <div class="hero-grid">
          <div class="hero-text">
            <span class="hero-badge">📍 {{ shop.area }}</span>
            <h1 class="serif hero-name">{{ shop.name }}</h1>
            <div class="meta-row">
              <span class="meta-pill dark">
                ⭐ {{ (shop.score / 10).toFixed(1) }}
              </span>
              <span class="meta-pill">¥{{ shop.avgPrice }} / person</span>
              <span class="meta-pill">🕒 {{ shop.openHours }}</span>
            </div>
            <p class="addr">📌 {{ shop.address }}</p>
            <div class="stats">
              <div class="stat">
                <div class="stat-num">{{ shop.sold }}</div>
                <div class="stat-label">Sold</div>
              </div>
              <div class="stat-divider"></div>
              <div class="stat">
                <div class="stat-num">{{ shop.comments }}</div>
                <div class="stat-label">Reviews</div>
              </div>
              <div class="stat-divider"></div>
              <div class="stat">
                <div class="stat-num">{{ vouchers.length }}</div>
                <div class="stat-label">Vouchers</div>
              </div>
            </div>
          </div>
          <div class="hero-image">
            <img
              v-if="shopImages.length"
              :src="shopImages[0]"
              alt=""
              @error="onImgError"
            />
            <div v-else class="img-fallback">🍽</div>
          </div>
        </div>
      </section>

      <!-- 优惠券 -->
      <section class="section">
        <div class="section-head">
          <div>
            <h2 class="serif">🎟 Vouchers / Seckill</h2>
            <p class="section-sub">Live stock, ready to seckill →</p>
          </div>
          <button class="ghost-btn" @click="dialog = true">
            🛠 Create voucher (demo)
          </button>
        </div>

        <div v-if="!vouchers.length" class="empty-card">
          <div class="empty-icon">🎫</div>
          <p>No vouchers for this shop yet</p>
          <button class="lime-btn" @click="dialog = true">
            Create a seckill voucher →
          </button>
        </div>

        <div v-else class="vouchers">
          <div
            v-for="v in vouchers"
            :key="v.id"
            class="voucher"
            :class="{ 'is-seckill': v.stock != null }"
          >
            <div class="v-left">
              <div class="v-tag-row">
                <span class="v-tag" :class="v.stock != null ? 'red' : 'gray'">
                  {{ v.stock != null ? 'Seckill' : 'Coupon' }}
                </span>
                <span v-if="v.stock != null" class="v-stock-tag">
                  {{ v.stock }} left
                </span>
              </div>
              <h3 class="v-title">{{ v.title }}</h3>
              <p class="v-sub">{{ v.subTitle }}</p>
              <p class="v-rules">📋 {{ v.rules }}</p>
              <p v-if="v.beginTime" class="v-time">
                ⏰ {{ formatTime(v.beginTime) }} ~ {{ formatTime(v.endTime) }}
              </p>
            </div>
            <div class="v-right">
              <div class="v-price">
                <span class="currency">¥</span>
                <span class="amount">{{ (v.payValue / 100).toFixed(0) }}</span>
              </div>
              <div class="v-discount">
                Save ¥{{ ((v.actualValue - v.payValue) / 100).toFixed(0) }}
              </div>
              <button
                v-if="v.stock != null"
                class="seckill-btn"
                :disabled="buying === v.id"
                @click="onSeckill(v.id)"
              >
                {{ buying === v.id ? 'Buying…' : 'Seckill Now' }}
              </button>
              <button v-else class="ghost-btn small" disabled>Coupon</button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 创建秒杀券 dialog -->
    <el-dialog
      v-model="dialog"
      title="🛠 Create Seckill Voucher (Demo Tool)"
      width="520px"
      class="hm-dialog"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          Calls <code>POST /voucher/seckill</code> to create one voucher<br />
          and write the stock to both MySQL and Redis.
        </template>
      </el-alert>
      <el-form :model="newVoucher" label-position="top">
        <el-form-item label="Title">
          <el-input v-model="newVoucher.title" />
        </el-form-item>
        <el-form-item label="Subtitle">
          <el-input v-model="newVoucher.subTitle" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="Pay amount (cents)">
              <el-input-number v-model="newVoucher.payValue" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Face value (cents)">
              <el-input-number v-model="newVoucher.actualValue" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Stock">
          <el-input-number v-model="newVoucher.stock" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Rules">
          <el-input v-model="newVoucher.rules" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">Cancel</el-button>
        <el-button type="primary" :loading="creating" @click="onCreate">
          Create
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppHeader from '../components/AppHeader.vue'
import {
  getShopById,
  getVoucherList,
  seckillVoucher,
  createSeckillVoucher
} from '../api'

const route = useRoute()
const shopId = route.params.id
const shop = ref(null)
const vouchers = ref([])
const loading = ref(false)
const buying = ref(null)
const imgError = ref(false)

const shopImages = computed(() => {
  if (imgError.value) return []
  return shop.value?.images?.split(',').filter(Boolean) || []
})

function onImgError() {
  imgError.value = true
}

const dialog = ref(false)
const creating = ref(false)
const newVoucher = ref({
  title: '[Demo Seckill] ¥100 Voucher',
  subTitle: 'SE Presentation Day Exclusive',
  payValue: 5000,
  actualValue: 10000,
  stock: 100,
  rules: 'All-day use / No reservation / Non-refundable / No change given'
})

onMounted(load)

async function load() {
  loading.value = true
  imgError.value = false
  try {
    const [shopRes, vRes] = await Promise.all([
      getShopById(shopId),
      getVoucherList(shopId)
    ])
    shop.value = shopRes.data
    vouchers.value = vRes.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function onSeckill(id) {
  buying.value = id
  try {
    const res = await seckillVoucher(id)
    await ElMessageBox.alert(
      `🎉 Seckill succeeded!\n\nOrder ID: ${res.data}\n\nBackend pipeline:\nLua precheck → Redis stock decrement → Kafka async insert\n\nCheck the IDE console / tb_voucher_order table to verify.`,
      'Success',
      { type: 'success', confirmButtonText: 'OK' }
    )
    await load()
  } catch (e) {
  } finally {
    buying.value = null
  }
}

async function onCreate() {
  creating.value = true
  try {
    const now = new Date()
    const end = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)
    const body = {
      shopId: Number(shopId),
      title: newVoucher.value.title,
      subTitle: newVoucher.value.subTitle,
      rules: newVoucher.value.rules,
      payValue: newVoucher.value.payValue,
      actualValue: newVoucher.value.actualValue,
      type: 1,
      stock: newVoucher.value.stock,
      beginTime: formatDateTime(now),
      endTime: formatDateTime(end)
    }
    await createSeckillVoucher(body)
    ElMessage.success('✅ Voucher created')
    dialog.value = false
    await load()
  } catch (e) {
  } finally {
    creating.value = false
  }
}

function formatTime(t) {
  return String(t).replace('T', ' ').slice(0, 16)
}
function formatDateTime(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>

<style scoped>
.shop-detail {
  min-height: 100vh;
  background: var(--hm-lime);
  padding-bottom: 60px;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}
.back-btn {
  margin: 24px 0 16px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.6);
  border: none;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.back-btn:hover { background: #fff; }

/* Hero */
.hero-card {
  background: #fff;
  border-radius: 28px;
  padding: 40px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}
.hero-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 32px;
  align-items: center;
}
.hero-badge {
  display: inline-block;
  background: var(--hm-lime);
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}
.hero-name {
  font-size: 48px;
  line-height: 1.1;
  font-weight: 700;
  margin: 16px 0 20px;
}
.meta-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.meta-pill {
  padding: 6px 14px;
  background: #f6f7f4;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.meta-pill.dark {
  background: var(--hm-black);
  color: var(--hm-lime);
}
.addr {
  color: var(--hm-muted);
  margin: 0 0 24px;
  font-size: 14px;
}
.stats {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}
.stat-num {
  font-size: 26px;
  font-weight: 700;
  font-family: 'Playfair Display', serif;
}
.stat-label {
  font-size: 12px;
  color: var(--hm-muted);
  font-weight: 500;
}
.stat-divider {
  width: 1px;
  height: 32px;
  background: #eee;
}
.hero-image {
  border-radius: 20px;
  overflow: hidden;
  aspect-ratio: 1;
  background: var(--hm-lime);
}
.hero-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.img-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 80px;
  color: rgba(0, 0, 0, 0.3);
}

/* Section */
.section { margin-top: 40px; }
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 20px;
  padding: 0 8px;
}
.section-head h2 {
  font-size: 32px;
  margin: 0;
  font-weight: 700;
}
.section-sub {
  margin: 4px 0 0;
  color: rgba(0, 0, 0, 0.55);
  font-size: 13px;
}

/* Vouchers */
.empty-card {
  background: #fff;
  border-radius: 24px;
  padding: 60px 20px;
  text-align: center;
}
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-card p { color: var(--hm-muted); margin: 0 0 20px; }

.vouchers {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 16px;
}
.voucher {
  background: #fff;
  border-radius: 24px;
  padding: 24px;
  display: grid;
  grid-template-columns: 1fr 140px;
  gap: 20px;
  align-items: center;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 2px solid transparent;
}
.voucher:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
}
.voucher.is-seckill {
  background: linear-gradient(135deg, #fff 60%, var(--hm-lime) 100%);
  border-color: var(--hm-lime);
}
.v-tag-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
.v-tag {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}
.v-tag.red { background: var(--hm-black); color: var(--hm-lime); }
.v-tag.gray { background: #eee; color: #666; }
.v-stock-tag {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: #fff5f5;
  color: #e34646;
  border: 1px solid #ffd6d6;
}
.v-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--hm-black);
}
.v-sub {
  color: var(--hm-muted);
  font-size: 13px;
  margin: 0 0 10px;
}
.v-rules, .v-time {
  font-size: 12px;
  color: #666;
  margin: 4px 0;
  line-height: 1.5;
}
.v-right {
  text-align: center;
  border-left: 2px dashed #eee;
  padding-left: 20px;
}
.v-price {
  display: flex;
  align-items: baseline;
  justify-content: center;
  color: var(--hm-black);
  margin-bottom: 4px;
}
.currency { font-size: 18px; font-weight: 700; }
.amount {
  font-size: 42px;
  font-weight: 800;
  font-family: 'Playfair Display', serif;
  line-height: 1;
}
.v-discount {
  font-size: 12px;
  color: var(--hm-muted);
  margin-bottom: 14px;
}
.seckill-btn {
  width: 100%;
  background: var(--hm-black);
  color: var(--hm-lime);
  border: none;
  padding: 12px 16px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s;
}
.seckill-btn:hover:not(:disabled) { background: #1f1f1f; }
.seckill-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.ghost-btn {
  background: #fff;
  color: var(--hm-black);
  border: 2px solid var(--hm-black);
  padding: 10px 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}
.ghost-btn:hover { background: var(--hm-black); color: var(--hm-lime); }
.ghost-btn.small { padding: 8px 14px; font-size: 12px; }
.ghost-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.lime-btn {
  background: var(--hm-lime);
  color: var(--hm-black);
  border: 2px solid var(--hm-black);
  padding: 12px 24px;
  border-radius: 999px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.15s;
}
.lime-btn:hover { transform: translateY(-1px); }

code {
  background: var(--hm-lime);
  padding: 1px 6px;
  border-radius: 4px;
  font-family: Consolas, monospace;
  color: var(--hm-black);
  font-weight: 600;
}

@media (max-width: 760px) {
  .hero-grid { grid-template-columns: 1fr; }
  .hero-name { font-size: 36px; }
}
</style>

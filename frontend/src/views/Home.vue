<template>
  <div class="home">
    <AppHeader />
    <div class="content">
      <!-- 商铺分类卡片 -->
      <section class="section">
        <div class="section-head">
          <div>
            <span class="hero-badge">🔥 Live Seckill Now</span>
            <h2 class="serif">Pick a category, grab a deal</h2>
          </div>
          <span class="hint">Click a card to view details / seckill →</span>
        </div>

        <div class="white-card">
          <el-tabs v-model="activeType" @tab-change="loadShops" class="lime-tabs">
            <el-tab-pane
              v-for="type in shopTypes"
              :key="type.id"
              :label="type.name"
              :name="String(type.id)"
            />
          </el-tabs>

          <div v-loading="loading" class="shops-grid">
            <div
              v-for="shop in shops"
              :key="shop.id"
              class="shop-card"
              @click="goDetail(shop.id)"
            >
              <div class="shop-card-inner">
                <div class="shop-top">
                  <span class="shop-name">{{ shop.name }}</span>
                  <span class="shop-arrow">→</span>
                </div>
                <div class="shop-rate">
                  <span class="rate-pill">⭐ {{ (shop.score / 10).toFixed(1) }}</span>
                  <span class="price">¥{{ shop.avgPrice }} / person</span>
                </div>
                <div class="shop-addr">📍 {{ shop.area }} · {{ shop.address }}</div>
                <div class="shop-bottom">
                  <span>🛒 Sold {{ shop.sold }}</span>
                  <span>💬 {{ shop.comments }} reviews</span>
                </div>
              </div>
            </div>

            <el-empty
              v-if="!loading && !shops.length"
              description="No shops in this category"
              style="grid-column: 1 / -1"
            />
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { getShopTypes, getShopsByType } from '../api'

const router = useRouter()
const shopTypes = ref([])
const activeType = ref('')
const shops = ref([])
const loading = ref(false)

onMounted(async () => {
  try {
    const res = await getShopTypes()
    shopTypes.value = res.data || []
    if (shopTypes.value.length) {
      activeType.value = String(shopTypes.value[0].id)
      await loadShops()
    }
  } catch (e) {}
})

async function loadShops() {
  if (!activeType.value) return
  loading.value = true
  try {
    const res = await getShopsByType(Number(activeType.value))
    shops.value = res.data || []
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/shop/${id}`)
}
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: var(--hm-lime);
  padding-bottom: 60px;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* Section */
.section {
  margin-top: 36px;
}
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 8px 20px;
  gap: 16px;
}
.hero-badge {
  display: inline-block;
  background: #fff;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 10px;
}
.section-head h2 {
  font-size: 44px;
  margin: 0;
  font-weight: 700;
  line-height: 1.1;
  color: var(--hm-black);
}
.hint {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.55);
  font-weight: 500;
  white-space: nowrap;
  padding-bottom: 6px;
}

/* 白卡 */
.white-card {
  background: #fff;
  border-radius: 28px;
  padding: 32px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}

/* Tabs 自定义 */
:deep(.lime-tabs .el-tabs__nav-wrap::after) { display: none; }
:deep(.lime-tabs .el-tabs__active-bar) { background-color: var(--hm-black); height: 3px; border-radius: 2px; }
:deep(.lime-tabs .el-tabs__item) { font-weight: 600; color: #999; font-size: 15px; }
:deep(.lime-tabs .el-tabs__item.is-active) { color: var(--hm-black); }
:deep(.lime-tabs .el-tabs__item:hover) { color: var(--hm-black); }

/* 商铺卡片 */
.shops-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  margin-top: 20px;
  min-height: 200px;
}
.shop-card {
  background: var(--hm-lime);
  border-radius: 20px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
  overflow: hidden;
}
.shop-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, var(--hm-lime) 0%, var(--hm-lime-2) 100%);
  opacity: 0;
  transition: opacity 0.2s;
}
.shop-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.12);
}
.shop-card:hover::before { opacity: 1; }
.shop-card-inner {
  position: relative;
  z-index: 1;
  padding: 20px;
}
.shop-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.shop-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--hm-black);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}
.shop-arrow {
  font-size: 18px;
  color: var(--hm-black);
  font-weight: 700;
  transition: transform 0.2s;
}
.shop-card:hover .shop-arrow { transform: translateX(4px); }
.shop-rate {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.rate-pill {
  background: var(--hm-black);
  color: var(--hm-lime);
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}
.price {
  font-weight: 700;
  color: var(--hm-black);
  font-size: 14px;
}
.shop-addr {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.7);
  margin-bottom: 12px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.shop-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.7);
  padding-top: 10px;
  border-top: 1px dashed rgba(0, 0, 0, 0.15);
  font-weight: 500;
}

@media (max-width: 700px) {
  .section-head { flex-direction: column; align-items: flex-start; }
  .section-head h2 { font-size: 32px; }
}
</style>

<template>
  <div class="blog-list">
    <AppHeader />

    <div class="content" v-loading="loading">
      <!-- 顶部介绍条 -->
      <section class="intro">
        <div>
          <span class="hero-badge">🏆 点赞排行榜</span>
          <h2 class="serif">达人探店</h2>
          <p class="subtitle">
            按点赞数实时排序，数据存在 <code>Redis ZSet</code> 里
          </p>
        </div>
        <div class="stats-card" v-if="blogs.length">
          <div class="stat">
            <div class="stat-num serif">{{ blogs.length }}</div>
            <div class="stat-label">篇笔记</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat">
            <div class="stat-num serif">{{ totalLikes }}</div>
            <div class="stat-label">总点赞</div>
          </div>
        </div>
      </section>

      <!-- Top 3 颁奖台 -->
      <section v-if="podium.length" class="podium">
        <div
          v-for="(blog, idx) in podium"
          :key="blog.id"
          class="podium-card"
          :class="`rank-${idx + 1}`"
          @click="$router.push(`/blog/${blog.id}`)"
        >
          <div class="medal">
            {{ idx === 0 ? '🥇' : idx === 1 ? '🥈' : '🥉' }}
          </div>
          <div class="rank-num serif">#{{ idx + 1 }}</div>
          <div class="podium-author">
            <div class="avatar-sm">
              {{ blog.name?.[0]?.toUpperCase() || 'U' }}
            </div>
            <span>{{ blog.name }}</span>
          </div>
          <h3 class="podium-title">{{ blog.title }}</h3>
          <div class="podium-bottom">
            <span class="likes-pill">❤️ {{ blog.liked }} 赞</span>
            <span class="arrow">→</span>
          </div>
        </div>
      </section>

      <!-- 其余笔记 -->
      <section v-if="rest.length" class="rest-section">
        <div class="section-head">
          <h3 class="serif">更多笔记</h3>
          <span class="hint">{{ rest.length }} 篇</span>
        </div>
        <div class="grid">
          <article
            v-for="blog in rest"
            :key="blog.id"
            class="card"
            @click="$router.push(`/blog/${blog.id}`)"
          >
            <div class="card-img">
              <img
                v-if="firstImg(blog)"
                :src="firstImg(blog)"
                @error="(e) => (e.target.style.display = 'none')"
                alt=""
              />
              <div v-else class="card-img-fallback">📝</div>
            </div>
            <div class="card-body">
              <div class="card-author">
                <div class="avatar-xs">
                  {{ blog.name?.[0]?.toUpperCase() || 'U' }}
                </div>
                <span>{{ blog.name }}</span>
              </div>
              <h4 class="card-title">{{ blog.title }}</h4>
              <div class="card-bottom">
                <span class="like-count">❤️ {{ blog.liked }}</span>
                <span class="comment-count">💬 {{ blog.comments || 0 }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <el-empty
        v-if="!loading && !blogs.length"
        description="还没有任何探店笔记"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import { getHotBlogs } from '../api'

const blogs = ref([])
const loading = ref(false)

const podium = computed(() => blogs.value.slice(0, 3))
const rest = computed(() => blogs.value.slice(3))
const totalLikes = computed(() =>
  blogs.value.reduce((sum, b) => sum + (b.liked || 0), 0)
)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getHotBlogs()
    blogs.value = res.data || []
  } finally {
    loading.value = false
  }
})

function firstImg(blog) {
  const img = blog.images?.split(',').filter(Boolean)[0]
  if (!img) return null
  // 后端图片走 nginx，本地无 nginx 时返回 null
  return img.startsWith('http') ? img : null
}
</script>

<style scoped>
.blog-list {
  min-height: 100vh;
  background: var(--hm-lime);
  padding-bottom: 60px;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* Intro */
.intro {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 36px 8px 20px;
  gap: 20px;
  flex-wrap: wrap;
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
.intro h2 {
  font-size: 44px;
  margin: 0;
  font-weight: 700;
  line-height: 1.1;
}
.subtitle {
  margin: 8px 0 0;
  color: rgba(0, 0, 0, 0.6);
  font-size: 14px;
}
.stats-card {
  background: #fff;
  border-radius: 20px;
  padding: 16px 28px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}
.stat-label {
  font-size: 12px;
  color: var(--hm-muted);
  margin-top: 4px;
  font-weight: 500;
}
.stat-divider {
  width: 1px;
  height: 32px;
  background: #eee;
}

/* Podium */
.podium {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 8px;
}
.podium-card {
  background: #fff;
  border-radius: 24px;
  padding: 28px 24px 24px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
  overflow: hidden;
}
.podium-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.12);
}
.podium-card.rank-1 {
  background: linear-gradient(135deg, #fffbe6 0%, #fff5b8 100%);
  border: 2px solid #f0c419;
}
.podium-card.rank-2 {
  background: linear-gradient(135deg, #f8f9fb 0%, #e2e6ec 100%);
  border: 2px solid #b8c1cd;
}
.podium-card.rank-3 {
  background: linear-gradient(135deg, #fdf1e6 0%, #f9d9b8 100%);
  border: 2px solid #d39a6a;
}
.medal {
  position: absolute;
  top: 18px;
  right: 18px;
  font-size: 32px;
}
.rank-num {
  font-size: 56px;
  font-weight: 800;
  color: var(--hm-black);
  opacity: 0.15;
  line-height: 1;
  margin-bottom: 8px;
}
.podium-author {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #444;
  margin-bottom: 12px;
}
.avatar-sm {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--hm-black);
  color: var(--hm-lime);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
}
.podium-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--hm-black);
  margin: 0 0 20px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 76px;
}
.podium-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.likes-pill {
  background: var(--hm-black);
  color: var(--hm-lime);
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}
.arrow {
  font-size: 22px;
  font-weight: 700;
  transition: transform 0.2s;
}
.podium-card:hover .arrow {
  transform: translateX(4px);
}

/* Rest */
.rest-section {
  margin-top: 40px;
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 8px 16px;
}
.section-head h3 {
  font-size: 28px;
  margin: 0;
  font-weight: 700;
}
.hint {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.55);
  font-weight: 500;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.card {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
}
.card-img {
  aspect-ratio: 4/3;
  background: var(--hm-lime);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.card-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-img-fallback {
  font-size: 48px;
  color: rgba(0, 0, 0, 0.3);
}
.card-body {
  padding: 16px;
}
.card-author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #666;
  margin-bottom: 8px;
}
.avatar-xs {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--hm-black);
  color: var(--hm-lime);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 11px;
}
.card-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--hm-black);
  margin: 0 0 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
}
.card-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #666;
}
.like-count {
  color: #e34646;
}
code {
  background: #fff;
  padding: 1px 8px;
  border-radius: 6px;
  font-family: Consolas, monospace;
  color: var(--hm-black);
  font-weight: 600;
  font-size: 12px;
}

@media (max-width: 760px) {
  .podium { grid-template-columns: 1fr; }
  .intro h2 { font-size: 32px; }
}
</style>

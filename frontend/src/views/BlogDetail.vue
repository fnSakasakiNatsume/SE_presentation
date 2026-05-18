<template>
  <div class="blog-detail">
    <AppHeader />
    <div class="content" v-loading="loading">
      <button class="back-btn" @click="$router.push('/blog')">← Back to Notes</button>

      <article v-if="blog" class="article-card">
        <!-- 作者 -->
        <header class="author-row">
          <div class="avatar">
            {{ blog.name?.[0]?.toUpperCase() || 'U' }}
          </div>
          <div class="author-meta">
            <div class="author-name">{{ blog.name }}</div>
            <div class="post-date">📅 {{ formatTime(blog.createTime) }}</div>
          </div>
          <span class="hot-badge">🔥 {{ blog.liked }} likes</span>
        </header>

        <!-- 标题 -->
        <h1 class="serif title">{{ blog.title }}</h1>

        <!-- 内容 -->
        <div class="article-content" v-html="renderedContent" />

        <!-- 操作区 -->
        <footer class="action-bar">
          <button
            class="like-btn"
            :class="{ liked: blog.isLike }"
            :disabled="liking"
            @click="onLike"
          >
            <span class="heart">{{ blog.isLike ? '❤️' : '🤍' }}</span>
            <span>{{ blog.isLike ? 'Liked' : 'Like' }}</span>
            <span class="count">{{ blog.liked || 0 }}</span>
          </button>
          <div class="action-hint">
            One like per user, click again to undo · Powered by Redis ZSet
          </div>
        </footer>
      </article>

      <!-- Top 5 点赞用户 -->
      <section v-if="blog" class="likers-card">
        <div class="likers-head">
          <h3 class="serif">Top 5 First Likers</h3>
          <span class="hint">Sorted by like time · Redis ZSet</span>
        </div>
        <div v-if="likers.length" class="likers-row">
          <div
            v-for="(u, idx) in likers"
            :key="u.id"
            class="liker-chip"
            :class="{ champion: idx === 0 }"
          >
            <div class="liker-rank">#{{ idx + 1 }}</div>
            <div class="liker-avatar">
              {{ u.nickName?.[0]?.toUpperCase() || 'U' }}
            </div>
            <div class="liker-name">{{ u.nickName }}</div>
          </div>
        </div>
        <el-empty
          v-else
          description="No likes yet — be the first ❤️"
          :image-size="80"
        />
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppHeader from '../components/AppHeader.vue'
import { getBlogById, likeBlog, getBlogLikes } from '../api'

const route = useRoute()
const blogId = route.params.id
const blog = ref(null)
const likers = ref([])
const loading = ref(false)
const liking = ref(false)

const renderedContent = computed(() => {
  if (!blog.value?.content) return ''
  // 后端原内容是带 <br/> 的，直接用，做基本 XSS 兜底
  return blog.value.content.replace(/<script/gi, '&lt;script')
})

onMounted(async () => {
  await load()
})

async function load() {
  loading.value = true
  try {
    const [bRes, lRes] = await Promise.all([
      getBlogById(blogId),
      getBlogLikes(blogId)
    ])
    blog.value = bRes.data
    likers.value = lRes.data || []
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function onLike() {
  liking.value = true
  try {
    await likeBlog(blogId)
    const wasLiked = blog.value.isLike
    blog.value.isLike = !wasLiked
    blog.value.liked += wasLiked ? -1 : 1
    ElMessage.success(wasLiked ? 'Unliked' : 'Liked ❤️')
    // 刷新点赞用户列表
    const lRes = await getBlogLikes(blogId)
    likers.value = lRes.data || []
  } catch (e) {
  } finally {
    liking.value = false
  }
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.blog-detail {
  min-height: 100vh;
  background: var(--hm-lime);
  padding-bottom: 60px;
}
.content {
  max-width: 820px;
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
.back-btn:hover {
  background: #fff;
}

.article-card {
  background: #fff;
  border-radius: 28px;
  padding: 40px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}

/* 作者 */
.author-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--hm-black);
  color: var(--hm-lime);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 18px;
}
.author-meta {
  flex: 1;
}
.author-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--hm-black);
}
.post-date {
  font-size: 12px;
  color: var(--hm-muted);
  margin-top: 2px;
}
.hot-badge {
  background: var(--hm-lime);
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  color: var(--hm-black);
}

/* 标题 */
.title {
  font-size: 36px;
  line-height: 1.25;
  font-weight: 700;
  margin: 0 0 24px;
  color: var(--hm-black);
}

/* 内容 */
.article-content {
  font-size: 15px;
  line-height: 1.85;
  color: #2a2a2a;
  margin-bottom: 32px;
}
.article-content :deep(br) {
  display: block;
  margin: 4px 0;
}

/* 点赞 */
.action-bar {
  border-top: 1px solid #eee;
  padding-top: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border: 2px solid var(--hm-black);
  padding: 12px 24px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 700;
  color: var(--hm-black);
  cursor: pointer;
  transition: all 0.15s;
}
.like-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}
.like-btn.liked {
  background: var(--hm-black);
  color: var(--hm-lime);
}
.like-btn .heart {
  font-size: 18px;
  transition: transform 0.2s;
}
.like-btn:hover .heart {
  transform: scale(1.2);
}
.like-btn .count {
  background: var(--hm-lime);
  color: var(--hm-black);
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  min-width: 28px;
  text-align: center;
}
.like-btn.liked .count {
  background: var(--hm-lime);
  color: var(--hm-black);
}
.like-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.action-hint {
  font-size: 12px;
  color: var(--hm-muted);
}

/* Top 5 点赞用户 */
.likers-card {
  margin-top: 20px;
  background: #fff;
  border-radius: 24px;
  padding: 28px 32px;
}
.likers-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18px;
  flex-wrap: wrap;
  gap: 8px;
}
.likers-head h3 {
  font-size: 20px;
  margin: 0;
  font-weight: 700;
}
.hint {
  font-size: 12px;
  color: var(--hm-muted);
}
.likers-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.liker-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f6f7f4;
  padding: 8px 14px 8px 8px;
  border-radius: 999px;
}
.liker-chip.champion {
  background: var(--hm-lime);
}
.liker-rank {
  font-size: 12px;
  font-weight: 800;
  color: var(--hm-black);
  background: #fff;
  padding: 2px 8px;
  border-radius: 999px;
}
.liker-avatar {
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
.liker-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--hm-black);
}

@media (max-width: 600px) {
  .article-card { padding: 24px; }
  .title { font-size: 28px; }
}
</style>

import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers['authorization'] = userStore.token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.success === false) {
      ElMessage.error(data.errorMsg || '请求失败')
      return Promise.reject(new Error(data.errorMsg || '请求失败'))
    }
    return data
  },
  (error) => {
    if (error.response?.status === 401) {
      ElMessage.warning('登录已过期，请重新登录')
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request

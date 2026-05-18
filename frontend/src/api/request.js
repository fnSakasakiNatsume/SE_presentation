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
      ElMessage.error(data.errorMsg || 'Request failed')
      return Promise.reject(new Error(data.errorMsg || 'Request failed'))
    }
    return data
  },
  (error) => {
    if (error.response?.status === 401) {
      ElMessage.warning('Session expired, please sign in again')
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
    } else {
      ElMessage.error(error.message || 'Network error')
    }
    return Promise.reject(error)
  }
)

export default request

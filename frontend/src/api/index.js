import request from './request'

// 用户
export const sendCode = (phone) =>
  request.post(`/user/code?phone=${encodeURIComponent(phone)}`)
export const login = (data) => request.post('/user/login', data)
export const getMe = () => request.get('/user/me')

// 商铺
export const getShopTypes = () => request.get('/shop-type/list')
export const getShopsByType = (typeId, current = 1) =>
  request.get('/shop/of/type', { params: { typeId, current } })
export const getShopById = (id) => request.get(`/shop/${id}`)

// 优惠券
export const getVoucherList = (shopId) => request.get(`/voucher/list/${shopId}`)
export const seckillVoucher = (id) => request.post(`/voucher-order/seckill/${id}`)

// 演示工具：手动创建一张秒杀券（数据库里默认没有）
export const createSeckillVoucher = (data) => request.post('/voucher/seckill', data)

// 达人探店 / 点赞 / 排行榜
export const getHotBlogs = (current = 1) =>
  request.get('/blog/hot', { params: { current } })
export const getBlogById = (id) => request.get(`/blog/${id}`)
export const likeBlog = (id) => request.put(`/blog/like/${id}`)
export const getBlogLikes = (id) => request.get(`/blog/likes/${id}`)
export const getUserById = (id) => request.get(`/user/${id}`)

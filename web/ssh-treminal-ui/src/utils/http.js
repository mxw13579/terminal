import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const http = axios.create({
  baseURL: 'http://localhost:8080',  // 后端服务地址
  timeout: 30000,  // 30秒超时
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
http.interceptors.request.use(
  config => {
    console.log('发送请求:', config.method?.toUpperCase(), config.url)
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
http.interceptors.response.use(
  response => {
    console.log('收到响应:', response.status, response.config.url)
    return response
  },
  error => {
    console.error('响应错误:', error)
    
    let message = '网络错误'
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权访问'
          break
        case 403:
          message = '禁止访问'
          break
        case 404:
          message = '接口不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        default:
          message = `请求失败 (${error.response.status})`
      }
    } else if (error.request) {
      message = '网络连接失败'
    }
    
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
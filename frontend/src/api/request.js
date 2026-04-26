import axios from 'axios'
import router from '../router'

const toCamel = (str) => str.replace(/_([a-z])/g, (_, c) => c.toUpperCase())

const convertKeys = (obj) => {
    if (Array.isArray(obj)) return obj.map(convertKeys)
    if (obj !== null && typeof obj === 'object') {
        return Object.fromEntries(
            Object.entries(obj).map(([k, v]) => [toCamel(k), convertKeys(v)])
        )
    }
    return obj
}

const request = axios.create({
    baseURL: 'http://localhost:8080',
    timeout: 10000
})

request.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
})

request.interceptors.response.use(
    response => {
        const data = response.data
        if (data && data.data) {
            data.data = convertKeys(data.data)
        }
        return data
    },
    error => {
        if (error.response?.status === 401) {
            localStorage.removeItem('token')
            router.push('/login')
        }
        return Promise.reject(error)
    }
)

export default request
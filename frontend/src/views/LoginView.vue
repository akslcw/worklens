<template>
  <div style="display:flex; justify-content:center; align-items:center; height:100vh; background:#f0f2f5;">
    <el-card style="width:400px;">
      <div style="text-align:center; margin-bottom:24px;">
        <div style="font-size:24px; font-weight:bold; color:#304156;">WorkLens</div>
        <div style="color:#999; margin-top:8px;">企业应用时间管理平台</div>
      </div>
      <el-form :model="form" label-width="0">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" prefix-icon="User"/>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" placeholder="密码" size="large"
                    type="password" prefix-icon="Lock" @keyup.enter="handleLogin"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width:100%;"
                     :loading="loading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const router = useRouter()
const loading = ref(false)
const form = ref({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/api/auth/login', form.value)
    if (res.code === 200) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('username', res.data.username)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
  }
}
</script>
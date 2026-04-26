<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
const username = ref(localStorage.getItem('username') || '')
const isLogin = computed(() => route.name === 'login')

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}
</script>

<template>
  <router-view v-if="isLogin" />
  <el-container v-else style="height: 100vh;">
    <el-aside width="200px" style="background:#304156;">
      <div style="color:white; font-size:18px; font-weight:bold; padding:20px 16px;">
        WorkLens
      </div>
      <el-menu
          :default-active="$route.path"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">数据总览</el-menu-item>
        <el-menu-item index="/employees">员工管理</el-menu-item>
        <el-menu-item index="/reports">效率报告</el-menu-item>
        <el-menu-item index="/categories">应用分类管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:white; border-bottom:1px solid #eee; display:flex; justify-content:space-between; align-items:center;">
        <span style="font-size:16px;">WorkLens 企业应用时间管理平台</span>
        <div>
          <span style="margin-right:12px; color:#666;">{{ username }}</span>
          <el-button size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main style="background:#f0f2f5;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
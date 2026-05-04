<template>
  <div>
    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
      <span style="font-size:16px; font-weight:bold;">实时监控</span>
      <div>
        <span style="color:#999; margin-right:12px;">每10秒自动刷新</span>
        <el-button @click="load">立即刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="8" v-for="device in devices" :key="device.id" style="margin-bottom:16px;">
        <el-card :class="isOnline(device) ? 'online-card' : 'offline-card'">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
            <span style="font-weight:bold; font-size:15px;">{{ device.deviceName }}</span>
            <el-tag :type="isOnline(device) ? 'success' : 'info'" size="small">
              {{ isOnline(device) ? '在线' : '离线' }}
            </el-tag>
          </div>
          <div style="color:#666; font-size:13px; margin-bottom:6px;">
            <span>当前应用：</span>
            <span style="color:#333; font-weight:500;">
              {{ isOnline(device) ? (device.currentApp || '未知') : '—' }}
            </span>
          </div>
          <div style="color:#666; font-size:13px; margin-bottom:6px;">
            <span>窗口标题：</span>
            <span style="color:#333;">
              {{ isOnline(device) ? (device.currentWindow || '—') : '—' }}
            </span>
          </div>
          <div style="color:#999; font-size:12px; margin-top:8px;">
            最后活跃：{{ formatTime(device.lastOnline) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="devices.length === 0" description="暂无设备数据"/>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getDevices } from '../api/device'

const devices = ref([])
let timer = null

const load = async () => {
  const res = await getDevices()
  devices.value = res.data || []
}

const isOnline = (device) => {
  if (!device.lastOnline) return false
  const last = new Date(device.lastOnline + 'Z')  // 加Z表示UTC
  const now = new Date()
  return (now - last) < 60 * 1000
}

const formatTime = (time) => {
  if (!time) return '从未'
  const d = new Date(time + 'Z')  // 加Z表示UTC，浏览器自动转本地时间显示
  return d.toLocaleString('zh-CN')
}
onMounted(() => {
  load()
  timer = setInterval(load, 10000) // 每10秒刷新
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped>
.online-card {
  border-left: 4px solid #67C23A;
}
.offline-card {
  border-left: 4px solid #DCDFE6;
  opacity: 0.8;
}
</style>
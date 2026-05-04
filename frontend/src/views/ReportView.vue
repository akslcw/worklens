<template>
  <el-card>
    <template #header>
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <span>效率报告</span>
        <div style="display:flex; gap:8px;">
          <el-input v-model="employeeId" placeholder="员工ID" style="width:120px;"/>
          <el-date-picker v-model="date" type="date" placeholder="选择日期"
                          value-format="YYYY-MM-DD" style="width:160px;"/>
          <el-button type="primary" @click="handleTrigger">触发分析</el-button>
          <el-button @click="load">刷新</el-button>
        </div>
      </div>
    </template>
    <el-table :data="reports" style="width:100%">
      <el-table-column label="姓名" width="100">
        <template #default="{ row }">
          {{ row.employeeName }}
        </template>
      </el-table-column>
      <el-table-column label="工号" width="120">
        <template #default="{ row }">
          {{ row.employeeNo }}
        </template>
      </el-table-column>
      el-table-column prop="reportDate" label="日期" width="140"/>
      <el-table-column prop="efficiencyScore" label="效率评分" width="200">
        <template #default="{ row }">
          <el-progress :percentage="row.efficiencyScore"
                       :color="row.efficiencyScore >= 80 ? '#67C23A' : row.efficiencyScore >= 60 ? '#E6A23C' : '#F56C6C'" />
        </template>
      </el-table-column>
      <el-table-column prop="workSeconds" label="工作时长" width="120">
        <template #default="{ row }">
          {{ Math.floor(row.workSeconds / 3600) }}h {{ Math.floor((row.workSeconds % 3600) / 60) }}m
        </template>
      </el-table-column>
      <el-table-column prop="idleSeconds" label="摸鱼时长" width="120">
        <template #default="{ row }">
          {{ Math.floor(row.idleSeconds / 3600) }}h {{ Math.floor((row.idleSeconds % 3600) / 60) }}m
        </template>
      </el-table-column>
      <el-table-column prop="aiComment" label="AI点评"/>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getReports, triggerAnalysis } from '../api/report'
import { ElMessage } from 'element-plus'

const reports = ref([])
const employeeId = ref('')
const date = ref('')

const load = async () => {
  try {
    const res = await getReports()
    reports.value = res.data || []
  } catch (e) {
    ElMessage.error('报告数据加载失败')
  }
}

const handleTrigger = async () => {
  if (!employeeId.value || !date.value) {
    ElMessage.warning('请填写员工ID和日期')
    return
  }
  try {
    await triggerAnalysis(employeeId.value, date.value)
    ElMessage.success('分析触发成功，请稍后刷新')
  } catch (e) {
    ElMessage.error('触发失败')
  }
}

onMounted(load)
</script>
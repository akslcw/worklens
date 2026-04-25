<template>
  <div>
    <el-row :gutter="16" style="margin-bottom:16px;">
      <el-col :span="8">
        <el-card>
          <div style="font-size:14px; color:#666;">员工总数</div>
          <div style="font-size:32px; font-weight:bold; color:#409EFF;">{{ employeeCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div style="font-size:14px; color:#666;">已生成报告</div>
          <div style="font-size:32px; font-weight:bold; color:#67C23A;">{{ reportCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div style="font-size:14px; color:#666;">平均效率评分</div>
          <div style="font-size:32px; font-weight:bold; color:#E6A23C;">{{ avgScore }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card>
      <el-table :data="reports" style="width:100%">
        <el-table-column prop="employeeId" label="员工ID" width="100"/>
        <el-table-column prop="reportDate" label="日期" width="140"/>
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
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { getEmployees } from '../api/employee'
import { getReports } from '../api/report'

const employees = ref([])
const reports = ref([])

const employeeCount = computed(() => employees.value.length)
const reportCount = computed(() => reports.value.length)
const avgScore = computed(() => {
  if (!reports.value.length) return 0
  const sum = reports.value.reduce((acc, r) => acc + r.efficiencyScore, 0)
  return Math.round(sum / reports.value.length)
})

onMounted(async () => {
  const empRes = await getEmployees()
  employees.value = empRes.data || []
  const repRes = await getReports()
  reports.value = repRes.data || []
})
</script>
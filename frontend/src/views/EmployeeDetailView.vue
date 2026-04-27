<template>
  <div>
    <el-page-header @back="$router.back()" style="margin-bottom:16px;">
      <template #content>
        <span>员工应用使用详情</span>
      </template>
    </el-page-header>

    <el-card style="margin-bottom:16px;">
      <el-form inline>
        <el-form-item label="开始日期">
          <el-date-picker v-model="startDate" type="date"
                          value-format="YYYY-MM-DD" style="width:160px;"
                          :disabled-date="(d) => d > new Date()"/>
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="endDate" type="date"
                          value-format="YYYY-MM-DD" style="width:160px;"
                          :disabled-date="(d) => d > new Date()"/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="应用使用时长 Top 10">
          <div ref="barChart" style="height:400px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="使用明细">
          <el-table :data="tableData" height="400">
            <el-table-column prop="appName" label="应用名称" width="160"/>
            <el-table-column prop="recordDate" label="日期" width="120"/>
            <el-table-column label="使用时长">
              <template #default="{ row }">
                {{ Math.floor(row.durationSeconds / 3600) }}h
                {{ Math.floor((row.durationSeconds % 3600) / 60) }}m
              </template>
            </el-table-column>
            <el-table-column prop="windowTitle" label="窗口标题"/>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import {
  TooltipComponent,
  GridComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, TooltipComponent, GridComponent, CanvasRenderer])
import { getRecordsByRange } from '../api/record'

const route = useRoute()
const employeeId = route.params.id

const startDate = ref(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)
    .toISOString().slice(0, 10))
const endDate = ref(new Date().toISOString().slice(0, 10))

const tableData = ref([])
const barChart = ref(null)
let chartInstance = null

const load = async () => {
  const res = await getRecordsByRange(employeeId, startDate.value, endDate.value)
  tableData.value = res.data || []

  // 按应用名聚合时长
  const appMap = {}
  tableData.value.forEach(r => {
    appMap[r.appName] = (appMap[r.appName] || 0) + r.durationSeconds
  })

  // 取 Top 10 排序
  const sorted = Object.entries(appMap)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)

  const appNames = sorted.map(([name]) => name)
  const durations = sorted.map(([, sec]) => Math.round(sec / 60))

  await nextTick()
  if (!chartInstance) {
    chartInstance = echarts.init(barChart.value)
  }
  chartInstance.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}: {c} 分钟' },
    grid: { left: '20%' },
    xAxis: { type: 'value', name: '分钟' },
    yAxis: {
      type: 'category',
      data: appNames,
      axisLabel: { fontSize: 12 }
    },
    series: [{
      type: 'bar',
      data: durations,
      itemStyle: { color: '#409EFF' },
      label: { show: true, position: 'right', formatter: '{c} min' }
    }]
  })
}

onMounted(load)
</script>
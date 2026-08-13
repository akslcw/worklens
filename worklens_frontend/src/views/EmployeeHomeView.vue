<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getEmployeeReportHistory } from '../api/employeeReports'
import type { ReportHistoryItem } from '../api/teamReports'
import { getUsageView, type UsageAppCard, type UsageView } from '../api/usageRecords'
import { clearSession, readStoredSession } from '../auth/session'
import EmployeeWorkspaceNav from '../components/EmployeeWorkspaceNav.vue'
import { hongKongDateString, hongKongDateTimeString, hongKongTimeString } from '../utils/hongKongTime'

const router = useRouter()
const session = readStoredSession()

const usageView = ref<UsageView | null>(null)
const reportHistory = ref<ReportHistoryItem[]>([])
const loading = ref(false)
const usageLoading = ref(false)
const errorMessage = ref('')
const usageDate = ref(todayDateString())
const usagePage = ref(1)
const usagePageSize = 10

const usageCards = computed(() => usageView.value?.items ?? [])
const usageReport = computed(() => usageView.value?.report ?? null)
const totalUsageApps = computed(() => usageView.value?.totalApps ?? usageCards.value.length)
const totalUsagePages = computed(() => Math.max(1, Math.ceil(totalUsageApps.value / usagePageSize)))
const canGoToPreviousUsagePage = computed(() => usagePage.value > 1)
const canGoToNextUsagePage = computed(() => usagePage.value < totalUsagePages.value)
const isLiveUsageMode = computed(() => usageView.value?.mode === 'LIVE_USAGE')
const isReportMode = computed(() => usageView.value?.mode === 'REPORT')

onMounted(async () => {
  await loadEmployeeHome()
})

async function loadEmployeeHome() {
  if (!session?.token) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const [view, history] = await Promise.all([
      getUsageView(session.token, usageDate.value, usagePage.value, usagePageSize),
      getEmployeeReportHistory(session.token),
    ])

    usageView.value = view
    reportHistory.value = history
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '个人效率面板加载失败。')
  } finally {
    loading.value = false
  }
}

async function loadUsageView() {
  if (!session?.token) {
    return
  }

  usageLoading.value = true
  errorMessage.value = ''

  try {
    usageView.value = await getUsageView(session.token, usageDate.value, usagePage.value, usagePageSize)
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '使用明细加载失败。')
  } finally {
    usageLoading.value = false
  }
}

async function handleUsageDateChange() {
  usagePage.value = 1
  await loadUsageView()
}

async function handlePreviousUsagePage() {
  if (!canGoToPreviousUsagePage.value) {
    return
  }
  usagePage.value -= 1
  await loadUsageView()
}

async function handleNextUsagePage() {
  if (!canGoToNextUsagePage.value) {
    return
  }
  usagePage.value += 1
  await loadUsageView()
}

async function handleLogout() {
  clearSession()
  await router.replace('/login')
}

function todayDateString() {
  return hongKongDateString(new Date())
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '本次即时生成'
  }

  return hongKongDateTimeString(value)
}

function formatDateOnly(value: string) {
  const [year, month, day] = value.split('-')
  return `${year}/${month}/${day}`
}

function formatTimeOnly(value: string) {
  return hongKongTimeString(value)
}

function formatDurationSeconds(seconds: number) {
  return `${Math.round(seconds / 60)} min`
}

function formatPercent(ratio: number) {
  const percent = Math.round(ratio * 1000) / 10
  return `${Number.isInteger(percent) ? percent.toFixed(0) : percent.toFixed(1)}%`
}

function reportPeriodLabel(report: ReportHistoryItem) {
  if (report.periodStartDate && report.periodEndDate) {
    const typeLabel = periodTypeLabel(report.periodType)
    if (report.periodType === 'DAILY') {
      return `${formatDateOnly(report.periodStartDate)} · ${typeLabel}`
    }
    return `${formatDateOnly(report.periodStartDate)} - ${formatDateOnly(report.periodEndDate)} · ${typeLabel}`
  }
  if (!report.periodStartedAt || !report.periodEndedAt) {
    return '最近一周'
  }

  return `${formatDateTime(report.periodStartedAt)} - ${formatDateTime(report.periodEndedAt)}`
}

function periodTypeLabel(periodType: string | null) {
  if (periodType === 'DAILY') {
    return '日报'
  }
  if (periodType === 'WEEKLY') {
    return '周报'
  }
  if (periodType === 'MONTHLY') {
    return '月报'
  }
  return '报告'
}

function usageReportPeriodLabel() {
  if (!usageReport.value) {
    return ''
  }
  return `${formatDateOnly(usageReport.value.periodStartDate)} - ${formatDateOnly(usageReport.value.periodEndDate)}`
}

function appCardKey(card: UsageAppCard, index: number) {
  return `${card.appName}-${index}`
}

function toErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
</script>

<template>
  <main class="employee-layout">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Employee Workspace</p>
        <h1>个人效率面板</h1>
        <p class="hero-copy">这里只展示当前登录员工自己的使用明细和自己的报告历史，不接受前端传参切换到别人的数据。</p>
        <div class="hero-nav">
          <EmployeeWorkspaceNav current="dashboard" />
        </div>
      </div>
      <div class="hero-actions">
        <div class="session-pill">
          <span>Current User</span>
          <strong>{{ session?.displayName ?? session?.username ?? 'employee' }}</strong>
        </div>
        <button class="ghost-button" type="button" @click="handleLogout">退出登录</button>
      </div>
    </section>

    <p v-if="errorMessage" class="feedback feedback--error" role="alert">{{ errorMessage }}</p>
    <p v-else-if="loading" class="feedback">正在加载你的个人明细与报告历史...</p>

    <template v-else>
      <section class="content-grid">
        <article class="panel-card">
          <div class="panel-head panel-head--with-controls">
            <div>
              <p class="eyebrow">Usage View</p>
              <h2>我的使用明细</h2>
            </div>
            <label class="date-control">
              <span>日期</span>
              <input v-model="usageDate" type="date" @change="handleUsageDateChange" />
            </label>
          </div>

          <p v-if="usageLoading" class="inline-status">正在刷新使用明细...</p>

          <template v-else-if="isLiveUsageMode">
            <div v-if="usageCards.length === 0" class="empty-state">
              <strong>暂无个人使用记录</strong>
              <p>等待桌面采集客户端上报后，这里会按应用显示当天累计时长。</p>
            </div>

            <ul v-else class="usage-card-list">
              <li
                v-for="(card, index) in usageCards"
                :key="appCardKey(card, index)"
                data-test="usage-app-card"
                class="usage-app-card"
              >
                <details open>
                  <summary>
                    <span>
                      <strong>{{ card.appName }}</strong>
                      <small>{{ card.segments.length }} 个时间段</small>
                    </span>
                    <span class="duration-chip">{{ formatDurationSeconds(card.durationSeconds) }}</span>
                  </summary>
                  <ul class="segment-list">
                    <li v-for="segment in card.segments" :key="`${segment.startedAt}-${segment.endedAt}`">
                      {{ formatTimeOnly(segment.startedAt) }} - {{ formatTimeOnly(segment.endedAt) }}
                    </li>
                  </ul>
                </details>
              </li>
            </ul>

            <div v-if="totalUsageApps > usagePageSize" class="pager">
              <button
                class="secondary-button"
                type="button"
                :disabled="!canGoToPreviousUsagePage || usageLoading"
                @click="handlePreviousUsagePage"
              >
                上一页
              </button>
              <span>第 {{ usagePage }} / {{ totalUsagePages }} 页，共 {{ totalUsageApps }} 个应用</span>
              <button
                data-test="usage-next-page"
                class="secondary-button"
                type="button"
                :disabled="!canGoToNextUsagePage || usageLoading"
                @click="handleNextUsagePage"
              >
                下一页
              </button>
            </div>
          </template>

          <article v-else-if="isReportMode && usageReport" data-test="usage-report" class="usage-report">
            <div class="report-meta">
              <span>{{ usageReport.periodType }}</span>
              <span>{{ usageReportPeriodLabel() }}</span>
            </div>
            <p class="report-summary">{{ usageReport.summary }}</p>
            <table class="report-detail-table">
              <thead>
                <tr>
                  <th>应用</th>
                  <th>累计时长</th>
                  <th>占比</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="detail in usageReport.details" :key="detail.appName">
                  <td>{{ detail.appName }}</td>
                  <td>{{ formatDurationSeconds(detail.durationSeconds) }}</td>
                  <td>{{ formatPercent(detail.ratio) }}</td>
                </tr>
              </tbody>
            </table>
          </article>

          <div v-else class="empty-state">
            <strong>暂无可展示内容</strong>
            <p>当前日期没有实时使用数据，也没有覆盖该日期的历史报告。</p>
          </div>
        </article>

        <article class="panel-card">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Weekly Report</p>
              <h2>我的周报</h2>
            </div>
          </div>

          <div class="history-head">
            <p class="eyebrow">History</p>
          </div>

          <div v-if="reportHistory.length === 0" class="empty-state">
            <strong>暂无周报历史</strong>
            <p>报告会在系统定时归档任务完成后自动出现在这里。</p>
          </div>

          <ul v-else class="history-list">
            <li v-for="(report, index) in reportHistory" :key="`${report.createdAt}-${index}`" class="history-row">
              <strong>{{ report.summary }}</strong>
              <small>生成时间：{{ formatDateTime(report.createdAt) }}</small>
              <small>覆盖时间：{{ reportPeriodLabel(report) }}</small>
            </li>
          </ul>
        </article>
      </section>
    </template>
  </main>
</template>

<style scoped>
.employee-layout {
  position: relative;
  z-index: 1;
  width: min(1180px, calc(100% - 40px));
  min-height: 100vh;
  margin: 0 auto;
  padding: 28px 0 36px;
}

.hero-card,
.panel-card {
  border-radius: 12px;
  border: 1px solid rgba(29, 46, 71, 0.12);
  box-shadow: var(--wl-shadow);
}

.hero-card {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 20px;
  padding: 24px 26px;
  background: #ffffff;
}

.eyebrow {
  margin: 0 0 10px;
  color: var(--wl-copper);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 0.76rem;
}

.hero-card h1,
.panel-card h2,
.session-pill strong,
.usage-app-card strong,
.history-row strong,
.empty-state strong {
  margin: 0;
  color: #1d2e47;
}

.hero-card h1 {
  font-size: 2rem;
  line-height: 1.2;
}

.hero-copy,
.feedback,
.inline-status,
.usage-app-card small,
.report-card p,
.history-row small,
.empty-state p,
.report-summary {
  margin: 0;
  line-height: 1.7;
  color: #5d6e83;
}

.hero-copy {
  margin-top: 8px;
  max-width: 620px;
}

.hero-nav {
  margin-top: 18px;
}

.hero-actions {
  display: grid;
  gap: 12px;
  justify-items: end;
}

.session-pill {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  min-width: 220px;
  border-radius: 10px;
  background: rgba(29, 46, 71, 0.92);
  color: rgba(238, 245, 252, 0.84);
}

.session-pill strong {
  color: #edf4fc;
  font-size: 1.2rem;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 22px;
  margin-top: 22px;
}

.panel-card {
  padding: 24px;
  background: #ffffff;
}

.panel-head,
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 16px;
}

.panel-head {
  margin-bottom: 20px;
}

.panel-head--with-controls {
  align-items: center;
}

.history-head {
  margin-top: 22px;
  margin-bottom: 16px;
}

.panel-head h2 {
  font-size: 1.35rem;
}

.date-control {
  display: grid;
  gap: 6px;
  min-width: 160px;
  color: #5d6e83;
  font-size: 0.82rem;
}

.date-control input {
  min-height: 38px;
  border: 1px solid #d2dde8;
  border-radius: 8px;
  padding: 0 10px;
  color: #1d2e47;
  background: #ffffff;
}

.primary-button,
.ghost-button,
.secondary-button {
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease;
  font-size: 0.95rem;
}

.primary-button {
  padding: 14px 18px;
  background: #1d2e47;
  color: #ffffff;
}

.ghost-button {
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid rgba(29, 46, 71, 0.14);
  color: #1d2e47;
}

.secondary-button {
  padding: 10px 14px;
  background: #edf3f9;
  color: #35506b;
}

.primary-button:hover,
.ghost-button:hover,
.secondary-button:hover {
  transform: translateY(-1px);
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}

.feedback,
.inline-status {
  margin-top: 18px;
  padding: 15px 16px;
  border-radius: 8px;
  background: #edf3f9;
}

.inline-status {
  margin-top: 0;
}

.feedback--error {
  background: #fff0ed;
  color: #b64131;
}

.empty-state {
  display: grid;
  gap: 10px;
  padding: 24px;
  border-radius: 10px;
  border: 1px dashed #cfd8e4;
  background: rgba(244, 247, 251, 0.9);
}

.usage-card-list,
.segment-list,
.history-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.usage-app-card,
.history-row,
.report-card,
.usage-report {
  border-radius: 10px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
}

.usage-app-card details {
  padding: 0;
}

.usage-app-card summary {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 18px 20px;
  cursor: pointer;
  list-style: none;
}

.usage-app-card summary::-webkit-details-marker {
  display: none;
}

.usage-app-card summary > span:first-child {
  display: grid;
  gap: 6px;
}

.duration-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 74px;
  padding: 7px 12px;
  border-radius: 999px;
  background: #edf3f9;
  color: #35506b;
  font-size: 0.8rem;
  letter-spacing: 0.06em;
}

.segment-list {
  gap: 8px;
  padding: 0 20px 18px;
}

.segment-list li {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f6f9fc;
  color: #5d6e83;
  font-size: 0.9rem;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  color: #5d6e83;
  font-size: 0.9rem;
}

.usage-report {
  display: grid;
  gap: 16px;
  padding: 18px 20px;
}

.report-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.report-meta span {
  padding: 6px 10px;
  border-radius: 999px;
  background: #edf3f9;
  color: #35506b;
  font-size: 0.8rem;
}

.report-detail-table {
  width: 100%;
  border-collapse: collapse;
  overflow: hidden;
  border-radius: 8px;
}

.report-detail-table th,
.report-detail-table td {
  padding: 11px 10px;
  border-bottom: 1px solid #e4ebf2;
  text-align: left;
  color: #35506b;
}

.report-detail-table th {
  background: #f5f8fb;
  color: #1d2e47;
  font-size: 0.82rem;
}

.report-card {
  margin-top: 18px;
  padding: 18px 20px;
  height: auto;
  min-width: 0;
  overflow: visible;
}

.report-card--current {
  background: #f8fafc;
}

.report-card p,
.history-row strong,
.report-summary {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.history-row {
  display: grid;
  gap: 8px;
  padding: 18px 20px;
}

@media (max-width: 960px) {
  .employee-layout {
    width: min(100%, calc(100% - 24px));
    padding-top: 18px;
  }

  .hero-card,
  .content-grid,
  .panel-head--with-controls,
  .pager {
    grid-template-columns: 1fr;
  }

  .hero-card,
  .pager {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions {
    width: 100%;
    justify-items: stretch;
  }

  .ghost-button,
  .date-control {
    width: 100%;
  }
}
</style>

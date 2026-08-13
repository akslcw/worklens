<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ManagerWorkspaceNav from '../components/ManagerWorkspaceNav.vue'
import { clearSession, readStoredSession } from '../auth/session'
import { getTeamUsageSummary, type AppUsageRatio, type TeamUsageSummary } from '../api/teamUsage'
import { getTeamReportHistory, type ReportHistoryItem } from '../api/teamReports'
import { hongKongDateTimeString } from '../utils/hongKongTime'

const router = useRouter()
const session = readStoredSession()

const summary = ref<TeamUsageSummary | null>(null)
const reportHistory = ref<ReportHistoryItem[]>([])
const loading = ref(false)
const errorMessage = ref('')

const topApps = computed(() => summary.value?.appUsageRatios ?? [])

onMounted(async () => {
  await loadManagerTeamView()
})

async function loadManagerTeamView() {
  if (!session?.token) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const [nextSummary, nextHistory] = await Promise.all([
      getTeamUsageSummary(session.token),
      getTeamReportHistory(session.token),
    ])

    summary.value = nextSummary
    reportHistory.value = nextHistory
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '团队数据加载失败。')
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
  clearSession()
  await router.replace('/login')
}

function formatMinutes(value: number) {
  return `${value} min`
}

function formatRatio(value: number) {
  return `${(value * 100).toFixed(2)}%`
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

function reportPeriodLabel(report: ReportHistoryItem) {
  if (report.periodStartDate && report.periodEndDate) {
    const typeLabel = periodTypeLabel(report.periodType)
    if (report.periodType === 'DAILY') {
      return `${formatDateOnly(report.periodStartDate)} · ${typeLabel}`
    }
    return `${formatDateOnly(report.periodStartDate)} - ${formatDateOnly(report.periodEndDate)} · ${typeLabel}`
  }
  if (!report.periodStartedAt || !report.periodEndedAt) {
    return '时间范围由后端历史记录提供'
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

function toErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return fallback
}
</script>

<template>
  <main class="team-layout">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Manager Workspace</p>
        <h1>团队效能面板</h1>
        <p class="hero-copy">这里只消费团队聚合接口和团队报告接口，不拉任何个人明细。</p>
        <div class="hero-nav">
          <ManagerWorkspaceNav current="team" />
        </div>
      </div>
      <div class="hero-actions">
        <div class="session-pill">
          <span>Current User</span>
          <strong>{{ session?.displayName ?? session?.username ?? 'manager' }}</strong>
        </div>
        <button class="ghost-button" type="button" @click="handleLogout">退出登录</button>
      </div>
    </section>

    <p v-if="errorMessage" class="feedback feedback--error" role="alert">{{ errorMessage }}</p>
    <p v-else-if="loading" class="feedback">正在加载团队聚合数据...</p>

    <template v-else-if="summary">
      <section class="metric-grid">
        <article class="metric-card">
          <span class="metric-label">Team Average</span>
          <strong>{{ summary.teamAverageUsageMinutes }}</strong>
          <p>团队平均使用时长</p>
        </article>

        <article class="metric-card">
          <span class="metric-label">Total Usage</span>
          <strong>{{ formatMinutes(summary.totalUsageMinutes) }}</strong>
          <p>团队总使用时长</p>
        </article>

        <article class="metric-card">
          <span class="metric-label">Active Employees</span>
          <strong>{{ summary.activeEmployeeCount }}</strong>
          <p>活跃员工数</p>
        </article>
      </section>

      <section class="content-grid">
        <article class="panel-card">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Aggregate Split</p>
              <h2>应用使用占比</h2>
            </div>
          </div>

          <ul class="ratio-list">
            <li v-for="app in topApps" :key="app.appName" class="ratio-row">
              <div class="ratio-meta">
                <strong>{{ app.appName }}</strong>
                <small>{{ formatMinutes(app.usageMinutes) }}</small>
              </div>
              <div class="ratio-meter">
                <span>{{ formatRatio(app.usageRatio) }}</span>
                <div class="meter-track">
                  <div class="meter-fill" :style="{ width: `${app.usageRatio * 100}%` }"></div>
                </div>
              </div>
            </li>
          </ul>
        </article>

        <article class="panel-card">
          <div class="panel-head">
            <div>
              <p class="eyebrow">LLM Summary</p>
              <h2>团队报告</h2>
            </div>
          </div>

          <div class="history-head">
            <p class="eyebrow">History</p>
          </div>

          <div v-if="reportHistory.length === 0" class="empty-state">
            <strong>暂无历史团队报告</strong>
            <p>报告会在系统定时归档任务完成后自动出现在这里。</p>
          </div>

          <ul v-else class="history-list">
            <li v-for="(report, index) in reportHistory" :key="`${report.createdAt}-${index}`" class="history-row">
              <div class="history-meta">
                <strong>{{ report.summary }}</strong>
                <small>生成时间：{{ formatDateTime(report.createdAt) }}</small>
                <small>覆盖时间：{{ reportPeriodLabel(report) }}</small>
              </div>
            </li>
          </ul>
        </article>
      </section>
    </template>
  </main>
</template>

<style scoped>
.team-layout {
  position: relative;
  z-index: 1;
  width: min(1180px, calc(100% - 40px));
  min-height: 100vh;
  margin: 0 auto;
  padding: 28px 0 36px;
}

.hero-card,
.metric-card,
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
.metric-card strong,
.panel-card h2,
.ratio-meta strong,
.history-meta strong,
.session-pill strong {
  margin: 0;
  color: #1d2e47;
}

.hero-card h1 {
  font-size: 2rem;
  line-height: 1.2;
}

.hero-copy,
.metric-card p,
.feedback,
.ratio-meta small,
.history-meta small,
.empty-state p,
.report-card p {
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
  min-width: 200px;
  border-radius: 10px;
  background: rgba(29, 46, 71, 0.92);
  color: rgba(238, 245, 252, 0.84);
}

.session-pill strong {
  color: #edf4fc;
  font-size: 1.2rem;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;
}

.metric-card {
  padding: 24px;
  background: #ffffff;
}

.metric-label {
  display: block;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #6f8196;
  font-size: 0.78rem;
}

.metric-card strong {
  display: block;
  font-size: 2rem;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
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

.history-head {
  margin-top: 22px;
  margin-bottom: 16px;
}

.panel-head h2 {
  font-size: 1.35rem;
}

.primary-button,
.ghost-button {
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

.primary-button:hover,
.ghost-button:hover {
  transform: translateY(-1px);
}

.primary-button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
}

.feedback {
  margin-top: 18px;
  padding: 15px 16px;
  border-radius: 8px;
  background: #edf3f9;
}

.feedback--error {
  background: #fff0ed;
  color: #b64131;
}

.ratio-list,
.history-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ratio-row,
.history-row,
.report-card,
.empty-state {
  border-radius: 10px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
}

.ratio-row {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
}

.ratio-meta {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.ratio-meter {
  display: grid;
  gap: 8px;
}

.meter-track {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: #dbe6f1;
  overflow: hidden;
}

.meter-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--wl-copper);
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
.history-meta strong {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.history-row {
  padding: 18px 20px;
}

.history-meta {
  display: grid;
  gap: 8px;
}

.empty-state {
  display: grid;
  gap: 10px;
  padding: 24px;
}

.empty-state strong {
  color: #1d2e47;
}

@media (max-width: 960px) {
  .team-layout {
    width: min(100%, calc(100% - 24px));
    padding-top: 18px;
  }

  .hero-card,
  .metric-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .hero-card {
    flex-direction: column;
    align-items: start;
  }

  .hero-actions {
    width: 100%;
    justify-items: stretch;
  }

  .ghost-button {
    width: 100%;
  }
}
</style>

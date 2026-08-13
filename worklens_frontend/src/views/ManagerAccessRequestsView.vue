<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Employee } from '../api/employees'
import { getEmployees } from '../api/employees'
import {
  createDetailAccessRequest,
  listOwnDetailAccessRequests,
  viewApprovedUsageView,
  type DetailAccessRequest,
} from '../api/detailAccessRequests'
import type { UsageAppCard, UsageView } from '../api/usageRecords'
import { clearSession, readStoredSession } from '../auth/session'
import ManagerWorkspaceNav from '../components/ManagerWorkspaceNav.vue'
import { hongKongDateString, hongKongDateTimeString, hongKongTimeString } from '../utils/hongKongTime'

const router = useRouter()
const session = readStoredSession()

const employees = ref<Employee[]>([])
const requests = ref<DetailAccessRequest[]>([])
const selectedRequestUsageView = ref<UsageView | null>(null)
const selectedRequestId = ref<number | null>(null)
const accessViewDate = ref(todayDateString())
const loading = ref(false)
const submitting = ref(false)
const viewingRequestId = ref<number | null>(null)
const errorMessage = ref('')

const form = reactive({
  targetEmployeeId: '',
  reason: '',
})

const employeeNameById = computed(() =>
  new Map(employees.value.map((employee) => [employee.id, employee.name])),
)

onMounted(async () => {
  await loadAccessRequestsView()
})

async function loadAccessRequestsView() {
  if (!session?.token) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const [nextEmployees, nextRequests] = await Promise.all([
      getEmployees(session.token),
      listOwnDetailAccessRequests(session.token),
    ])

    employees.value = nextEmployees
    requests.value = nextRequests
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '查看申请页面加载失败。')
  } finally {
    loading.value = false
  }
}

async function handleCreateRequest() {
  if (!session?.token || submitting.value) {
    return
  }

  submitting.value = true
  errorMessage.value = ''

  try {
    const createdRequest = await createDetailAccessRequest(
      {
        targetEmployeeId: Number(form.targetEmployeeId),
        reason: form.reason.trim(),
      },
      session.token,
    )

    requests.value = [...requests.value, createdRequest].sort(
      (left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime(),
    )
    form.targetEmployeeId = ''
    form.reason = ''
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '查看申请创建失败。')
  } finally {
    submitting.value = false
  }
}

async function handleViewApprovedRequest(request: DetailAccessRequest) {
  if (!session?.token || viewingRequestId.value !== null || request.status !== 'APPROVED') {
    return
  }

  viewingRequestId.value = request.id
  errorMessage.value = ''

  try {
    const usageView = await viewApprovedUsageView(request.id, session.token, accessViewDate.value)
    selectedRequestId.value = request.id
    selectedRequestUsageView.value = usageView
    requests.value = requests.value.map((item) =>
      item.id === request.id ? { ...item, status: 'USED' } : item,
    )
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '明细查看失败。')
  } finally {
    viewingRequestId.value = null
  }
}

async function handleLogout() {
  clearSession()
  await router.replace('/login')
}

function employeeName(targetEmployeeId: number) {
  return employeeNameById.value.get(targetEmployeeId) ?? `Employee #${targetEmployeeId}`
}

function statusLabel(status: DetailAccessRequest['status']) {
  if (status === 'PENDING') {
    return '待处理'
  }
  if (status === 'APPROVED') {
    return '已批准未使用'
  }
  if (status === 'USED') {
    return '已使用'
  }
  return '已拒绝'
}

function canViewRequest(request: DetailAccessRequest) {
  return request.status === 'APPROVED'
}

function todayDateString() {
  return hongKongDateString(new Date())
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '未处理'
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

function usageReportPeriodLabel() {
  if (!selectedRequestUsageView.value?.report) {
    return ''
  }
  return `${formatDateOnly(selectedRequestUsageView.value.report.periodStartDate)} - ${formatDateOnly(selectedRequestUsageView.value.report.periodEndDate)}`
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
  <main class="access-layout">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Manager Workspace</p>
        <h1>查看申请与一次性明细访问</h1>
        <p class="hero-copy">管理者只能在员工批准后，一次性查看指定申请对应的个人明细。用后即失效。</p>
        <div class="hero-nav">
          <ManagerWorkspaceNav current="access" />
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
    <p v-else-if="loading" class="feedback">正在加载查看申请...</p>

    <template v-else>
      <section class="content-grid">
        <article class="panel-card">
          <div class="panel-head">
            <div>
              <p class="eyebrow">Create Request</p>
              <h2>发起查看申请</h2>
            </div>
          </div>

          <form data-test="access-request-form" class="request-form" @submit.prevent="handleCreateRequest">
            <label class="field">
              <span>目标员工</span>
              <select data-test="target-employee-select" v-model="form.targetEmployeeId" required>
                <option value="" disabled>请选择员工</option>
                <option v-for="employee in employees" :key="employee.id" :value="String(employee.id)">
                  {{ employee.name }} / {{ employee.employeeNo }}
                </option>
              </select>
            </label>

            <label class="field">
              <span>申请理由</span>
              <textarea
                data-test="access-reason-input"
                v-model="form.reason"
                rows="5"
                placeholder="请填写此次查看个人明细的理由"
                required
              ></textarea>
            </label>

            <button class="primary-button" type="submit" :disabled="submitting">
              {{ submitting ? '提交中...' : '发起申请' }}
            </button>
          </form>
        </article>

        <article class="panel-card">
          <div class="panel-head panel-head--with-control">
            <div>
              <p class="eyebrow">My Requests</p>
              <h2>我发起的申请</h2>
            </div>
            <label class="date-control">
              <span>查看日期</span>
              <input data-test="access-view-date" v-model="accessViewDate" type="date" />
            </label>
          </div>

          <div v-if="requests.length === 0" class="empty-state">
            <strong>暂无申请记录</strong>
            <p>左侧提交一条申请后，这里会显示当前管理者发起过的全部申请。</p>
          </div>

          <ul v-else class="request-list">
            <li v-for="request in requests" :key="request.id" class="request-row">
              <div class="request-main">
                <div class="request-head">
                  <strong>{{ employeeName(request.targetEmployeeId) }}</strong>
                  <span class="status-chip" :class="`status-chip--${request.status.toLowerCase()}`">
                    {{ statusLabel(request.status) }}
                  </span>
                </div>
                <p>{{ request.reason }}</p>
                <small>发起时间：{{ formatDateTime(request.createdAt) }}</small>
                <small>处理时间：{{ formatDateTime(request.processedAt) }}</small>
              </div>

              <button
                :data-test="`view-request-${request.id}`"
                class="secondary-button"
                type="button"
                :disabled="!canViewRequest(request) || viewingRequestId === request.id"
                @click="handleViewApprovedRequest(request)"
              >
                {{
                  viewingRequestId === request.id
                    ? '读取中...'
                    : request.status === 'USED'
                      ? '已使用'
                      : request.status === 'APPROVED'
                        ? '查看一次明细'
                        : '不可查看'
                }}
              </button>
            </li>
          </ul>
        </article>
      </section>

      <section
        v-if="selectedRequestId !== null && selectedRequestUsageView"
        data-test="request-usage-view-panel"
        class="panel-card records-panel"
      >
        <div class="panel-head">
          <div>
            <p class="eyebrow">Consumed Authorization</p>
            <h2>已读取的个人使用视图</h2>
          </div>
        </div>

        <template v-if="selectedRequestUsageView.mode === 'LIVE_USAGE'">
          <div v-if="(selectedRequestUsageView.items ?? []).length === 0" class="empty-state">
            <strong>暂无实时使用记录</strong>
            <p>该日期没有可展示的实时应用使用数据。</p>
          </div>

          <ul v-else class="records-list">
            <li
              v-for="(card, index) in selectedRequestUsageView.items"
              :key="appCardKey(card, index)"
              class="record-row"
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
        </template>

        <article v-else-if="selectedRequestUsageView.report" class="usage-report">
          <div class="report-meta">
            <span>{{ selectedRequestUsageView.report.periodType }}</span>
            <span>{{ usageReportPeriodLabel() }}</span>
          </div>
          <p class="report-summary">{{ selectedRequestUsageView.report.summary }}</p>
          <table class="report-detail-table">
            <thead>
              <tr>
                <th>应用</th>
                <th>累计时长</th>
                <th>占比</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="detail in selectedRequestUsageView.report.details" :key="detail.appName">
                <td>{{ detail.appName }}</td>
                <td>{{ formatDurationSeconds(detail.durationSeconds) }}</td>
                <td>{{ formatPercent(detail.ratio) }}</td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>
    </template>
  </main>
</template>

<style scoped>
.access-layout {
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
.request-head strong,
.record-row strong,
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
.request-main p,
.request-main small,
.record-row p,
.empty-state p {
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

.content-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.82fr) minmax(0, 1.18fr);
  gap: 22px;
  margin-top: 22px;
}

.panel-card {
  padding: 24px;
  background: #ffffff;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-head--with-control {
  align-items: center;
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

.request-form {
  display: grid;
  gap: 16px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: #253650;
  font-size: 0.95rem;
}

.field select,
.field textarea {
  width: 100%;
  padding: 15px 16px;
  border: 1px solid #cfdae7;
  border-radius: 8px;
  background: #ffffff;
  font: inherit;
}

.field select:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--wl-copper);
  box-shadow: 0 0 0 3px rgba(47, 111, 168, 0.16);
}

.primary-button,
.secondary-button,
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

.secondary-button {
  padding: 12px 16px;
  background: #eef4fa;
  color: #1d2e47;
}

.ghost-button {
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid rgba(29, 46, 71, 0.14);
  color: #1d2e47;
}

.primary-button:hover,
.secondary-button:hover,
.ghost-button:hover {
  transform: translateY(-1px);
}

.primary-button:disabled,
.secondary-button:disabled {
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

.empty-state {
  display: grid;
  gap: 10px;
  padding: 24px;
  border-radius: 10px;
  border: 1px dashed #cfd8e4;
  background: rgba(244, 247, 251, 0.9);
}

.request-list,
.records-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.request-row,
.record-row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: start;
  padding: 18px 20px;
  border-radius: 10px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
}

.request-main {
  display: grid;
  gap: 8px;
}

.record-row details {
  width: 100%;
}

.record-row summary {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  cursor: pointer;
  list-style: none;
}

.record-row summary::-webkit-details-marker {
  display: none;
}

.record-row summary > span:first-child {
  display: grid;
  gap: 6px;
}

.request-head {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.status-chip,
.duration-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 0.8rem;
  letter-spacing: 0.06em;
}

.status-chip--pending {
  background: #eef4fa;
  color: #46617b;
}

.status-chip--approved {
  background: #eef8f0;
  color: #2e6a3a;
}

.status-chip--used {
  background: #f2f0ff;
  color: #5b4ab5;
}

.status-chip--rejected {
  background: #fff0ed;
  color: #b64131;
}

.duration-chip {
  background: #edf3f9;
  color: #35506b;
}

.segment-list {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.segment-list li {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f6f9fc;
  color: #5d6e83;
  font-size: 0.9rem;
}

.usage-report {
  display: grid;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 10px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
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

.report-summary {
  margin: 0;
  line-height: 1.7;
  color: #5d6e83;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
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

.records-panel {
  margin-top: 22px;
}

@media (max-width: 980px) {
  .access-layout {
    width: min(100%, calc(100% - 24px));
    padding-top: 18px;
  }

  .hero-card,
  .content-grid,
  .panel-head--with-control,
  .request-row,
  .record-row {
    grid-template-columns: 1fr;
  }

  .hero-card,
  .request-row,
  .record-row {
    flex-direction: column;
    align-items: start;
  }

  .hero-actions {
    width: 100%;
    justify-items: stretch;
  }

  .ghost-button,
  .secondary-button,
  .date-control {
    width: 100%;
  }
}
</style>

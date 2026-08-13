<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  decideDetailAccessRequest,
  listRequestsTargetingCurrentEmployee,
  type EmployeeDetailAccessRequest,
} from '../api/detailAccessRequests'
import { clearSession, readStoredSession } from '../auth/session'
import { logout } from '../api/auth'
import EmployeeWorkspaceNav from '../components/EmployeeWorkspaceNav.vue'
import { hongKongDateTimeString } from '../utils/hongKongTime'

const router = useRouter()
const session = readStoredSession()

const requests = ref<EmployeeDetailAccessRequest[]>([])
const loading = ref(false)
const decidingId = ref<number | null>(null)
const errorMessage = ref('')

onMounted(async () => {
  await loadAccessRecords()
})

async function loadAccessRecords() {
  if (!session?.token) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    requests.value = await listRequestsTargetingCurrentEmployee(session.token)
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '审批与访问记录加载失败。')
  } finally {
    loading.value = false
  }
}

async function handleDecision(request: EmployeeDetailAccessRequest, decision: 'APPROVED' | 'REJECTED') {
  if (!session?.token || decidingId.value !== null || request.status !== 'PENDING') {
    return
  }

  decidingId.value = request.id
  errorMessage.value = ''

  try {
    const decided = await decideDetailAccessRequest(request.id, decision, session.token)
    requests.value = requests.value.map((item) =>
      item.id === request.id
        ? {
            ...item,
            status: decided.status,
            processedAt: decided.processedAt,
            hasBeenViewed: false,
            viewedAt: null,
          }
        : item,
    )
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '审批操作失败。')
  } finally {
    decidingId.value = null
  }
}

async function handleLogout() {
  const token = session?.token
  clearSession()
  if (token) {
    await logout(token).catch(() => {})
  }
  await router.replace('/login')
}

function requesterName(request: EmployeeDetailAccessRequest) {
  return request.requesterEmployeeName ?? `Employee #${request.requesterEmployeeId}`
}

function stateLabel(request: EmployeeDetailAccessRequest) {
  if (request.status === 'PENDING') {
    return '待处理'
  }
  if (request.status === 'REJECTED') {
    return '已拒绝'
  }
  if (request.hasBeenViewed || request.status === 'USED') {
    return '已批准且已查看'
  }
  return '已批准未查看'
}

function stateClass(request: EmployeeDetailAccessRequest) {
  if (request.status === 'PENDING') {
    return 'status-chip--pending'
  }
  if (request.status === 'REJECTED') {
    return 'status-chip--rejected'
  }
  if (request.hasBeenViewed || request.status === 'USED') {
    return 'status-chip--used'
  }
  return 'status-chip--approved'
}

function canDecide(request: EmployeeDetailAccessRequest) {
  return request.status === 'PENDING'
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '暂无'
  }

  return hongKongDateTimeString(value)
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
        <p class="eyebrow">Employee Workspace</p>
        <h1>审批与访问记录</h1>
        <p class="hero-copy">这里展示所有指向当前员工的查看申请，以及授权后对方是否已经实际查看过。</p>
        <div class="hero-nav">
          <EmployeeWorkspaceNav current="access" />
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
    <p v-else-if="loading" class="feedback">正在加载审批与访问记录...</p>

    <section v-else class="panel-card">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Requests Targeting Me</p>
          <h2>指向我的申请</h2>
        </div>
      </div>

      <div v-if="requests.length === 0" class="empty-state">
        <strong>暂无查看申请</strong>
        <p>当管理者向你申请查看个人明细时，记录会出现在这里。</p>
      </div>

      <ul v-else class="request-list">
        <li v-for="request in requests" :key="request.id" class="request-row">
          <div class="request-main">
            <div class="request-head">
              <strong>{{ requesterName(request) }}</strong>
              <span class="status-chip" :class="stateClass(request)">
                {{ stateLabel(request) }}
              </span>
            </div>
            <p>{{ request.reason }}</p>
            <small>申请时间：{{ formatDateTime(request.createdAt) }}</small>
            <small>处理时间：{{ formatDateTime(request.processedAt) }}</small>
            <small>查看时间：{{ formatDateTime(request.viewedAt) }}</small>
          </div>

          <div v-if="canDecide(request)" class="decision-actions">
            <button
              :data-test="`approve-request-${request.id}`"
              class="approve-button"
              type="button"
              :disabled="decidingId === request.id"
              @click="handleDecision(request, 'APPROVED')"
            >
              {{ decidingId === request.id ? '处理中...' : '批准' }}
            </button>
            <button
              :data-test="`reject-request-${request.id}`"
              class="reject-button"
              type="button"
              :disabled="decidingId === request.id"
              @click="handleDecision(request, 'REJECTED')"
            >
              {{ decidingId === request.id ? '处理中...' : '拒绝' }}
            </button>
          </div>
        </li>
      </ul>
    </section>
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
  min-width: 220px;
  border-radius: 10px;
  background: rgba(29, 46, 71, 0.92);
  color: rgba(238, 245, 252, 0.84);
}

.session-pill strong {
  color: #edf4fc;
  font-size: 1.2rem;
}

.panel-card {
  margin-top: 22px;
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

.panel-head h2 {
  font-size: 1.35rem;
}

.ghost-button,
.approve-button,
.reject-button {
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease;
  font-size: 0.95rem;
}

.ghost-button {
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid rgba(29, 46, 71, 0.14);
  color: #1d2e47;
}

.approve-button,
.reject-button {
  padding: 11px 15px;
}

.approve-button {
  background: #eef8f0;
  color: #2e6a3a;
}

.reject-button {
  background: #fff0ed;
  color: #b64131;
}

.ghost-button:hover,
.approve-button:hover,
.reject-button:hover {
  transform: translateY(-1px);
}

.approve-button:disabled,
.reject-button:disabled {
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

.request-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.request-row {
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

.request-head {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.status-chip {
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

.decision-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .access-layout {
    width: min(100%, calc(100% - 24px));
    padding-top: 18px;
  }

  .hero-card,
  .request-row {
    flex-direction: column;
    align-items: start;
  }

  .hero-actions {
    width: 100%;
    justify-items: stretch;
  }

  .ghost-button,
  .approve-button,
  .reject-button {
    width: 100%;
  }
}
</style>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createEmployee,
  deleteEmployee,
  getEmployees,
  resetEmployeePassword,
  type Employee,
  type ResetEmployeePasswordResponse,
} from '../api/employees'
import { logout } from '../api/auth'
import { clearSession, readStoredSession } from '../auth/session'
import ManagerWorkspaceNav from '../components/ManagerWorkspaceNav.vue'
import { hongKongDateTimeString } from '../utils/hongKongTime'

const router = useRouter()
const session = readStoredSession()

const employees = ref<Employee[]>([])
const loading = ref(false)
const submitting = ref(false)
const deletingId = ref<number | null>(null)
const resettingId = ref<number | null>(null)
const resetResult = ref<ResetEmployeePasswordResponse | null>(null)
const errorMessage = ref('')

const form = reactive({
  name: '',
  employeeNo: '',
})

onMounted(async () => {
  await loadEmployees()
})

async function loadEmployees() {
  if (!session?.token) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    employees.value = await getEmployees(session.token)
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '员工列表加载失败。')
  } finally {
    loading.value = false
  }
}

async function handleCreateEmployee() {
  if (!session?.token || submitting.value) {
    return
  }

  submitting.value = true
  errorMessage.value = ''

  try {
    const createdEmployee = await createEmployee(
      {
        name: form.name.trim(),
        employeeNo: form.employeeNo.trim(),
      },
      session.token,
    )

    employees.value = [...employees.value, createdEmployee].sort((left, right) => left.id - right.id)
    resetResult.value = {
      username: createdEmployee.employeeNo,
      initialPassword: createdEmployee.initialPassword,
      mustChangePassword: createdEmployee.mustChangePassword,
    }
    form.name = ''
    form.employeeNo = ''
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '员工新增失败。')
  } finally {
    submitting.value = false
  }
}

async function handleDeleteEmployee(id: number) {
  if (!session?.token || deletingId.value !== null) {
    return
  }

  deletingId.value = id
  errorMessage.value = ''

  try {
    await deleteEmployee(id, session.token)
    employees.value = employees.value.filter((employee) => employee.id !== id)
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '员工删除失败。')
  } finally {
    deletingId.value = null
  }
}

async function handleResetPassword(id: number) {
  if (!session?.token || resettingId.value !== null) {
    return
  }

  resettingId.value = id
  errorMessage.value = ''

  try {
    resetResult.value = await resetEmployeePassword(id, session.token)
  } catch (error) {
    errorMessage.value = toErrorMessage(error, '密码重置失败。')
  } finally {
    resettingId.value = null
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

function formatDateTime(value: string) {
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
  <main class="manager-layout">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Manager Workspace</p>
        <h1>员工档案管理</h1>
        <p class="hero-copy">新增员工时会同步创建登录账号，账号名等于工号，并生成一次性临时密码。</p>
        <div class="hero-nav">
          <ManagerWorkspaceNav current="directory" />
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

    <section class="content-grid">
      <article class="panel-card panel-card--form">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Create Employee</p>
            <h2>新增员工档案</h2>
          </div>
        </div>

        <form data-test="employee-form" class="employee-form" @submit.prevent="handleCreateEmployee">
          <label class="field">
            <span>姓名</span>
            <input
              data-test="employee-name-input"
              v-model="form.name"
              type="text"
              placeholder="例如：Alice Chen"
              required
            />
          </label>

          <label class="field">
            <span>工号</span>
            <input
              data-test="employee-no-input"
              v-model="form.employeeNo"
              type="text"
              placeholder="例如：WL-001"
              required
            />
          </label>

          <button class="primary-button" type="submit" :disabled="submitting">
            {{ submitting ? '提交中...' : '新增员工' }}
          </button>
        </form>

        <p class="panel-note">登录账号使用工号。系统会生成随机临时密码，首次登录后必须修改。</p>
      </article>

      <article class="panel-card panel-card--list">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Employee Directory</p>
            <h2>员工列表</h2>
          </div>
        </div>

        <p v-if="errorMessage" class="feedback feedback--error" role="alert">{{ errorMessage }}</p>
        <p v-else-if="loading" class="feedback">正在加载员工列表...</p>

        <div v-else-if="employees.length === 0" data-test="employee-empty" class="empty-state">
          <strong>No employees yet</strong>
          <p>先在左侧创建一条员工档案，列表会立即刷新到当前页。</p>
        </div>

        <ul v-else data-test="employee-list" class="employee-list">
          <li v-for="employee in employees" :key="employee.id" class="employee-row">
            <div class="employee-meta">
              <strong>{{ employee.name }}</strong>
              <span class="employee-code">{{ employee.employeeNo }}</span>
              <small>创建时间：{{ formatDateTime(employee.createdAt) }}</small>
            </div>

            <div class="employee-actions">
              <button
                :data-test="`delete-employee-${employee.id}`"
                class="danger-button"
                type="button"
                :disabled="deletingId === employee.id"
                @click="handleDeleteEmployee(employee.id)"
              >
                {{ deletingId === employee.id ? '删除中...' : '删除' }}
              </button>
              <button
                :data-test="`reset-password-${employee.id}`"
                class="secondary-button"
                type="button"
                :disabled="resettingId === employee.id"
                @click="handleResetPassword(employee.id)"
              >
                {{ resettingId === employee.id ? '重置中...' : '重置密码' }}
              </button>
            </div>
          </li>
        </ul>

        <p v-if="resetResult" class="feedback" role="status">
          账号 {{ resetResult.username }} 的初始密码为 {{ resetResult.initialPassword }}，首次登录后必须修改密码。
        </p>
      </article>
    </section>
  </main>
</template>

<style scoped>
.manager-layout {
  position: relative;
  z-index: 1;
  width: min(1160px, calc(100% - 40px));
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
.employee-meta strong,
.session-pill strong {
  margin: 0;
  color: #1d2e47;
}

.hero-card h1 {
  font-size: 2rem;
  line-height: 1.2;
}

.hero-copy,
.employee-meta small,
.empty-state p,
.panel-note,
.feedback {
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
  grid-template-columns: minmax(320px, 0.86fr) minmax(0, 1.14fr);
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
  margin-bottom: 22px;
}

.panel-head h2 {
  font-size: 1.35rem;
}

.employee-form {
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

.field input {
  width: 100%;
  padding: 15px 16px;
  border: 1px solid #cfdae7;
  border-radius: 8px;
  background: #ffffff;
  font-size: 1rem;
}

.field input:focus {
  outline: none;
  border-color: var(--wl-copper);
  box-shadow: 0 0 0 3px rgba(47, 111, 168, 0.16);
}

.primary-button,
.ghost-button,
.secondary-button,
.danger-button {
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

.danger-button {
  padding: 11px 16px;
  background: #fff0ed;
  color: #b64131;
}

.secondary-button {
  padding: 11px 16px;
  background: #edf3f9;
  color: #35506b;
}

.primary-button:hover,
.ghost-button:hover,
.secondary-button:hover,
.danger-button:hover {
  transform: translateY(-1px);
}

.primary-button:disabled,
.secondary-button:disabled,
.danger-button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
}

.panel-note {
  margin-top: 16px;
}

.feedback {
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
  padding: 26px;
  border-radius: 10px;
  border: 1px dashed #cfd8e4;
  background: rgba(244, 247, 251, 0.9);
}

.empty-state strong {
  color: #1d2e47;
}

.employee-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.employee-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 18px 20px;
  border-radius: 10px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
}

.employee-actions {
  display: grid;
  gap: 8px;
  min-width: 116px;
}

.employee-meta {
  display: grid;
  gap: 8px;
}

.employee-meta strong {
  font-size: 1.18rem;
}

.employee-code {
  display: inline-flex;
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: #edf3f9;
  color: #35506b;
  font-size: 0.82rem;
  letter-spacing: 0.06em;
}

@media (max-width: 940px) {
  .manager-layout {
    width: min(100%, calc(100% - 24px));
    padding-top: 18px;
  }

  .hero-card,
  .content-grid,
  .employee-row {
    grid-template-columns: 1fr;
  }

  .hero-card,
  .employee-row {
    flex-direction: column;
    align-items: start;
  }

  .hero-actions {
    width: 100%;
    justify-items: stretch;
  }

  .ghost-button,
  .secondary-button,
  .danger-button {
    width: 100%;
  }
}
</style>

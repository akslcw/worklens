<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword, logout } from '../api/auth'
import { clearSession, readStoredSession } from '../auth/session'

const router = useRouter()
const session = readStoredSession()

const form = reactive({
  currentPassword: '',
  newPassword: '',
})

const submitting = ref(false)
const errorMessage = ref('')
const passwordChanged = ref(false)

async function handleChangePassword() {
  if (!session?.token || submitting.value) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  passwordChanged.value = false

  try {
    const response = await changePassword(
      {
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      },
      session.token,
    )

    clearSession()
    passwordChanged.value = true
    form.currentPassword = ''
    form.newPassword = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '密码修改失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}

async function handleContinue() {
  passwordChanged.value = false
  await router.replace('/login')
}

async function handleLogout() {
  const token = session?.token
  clearSession()
  if (token) {
    await logout(token).catch(() => {})
  }
  await router.replace('/login')
}
</script>

<template>
  <main class="password-layout">
    <section class="password-card">
      <p class="eyebrow">Password Required</p>
      <h1>修改初始密码</h1>
      <p class="intro-copy">
        当前账号正在使用系统生成的临时密码。修改完成后，才能继续进入 WorkLens 的业务页面。
      </p>

      <div v-if="passwordChanged" data-test="change-password-success" class="success-panel" role="status">
        <strong>密码已修改</strong>
        <p>出于安全考虑，修改密码后需要重新登录。请使用新密码登录 WorkLens。</p>
        <button data-test="continue-after-password-change" class="primary-button" type="button" @click="handleContinue">
          前往登录
        </button>
      </div>

      <form v-else data-test="change-password-form" class="password-form" @submit.prevent="handleChangePassword">
        <label class="field">
          <span>当前密码</span>
          <input
            data-test="current-password-input"
            v-model="form.currentPassword"
            type="password"
            autocomplete="current-password"
            required
          />
        </label>

        <label class="field">
          <span>新密码</span>
          <input
            data-test="new-password-input"
            v-model="form.newPassword"
            type="password"
            autocomplete="new-password"
            required
          />
        </label>

        <button class="primary-button" type="submit" :disabled="submitting">
          {{ submitting ? '提交中...' : '修改密码并继续' }}
        </button>
      </form>

      <p v-if="errorMessage" class="feedback feedback--error" role="alert">{{ errorMessage }}</p>

      <button class="ghost-button" type="button" @click="handleLogout">退出登录</button>
    </section>
  </main>
</template>

<style scoped>
.password-layout {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  min-height: 100vh;
  width: min(100%, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0;
}

.password-card {
  width: min(520px, 100%);
  padding: 28px;
  border-radius: 12px;
  border: 1px solid rgba(29, 46, 71, 0.12);
  background: #ffffff;
  box-shadow: var(--wl-shadow);
}

.eyebrow {
  margin: 0 0 10px;
  color: var(--wl-copper);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 0.76rem;
}

h1 {
  margin: 0;
  color: #1d2e47;
  font-size: 2rem;
  line-height: 1.2;
}

.intro-copy,
.feedback {
  margin: 12px 0 0;
  line-height: 1.7;
  color: #5d6e83;
}

.password-form {
  display: grid;
  gap: 16px;
  margin-top: 24px;
}

.success-panel {
  display: grid;
  gap: 12px;
  margin-top: 24px;
  padding: 18px;
  border-radius: 10px;
  border: 1px solid #cfe2d5;
  background: #f3faf5;
}

.success-panel strong {
  color: #1d2e47;
}

.success-panel p {
  margin: 0;
  color: #5d6e83;
}

.success-panel code {
  display: block;
  padding: 12px 14px;
  border-radius: 8px;
  background: #ffffff;
  color: #1d2e47;
  font-size: 1rem;
  overflow-wrap: anywhere;
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
  margin-top: 14px;
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
  padding: 15px 16px;
  border-radius: 8px;
}

.feedback--error {
  background: #fff0ed;
  color: #b64131;
}
</style>

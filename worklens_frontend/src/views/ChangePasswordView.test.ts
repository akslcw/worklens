import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import ChangePasswordView from './ChangePasswordView.vue'

describe('ChangePasswordView', () => {
  beforeEach(() => {
    sessionStorage.setItem(
      'worklens-session',
      JSON.stringify({
        token: 'employee-token',
        username: 'E001',
        role: 'EMPLOYEE',
        mustChangePassword: true,
      }),
    )
    vi.restoreAllMocks()
  })

  it('confirms a successful change, clears the revoked session and sends the user to login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(String(input)).toContain('/api/auth/change-password')
        expect(init?.method).toBe('POST')
        expect(init?.headers).toMatchObject({
          Authorization: 'Bearer employee-token',
        })
        expect(init?.body).toBe(
          JSON.stringify({
            currentPassword: 'worklens123',
            newPassword: 'Changed123!',
          }),
        )

        return jsonResponse({
          username: 'E001',
          mustChangePassword: false,
        })
      }),
    )

    const { router, wrapper } = await mountChangePassword()

    await wrapper.get('[data-test="current-password-input"]').setValue('worklens123')
    await wrapper.get('[data-test="new-password-input"]').setValue('Changed123!')
    await wrapper.get('[data-test="confirm-password-input"]').setValue('Changed123!')
    await wrapper.get('[data-test="change-password-form"]').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/change-password')
    expect(wrapper.get('[data-test="change-password-success"]').text()).toContain('密码已修改')
    expect(wrapper.get('[data-test="change-password-success"]').text()).not.toContain('Changed123!')
    expect(sessionStorage.getItem('worklens-session')).toBeNull()

    await wrapper.get('[data-test="continue-after-password-change"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/login')
  })

  it.each([
    ['same as current password', 'worklens123', 'worklens123', 'worklens123', '新密码不能与当前密码相同。'],
    ['too weak', 'worklens123', 'weakpass', 'weakpass', '至少 8 位'],
    ['missing symbol', 'worklens123', 'Changed1234', 'Changed1234', '至少 8 位'],
    ['confirm mismatch', 'worklens123', 'Changed123!', 'Changed456!', '两次输入的新密码不一致。'],
  ])('rejects %s without calling the API', async (_label, currentPassword, newPassword, confirmPassword, expectedMessage) => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const { wrapper } = await mountChangePassword()

    await wrapper.get('[data-test="current-password-input"]').setValue(currentPassword)
    await wrapper.get('[data-test="new-password-input"]').setValue(newPassword)
    await wrapper.get('[data-test="confirm-password-input"]').setValue(confirmPassword)
    await wrapper.get('[data-test="change-password-form"]').trigger('submit')
    await flushPromises()

    expect(fetchMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain(expectedMessage)
    expect(wrapper.find('[data-test="change-password-success"]').exists()).toBe(false)
  })
})

async function mountChangePassword() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/change-password', component: ChangePasswordView },
      { path: '/login', component: { template: '<div />' } },
    ],
  })

  router.push('/change-password')
  await router.isReady()

  const wrapper = mount(ChangePasswordView, {
    global: {
      plugins: [router],
    },
  })

  return { router, wrapper }
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import App from './App.vue'
import { resolveHomePath, removeLegacyLocalSession } from './auth/session'
import type { RouteRole } from './auth/types'
import { createAppRouter } from './router'

const SESSION_STORAGE_KEY = 'worklens-session'

describe('App routing', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('redirects unauthenticated users from business routes to login', async () => {
    const router = createAppRouter(createMemoryHistory())
    router.push('/manager')
    await router.isReady()

    mount(App, {
      global: {
        plugins: [router],
      },
    })

    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/login')
  })

  it('shows a minimal employee login page without marketing copy', async () => {
    const { wrapper } = await mountAppAt('/login')

    expect(wrapper.text()).toContain('WorkLens 登录')
    expect(wrapper.get('[data-test="username-input"]').exists()).toBe(true)
    expect(wrapper.get('[data-test="password-input"]').exists()).toBe(true)
    expect(wrapper.get('[data-test="login-form"]').text()).toContain('登录')
    expect(wrapper.text()).not.toContain('把权限边界做成真正可点击的前端入口')
    expect(wrapper.text()).not.toContain('团队聚合')
    expect(wrapper.text()).not.toContain('个人明细')
  })

  it('routes manager login to the manager home page', async () => {
    stubManagerLoginFetch({
      token: 'manager-token',
      username: 'manager',
      displayName: 'Manager User',
      role: 'MANAGER',
    })

    const { router, wrapper } = await mountAppAt('/login')

    await wrapper.get('[data-test="username-input"]').setValue('manager')
    await wrapper.get('[data-test="password-input"]').setValue('Password123!')
    await wrapper.get('[data-test="login-form"]').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/manager')
    expect(wrapper.text()).toContain('Manager User')
    expect(wrapper.text()).toContain('员工档案管理')
    expect(wrapper.text()).toContain('No employees yet')
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '{}')).toMatchObject({
      token: 'manager-token',
      role: 'MANAGER',
    })
  })

  it('routes employee login to the employee home page', async () => {
    stubEmployeeLoginFetch({
      token: 'employee-token',
      username: 'C001',
      displayName: 'Li',
      role: 'EMPLOYEE',
    })

    const { router, wrapper } = await mountAppAt('/login')

    await wrapper.get('[data-test="username-input"]').setValue('C001')
    await wrapper.get('[data-test="password-input"]').setValue('Password123!')
    await wrapper.get('[data-test="login-form"]').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/employee')
    expect(wrapper.text()).toContain('Li')
    expect(wrapper.text()).toContain('个人效率面板')
    expect(wrapper.text()).toContain('暂无个人使用记录')
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '{}')).toMatchObject({
      token: 'employee-token',
      role: 'EMPLOYEE',
    })
  })

  it('routes users who must change password to the change password page first', async () => {
    stubEmployeeLoginFetch({
      token: 'employee-token',
      username: 'E001',
      role: 'EMPLOYEE',
      mustChangePassword: true,
    })

    const { router, wrapper } = await mountAppAt('/login')

    await wrapper.get('[data-test="username-input"]').setValue('E001')
    await wrapper.get('[data-test="password-input"]').setValue('worklens123')
    await wrapper.get('[data-test="login-form"]').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/change-password')
    expect(wrapper.text()).toContain('修改初始密码')
  })

  it('restores an existing manager session and allows protected routes', async () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({
        token: 'stored-manager-token',
        username: 'manager',
        role: 'MANAGER',
      }),
    )
    stubStoredManagerFetch()

    const router = createAppRouter(createMemoryHistory())
    router.push('/manager')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [router],
      },
    })

    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/manager')
    expect(wrapper.text()).toContain('员工档案管理')
  })

  it('sends must-change-password users straight to the change password page', async () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({
        token: 'stored-token',
        username: 'E001',
        role: 'EMPLOYEE',
        mustChangePassword: true,
      }),
    )

    const router = createAppRouter(createMemoryHistory())
    router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/change-password')
  })

  it('redirects unknown routes instead of rendering a blank page', async () => {
    const router = createAppRouter(createMemoryHistory())
    router.push('/missing-page')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/login')
  })

  it('removes legacy localStorage tokens instead of restoring them', async () => {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({
      token: 'legacy-token',
      username: 'manager',
      role: 'MANAGER',
    }))

    removeLegacyLocalSession()

    const router = createAppRouter(createMemoryHistory())
    router.push('/')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/login')
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })
})

describe('route role mapping', () => {
  it('maps backend roles to frontend landing pages', () => {
    expect(resolveHomePath('MANAGER')).toBe('/manager')
    expect(resolveHomePath('EMPLOYEE')).toBe('/employee')
  })
})

async function mountAppAt(path: string) {
  const router = createAppRouter(createMemoryHistory())
  router.push(path)
  await router.isReady()

  const wrapper = mount(App, {
    global: {
      plugins: [router],
    },
  })

  return { router, wrapper }
}

function stubManagerLoginFetch(session: { token: string; username: string; displayName?: string; role: RouteRole }) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'

      if (url.endsWith('/auth/login') && method === 'POST') {
        return new Response(JSON.stringify(session), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }

      if (url.endsWith('/api/employees') && method === 'GET') {
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }

      return new Response(null, { status: 404 })
    }),
  )
}

function stubStoredManagerFetch() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'

      if (url.endsWith('/api/employees') && method === 'GET') {
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }

      return new Response(null, { status: 404 })
    }),
  )
}

function stubEmployeeLoginFetch(session: { token: string; username: string; displayName?: string; role: RouteRole; mustChangePassword?: boolean }) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'

      if (url.endsWith('/auth/login') && method === 'POST') {
        return new Response(JSON.stringify(session), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }

      if (url.includes('/api/usage-records/view?') && method === 'GET') {
        return new Response(JSON.stringify({
          mode: 'LIVE_USAGE',
          date: '2026-07-08',
          page: 1,
          pageSize: 10,
          totalApps: 0,
          items: [],
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }

      if (url.endsWith('/api/llm/employee-report-history') && method === 'GET') {
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }

      return new Response(null, { status: 404 })
    }),
  )
}

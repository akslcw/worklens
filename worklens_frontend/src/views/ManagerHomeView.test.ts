import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import ManagerHomeView from './ManagerHomeView.vue'

describe('ManagerHomeView', () => {
  beforeEach(() => {
    sessionStorage.setItem(
      'worklens-session',
      JSON.stringify({
        token: 'manager-token',
        username: 'manager',
        role: 'MANAGER',
      }),
    )
    vi.restoreAllMocks()
  })

  it('loads employees for the manager dashboard', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        expect(String(input)).toContain('/api/employees')
        expect(init?.headers).toMatchObject({
          Authorization: 'Bearer manager-token',
        })

        return new Response(
          JSON.stringify([
            {
              id: 1,
              name: 'Alice Chen',
              employeeNo: 'WL-001',
              createdAt: '2026-07-05T09:00:00',
            },
            {
              id: 2,
              name: 'Bob Lin',
              employeeNo: 'WL-002',
              createdAt: '2026-07-05T09:30:00',
            },
          ]),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        )
      }),
    )

    const wrapper = await mountManagerHome()
    await flushPromises()

    expect(wrapper.get('[data-test="employee-list"]').text()).toContain('Alice Chen')
    expect(wrapper.get('[data-test="employee-list"]').text()).toContain('WL-002')
  })

  it('creates a new employee from the manager page', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        const method = init?.method ?? 'GET'

        if (url.endsWith('/api/employees') && method === 'GET') {
          return jsonResponse([])
        }

        if (url.endsWith('/api/employees') && method === 'POST') {
          expect(init?.headers).toMatchObject({
            Authorization: 'Bearer manager-token',
          })
          expect(init?.body).toBe(JSON.stringify({ name: 'Carol Wu', employeeNo: 'WL-003' }))

          return jsonResponse({
            id: 3,
            name: 'Carol Wu',
            employeeNo: 'WL-003',
            createdAt: '2026-07-05T10:00:00',
            initialPassword: 'RandomCreatePassword7!',
            mustChangePassword: true,
          }, 201)
        }

        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = await mountManagerHome()
    await flushPromises()

    await wrapper.get('[data-test="employee-name-input"]').setValue('Carol Wu')
    await wrapper.get('[data-test="employee-no-input"]').setValue('WL-003')
    await wrapper.get('[data-test="employee-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-test="employee-list"]').text()).toContain('Carol Wu')
    expect(wrapper.get('[data-test="employee-list"]').text()).toContain('WL-003')
    expect(wrapper.text()).not.toContain('RandomCreatePassword7!')

    await wrapper.get('[data-test="toggle-password-visibility"]').trigger('click')
    expect(wrapper.get('[data-test="initial-password-value"]').text()).toContain('RandomCreatePassword7!')
    expect(wrapper.text()).not.toContain('worklens123')
  })

  it('resets an employee password from the action area below delete', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        const method = init?.method ?? 'GET'

        if (url.endsWith('/api/employees') && method === 'GET') {
          return jsonResponse([
            {
              id: 1,
              name: 'Alice Chen',
              employeeNo: 'E001',
              createdAt: '2026-07-05T09:00:00',
            },
          ])
        }

        if (url.endsWith('/api/employees/1/reset-password') && method === 'POST') {
          expect(init?.headers).toMatchObject({
            Authorization: 'Bearer manager-token',
          })
          return jsonResponse({
            username: 'E001',
            initialPassword: 'RandomResetPassword8!',
            mustChangePassword: true,
          })
        }

        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = await mountManagerHome()
    await flushPromises()

    await wrapper.get('[data-test="reset-password-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('E001')
    expect(wrapper.text()).not.toContain('RandomResetPassword8!')

    await wrapper.get('[data-test="toggle-password-visibility"]').trigger('click')
    expect(wrapper.get('[data-test="initial-password-value"]').text()).toContain('RandomResetPassword8!')
    expect(wrapper.text()).not.toContain('worklens123')
    expect(wrapper.text()).toContain('首次登录后必须修改密码')
  })

  it('deletes an employee from the manager page after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let employees = [
      {
        id: 1,
        name: 'Alice Chen',
        employeeNo: 'WL-001',
        createdAt: '2026-07-05T09:00:00',
      },
    ]

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        const method = init?.method ?? 'GET'

        if (url.endsWith('/api/employees') && method === 'GET') {
          return jsonResponse(employees)
        }

        if (url.endsWith('/api/employees/1') && method === 'DELETE') {
          expect(init?.headers).toMatchObject({
            Authorization: 'Bearer manager-token',
          })
          employees = []
          return new Response(null, { status: 204 })
        }

        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = await mountManagerHome()
    await flushPromises()

    await wrapper.get('[data-test="delete-employee-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="employee-empty"]').text()).toContain('No employees yet')
  })

  it('keeps the employee when the delete confirmation is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    let deleteCalled = false
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        const method = init?.method ?? 'GET'

        if (url.endsWith('/api/employees') && method === 'GET') {
          return jsonResponse([
            {
              id: 1,
              name: 'Alice Chen',
              employeeNo: 'WL-001',
              createdAt: '2026-07-05T09:00:00',
            },
          ])
        }

        if (url.endsWith('/api/employees/1') && method === 'DELETE') {
          deleteCalled = true
        }

        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = await mountManagerHome()
    await flushPromises()

    await wrapper.get('[data-test="delete-employee-1"]').trigger('click')
    await flushPromises()

    expect(deleteCalled).toBe(false)
    expect(wrapper.get('[data-test="employee-list"]').text()).toContain('Alice Chen')
  })
})

async function mountManagerHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/manager', component: ManagerHomeView },
      { path: '/manager/team', component: { template: '<div />' } },
      { path: '/manager/access-requests', component: { template: '<div />' } },
    ],
  })

  router.push('/manager')
  await router.isReady()

  return mount(ManagerHomeView, {
    global: {
      plugins: [router],
    },
  })
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

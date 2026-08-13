import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import EmployeeHomeView from './EmployeeHomeView.vue'

describe('EmployeeHomeView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-08T12:00:00.000Z'))
    sessionStorage.setItem(
      'worklens-session',
      JSON.stringify({
        token: 'employee-token',
        username: 'employee.alice',
        displayName: 'Alice',
        role: 'EMPLOYEE',
      }),
    )
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads live usage as app cards for today', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input)
        expect(init?.headers).toMatchObject({
          Authorization: 'Bearer employee-token',
        })

        if (url.endsWith('/api/usage-records/view?date=2026-07-08&page=1&pageSize=10')) {
          return jsonResponse({
            mode: 'LIVE_USAGE',
            date: '2026-07-08',
            page: 1,
            pageSize: 10,
            totalApps: 2,
            items: [
              {
                appName: 'Chrome',
                durationSeconds: 4500,
                segments: [
                  { startedAt: '2026-07-08T09:00:00', endedAt: '2026-07-08T10:00:00' },
                  { startedAt: '2026-07-08T11:00:00', endedAt: '2026-07-08T11:15:00' },
                ],
              },
              {
                appName: 'Slack',
                durationSeconds: 1800,
                segments: [{ startedAt: '2026-07-08T10:00:00', endedAt: '2026-07-08T10:30:00' }],
              },
            ],
          })
        }

        if (url.endsWith('/api/llm/employee-report-history')) {
          return jsonResponse([])
        }

        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = await mountEmployeeHome()
    await flushPromises()

    expect(wrapper.findAll('[data-test="usage-app-card"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('Chrome')
    expect(wrapper.text()).toContain('75 min')
    expect(wrapper.text()).toContain('09:00 - 10:00')
    expect(wrapper.text()).toContain('Slack')
    expect(wrapper.find('[data-test="usage-report"]').exists()).toBe(false)
  })

  it('pages live usage app cards through the backend', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)

      if (url.endsWith('/api/usage-records/view?date=2026-07-08&page=1&pageSize=10')) {
        return jsonResponse({
          mode: 'LIVE_USAGE',
          date: '2026-07-08',
          page: 1,
          pageSize: 10,
          totalApps: 12,
          items: Array.from({ length: 10 }, (_, index) => ({
            appName: `App ${index + 1}`,
            durationSeconds: 60,
            segments: [{ startedAt: '2026-07-08T09:00:00', endedAt: '2026-07-08T09:01:00' }],
          })),
        })
      }

      if (url.endsWith('/api/usage-records/view?date=2026-07-08&page=2&pageSize=10')) {
        return jsonResponse({
          mode: 'LIVE_USAGE',
          date: '2026-07-08',
          page: 2,
          pageSize: 10,
          totalApps: 12,
          items: [
            {
              appName: 'App 11',
              durationSeconds: 60,
              segments: [{ startedAt: '2026-07-08T10:00:00', endedAt: '2026-07-08T10:01:00' }],
            },
            {
              appName: 'App 12',
              durationSeconds: 60,
              segments: [{ startedAt: '2026-07-08T10:02:00', endedAt: '2026-07-08T10:03:00' }],
            },
          ],
        })
      }

      if (url.endsWith('/api/llm/employee-report-history')) {
        return jsonResponse([])
      }

      return new Response(null, { status: 404 })
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountEmployeeHome()
    await flushPromises()

    expect(wrapper.findAll('[data-test="usage-app-card"]')).toHaveLength(10)
    let appNames = wrapper.findAll('[data-test="usage-app-card"] strong').map((node) => node.text())
    expect(appNames).toContain('App 10')
    expect(appNames).not.toContain('App 11')

    await wrapper.get('[data-test="usage-next-page"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-test="usage-app-card"]')).toHaveLength(2)
    appNames = wrapper.findAll('[data-test="usage-app-card"] strong').map((node) => node.text())
    expect(appNames).toEqual(['App 11', 'App 12'])
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/usage-records/view?date=2026-07-08&page=2&pageSize=10'),
      expect.any(Object),
    )
  })

  it('shows a rolled report when raw usage has been archived', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)

        if (url.endsWith('/api/usage-records/view?date=2026-07-08&page=1&pageSize=10')) {
          return jsonResponse({
            mode: 'REPORT',
            date: '2026-07-08',
            report: {
              reportScope: 'EMPLOYEE',
              periodType: 'WEEKLY',
              periodStartDate: '2026-07-06',
              periodEndDate: '2026-07-12',
              summary: 'This week had steady focus blocks.',
              details: [
                { appName: 'Chrome', durationSeconds: 5400, durationMinutes: 90, ratio: 0.75 },
                { appName: 'Slack', durationSeconds: 1800, durationMinutes: 30, ratio: 0.25 },
              ],
            },
          })
        }

        if (url.endsWith('/api/llm/employee-report-history')) {
          return jsonResponse([])
        }

        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = await mountEmployeeHome()
    await flushPromises()

    expect(wrapper.find('[data-test="usage-report"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('WEEKLY')
    expect(wrapper.text()).toContain('This week had steady focus blocks.')
    expect(wrapper.text()).toContain('Chrome')
    expect(wrapper.text()).toContain('75%')
    expect(wrapper.find('[data-test="usage-app-card"]').exists()).toBe(false)
  })

  it('ignores stale responses when the date changes quickly', async () => {
    const pendingResolvers: Array<(response: Response) => void> = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)

        if (url.endsWith('/api/usage-records/view?date=2026-07-08&page=1&pageSize=10')) {
          return jsonResponse({
            mode: 'LIVE_USAGE',
            date: '2026-07-08',
            page: 1,
            pageSize: 10,
            totalApps: 1,
            items: [{ appName: 'Old App', durationSeconds: 60, segments: [] }],
          })
        }

        if (url.endsWith('/api/usage-records/view?date=2026-07-09&page=1&pageSize=10')) {
          return new Promise<Response>((resolve) => {
            pendingResolvers.push(resolve)
          })
        }

        if (url.endsWith('/api/llm/employee-report-history')) {
          return jsonResponse([])
        }

        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = await mountEmployeeHome()
    await flushPromises()

    const dateInput = wrapper.get('input[type="date"]')
    const loadUsageView = (wrapper.vm as { loadUsageView: (date?: string) => Promise<void> }).loadUsageView

    void loadUsageView('2026-07-09')
    await flushPromises()
    expect((dateInput.element as HTMLInputElement).disabled).toBe(true)

    await loadUsageView('2026-07-08')
    await flushPromises()
    expect(wrapper.text()).toContain('Old App')

    pendingResolvers.forEach((resolve) => resolve(jsonResponse({
      mode: 'LIVE_USAGE',
      date: '2026-07-09',
      page: 1,
      pageSize: 10,
      totalApps: 1,
      items: [{ appName: 'Stale App', durationSeconds: 60, segments: [] }],
    })))
    await flushPromises()

    expect(wrapper.text()).toContain('Old App')
    expect(wrapper.text()).not.toContain('Stale App')
  })

  it('does not expose manual employee report generation', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'

      if (url.endsWith('/api/usage-records/view?date=2026-07-08&page=1&pageSize=10') && method === 'GET') {
        return jsonResponse({
          mode: 'LIVE_USAGE',
          date: '2026-07-08',
          page: 1,
          pageSize: 10,
          totalApps: 0,
          items: [],
        })
      }

      if (url.endsWith('/api/llm/employee-report-history') && method === 'GET') {
        return jsonResponse([])
      }

      return new Response(null, { status: 404 })
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = await mountEmployeeHome()
    await flushPromises()

    expect(wrapper.find('[data-test="generate-employee-report"]').exists()).toBe(false)
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining('/api/llm/employee-report'),
      expect.objectContaining({ method: 'POST' }),
    )
  })
})

async function mountEmployeeHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/employee', component: EmployeeHomeView },
      { path: '/employee/access-records', component: { template: '<div />' } },
    ],
  })

  router.push('/employee')
  await router.isReady()

  return mount(EmployeeHomeView, {
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

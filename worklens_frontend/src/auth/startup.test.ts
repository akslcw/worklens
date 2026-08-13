import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SESSION_STORAGE_KEY } from './session'
import { validateStoredSession } from './startup'

describe('startup session validation', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('refreshes the stored session with server-authoritative role and flags', async () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ token: 'stored-token', username: 'E001', role: 'MANAGER' }),
    )
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      JSON.stringify({
        employeeId: 1,
        username: 'E001',
        displayName: 'Alice',
        role: 'EMPLOYEE',
        mustChangePassword: true,
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )))

    await validateStoredSession()

    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY) ?? '{}')).toMatchObject({
      token: 'stored-token',
      username: 'E001',
      displayName: 'Alice',
      role: 'EMPLOYEE',
      mustChangePassword: true,
    })
  })

  it('clears the session when the server rejects the stored token', async () => {
    sessionStorage.setItem(
      SESSION_STORAGE_KEY,
      JSON.stringify({ token: 'expired-token', username: 'E001', role: 'MANAGER' }),
    )
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      JSON.stringify({ message: 'Authentication required' }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    )))

    await validateStoredSession()

    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('does nothing when no session is stored', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await validateStoredSession()

    expect(fetchMock).not.toHaveBeenCalled()
  })
})

import type { RouteRole } from './types'

export const SESSION_STORAGE_KEY = 'worklens-session'

export type AuthSession = {
  token: string
  username: string
  displayName?: string
  role: RouteRole
  mustChangePassword?: boolean
}

export function readStoredSession() {
  const raw = sessionStorage.getItem(SESSION_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthSession
  } catch {
    sessionStorage.removeItem(SESSION_STORAGE_KEY)
    return null
  }
}

/** One-time cleanup of the legacy localStorage token storage (moved to
 * sessionStorage). Call once at startup, not on every read. */
export function removeLegacyLocalSession() {
  localStorage.removeItem(SESSION_STORAGE_KEY)
}

export function persistSession(session: AuthSession) {
  localStorage.removeItem(SESSION_STORAGE_KEY)
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearSession() {
  sessionStorage.removeItem(SESSION_STORAGE_KEY)
  localStorage.removeItem(SESSION_STORAGE_KEY)
}

export function resolveHomePath(role: RouteRole) {
  return role === 'MANAGER' ? '/manager' : '/employee'
}

export function resolvePostLoginPath(session: AuthSession) {
  return session.mustChangePassword ? '/change-password' : resolveHomePath(session.role)
}

import { getCurrentUser } from '../api/auth'
import { clearSession, persistSession, readStoredSession } from './session'

/**
 * Validates the stored session against the server once at startup, so a
 * tampered sessionStorage role or an expired token cannot render protected
 * pages: the server's response is authoritative for role and flags, and any
 * failure clears the local session (the router then lands on /login).
 */
export async function validateStoredSession(): Promise<void> {
  const session = readStoredSession()
  if (!session?.token) {
    return
  }

  try {
    const currentUser = await getCurrentUser(session.token)
    persistSession({
      token: session.token,
      username: currentUser.username,
      displayName: currentUser.displayName,
      role: currentUser.role,
      mustChangePassword: currentUser.mustChangePassword,
    })
  } catch {
    clearSession()
  }
}

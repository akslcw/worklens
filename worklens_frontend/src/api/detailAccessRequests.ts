import { request } from './http'
import type { UsageView } from './usageRecords'

export type DetailAccessRequest = {
  id: number
  requesterEmployeeId: number
  targetEmployeeId: number
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'USED'
  createdAt: string
  processedAt: string | null
  processedByEmployeeId: number | null
}

export type EmployeeDetailAccessRequest = {
  id: number
  requesterEmployeeId: number
  requesterEmployeeName: string | null
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'USED'
  createdAt: string
  processedAt: string | null
  hasBeenViewed: boolean
  viewedAt: string | null
}

export type CreateDetailAccessRequestPayload = {
  targetEmployeeId: number
  reason: string
}

export async function listOwnDetailAccessRequests(token: string) {
  return request<DetailAccessRequest[]>('/detail-access-requests', { method: 'GET' }, token)
}

export async function createDetailAccessRequest(payload: CreateDetailAccessRequestPayload, token: string) {
  return request<DetailAccessRequest>(
    '/detail-access-requests',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    token,
  )
}

export async function viewApprovedUsageView(requestId: number, token: string, date: string) {
  const query = new URLSearchParams({
    date,
    page: '1',
    pageSize: '10',
  })
  return request<UsageView>(`/detail-access-requests/${requestId}/usage-view?${query.toString()}`, { method: 'GET' }, token)
}

export async function listRequestsTargetingCurrentEmployee(token: string) {
  return request<EmployeeDetailAccessRequest[]>('/detail-access-requests/targeting-me', { method: 'GET' }, token)
}

export async function decideDetailAccessRequest(requestId: number, decision: 'APPROVED' | 'REJECTED', token: string) {
  return request<DetailAccessRequest>(
    `/detail-access-requests/${requestId}/decision`,
    {
      method: 'PATCH',
      body: JSON.stringify({ decision }),
    },
    token,
  )
}

import { request } from './http'

export type ReportHistoryItem = {
  reportType: string
  periodType: string | null
  summary: string
  periodStartedAt: string | null
  periodEndedAt: string | null
  periodStartDate: string | null
  periodEndDate: string | null
  createdAt: string
}

export async function getTeamReportHistory(token: string) {
  return request<ReportHistoryItem[]>('/llm/team-report-history', { method: 'GET' }, token)
}

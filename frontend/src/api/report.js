import request from './request'

export const getReports = () => request.get('/api/reports')
export const getReportByEmployee = (employeeId) => request.get(`/api/reports/${employeeId}`)
export const triggerAnalysis = (employeeId, date) =>
    request.post(`/api/analysis/trigger/${employeeId}?date=${date}`)
import request from './request'

export const getRecordsByRange = (employeeId, startDate, endDate) =>
    request.get(`/api/records/${employeeId}/range`, { params: { startDate, endDate } })
import request from './request'

export const getEmployees = () => request.get('/api/employees')
export const addEmployee = (data) => request.post('/api/employees', data)
export const deleteEmployee = (id) => request.delete(`/api/employees/${id}`)
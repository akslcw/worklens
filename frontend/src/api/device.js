import request from './request'

export const getDevices = () => request.get('/api/devices')
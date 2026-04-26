import request from './request'

export const getCategories = () => request.get('/api/categories')
export const addCategory = (data) => request.post('/api/categories', data)
export const updateCategory = (data) => request.put('/api/categories', data)
export const deleteCategory = (id) => request.delete(`/api/categories/${id}`)
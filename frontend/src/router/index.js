import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            redirect: '/dashboard'
        },
        {
            path: '/dashboard',
            name: 'dashboard',
            component: () => import('../views/DashboardView.vue')
        },
        {
            path: '/employees',
            name: 'employees',
            component: () => import('../views/EmployeeView.vue')
        },
        {
            path: '/reports',
            name: 'reports',
            component: () => import('../views/ReportView.vue')
        }
    ]
})

export default router
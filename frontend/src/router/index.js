import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/login',
            name: 'login',
            component: () => import('../views/LoginView.vue'),
            meta: { requiresAuth: false }
        },
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
            path: '/employees/:id',
            name: 'employeeDetail',
            component: () => import('../views/EmployeeDetailView.vue')
        },
        {
            path: '/reports',
            name: 'reports',
            component: () => import('../views/ReportView.vue')
        },
        {
            path: '/categories',
            name: 'categories',
            component: () => import('../views/CategoryView.vue')
        }
    ]
})

router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    if (to.name !== 'login' && !token) {
        next({ name: 'login' })
    } else {
        next()
    }
})

export default router
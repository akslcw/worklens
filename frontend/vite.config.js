import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => ({
    plugins: [
        vue(),
        ...(mode === 'development'
            ? [require('vite-plugin-vue-devtools')()]
            : [])
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url))
        }
    }
}))
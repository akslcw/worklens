import { createApp } from 'vue'
import App from './App.vue'
import { createAppRouter } from './router'
import { setUnauthorizedHandler } from './api/http'
import { removeLegacyLocalSession } from './auth/session'
import { validateStoredSession } from './auth/startup'
import './style.css'

async function bootstrap() {
  removeLegacyLocalSession()

  const app = createApp(App)
  const router = createAppRouter()
  setUnauthorizedHandler(() => {
    void router.replace('/login')
  })

  await validateStoredSession()

  app.use(router)
  app.mount('#app')
}

void bootstrap()


import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// Import design system styles
import './styles/design-system.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// Initialize theme system after app is created
app.mount('#app')

// Initialize theme after mounting
import { useThemeStore } from './stores/theme'
const themeStore = useThemeStore()
themeStore.initialize()

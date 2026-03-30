import { createApp } from 'vue'
import { setCustomComponents } from 'markstream-vue'
import App from './App.vue'
import 'markstream-vue/index.css'
import './style.css'

// 注册 Thinking 自定义组件
import ThinkingNode from './components/ThinkingNode.vue'
setCustomComponents('chatui', { thinking: ThinkingNode })

createApp(App).mount('#app')

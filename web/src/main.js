import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/dist/index.css'
import {
	ChatDotRound,
	Close,
	DArrowRight,
	Delete,
	Document,
	FullScreen,
	Iphone,
	Lock,
	Microphone,
	Minus,
	Monitor,
	More,
	Phone,
	Picture,
	Plus,
	Position,
	Promotion,
	RefreshLeft,
	Search,
	User,
	VideoCamera,
	Wallet,
	Warning
} from '@element-plus/icons-vue'

import App from './App'
import router from './router'
import './assets/style/yeying.scss'
import './assets/iconfont/iconfont.css'
import httpRequest from './api/httpRequest'
import * as socketApi from './api/wssocket'
import * as messageType from './api/messageType'
import emotion from './api/emotion.js'
import url from './api/url.js'
import str from './api/str.js'
import element from './api/element.js'
import * as enums from './api/enums.js'
import * as date from './api/date.js'
import eventBus from './api/eventBus.js'
import registerDialogDrag from './utils/directive/dialogDrag'
import useChatStore from './store/chatStore.js'
import useFriendStore from './store/friendStore.js'
import useGroupStore from './store/groupStore.js'
import useUserStore from './store/userStore.js'
import useConfigStore from './store/configStore.js'

const app = createApp(App)
const pinia = createPinia()

app.use(router)
app.use(pinia)
app.use(ElementPlus)

const iconComponents = {
	ChatDotRound,
	Close,
	DArrowRight,
	Delete,
	Document,
	FullScreen,
	Iphone,
	Lock,
	Microphone,
	Minus,
	Monitor,
	More,
	Phone,
	Picture,
	Plus,
	Position,
	Promotion,
	RefreshLeft,
	Search,
	User,
	VideoCamera,
	Wallet,
	Warning
}

for (const [key, component] of Object.entries(iconComponents)) {
	app.component(key, component)
}

registerDialogDrag(app)

// 挂载全局
app.config.globalProperties.$wsApi = socketApi
app.config.globalProperties.$msgType = messageType
app.config.globalProperties.$date = date
app.config.globalProperties.$http = httpRequest // http请求方法
app.config.globalProperties.$emo = emotion // emo表情
app.config.globalProperties.$url = url // url转换
app.config.globalProperties.$str = str // 字符串相关
app.config.globalProperties.$elm = element // 元素操作
app.config.globalProperties.$enums = enums // 枚举
app.config.globalProperties.$eventBus = eventBus // 全局事件
app.config.globalProperties.$message = ElMessage
app.config.globalProperties.$msgbox = ElMessageBox
app.config.globalProperties.$alert = ElMessageBox.alert
app.config.globalProperties.$confirm = ElMessageBox.confirm
app.config.globalProperties.$prompt = ElMessageBox.prompt
app.config.globalProperties.$loading = ElLoading.service

// 挂载全局的pinia
app.config.globalProperties.chatStore = useChatStore(pinia)
app.config.globalProperties.friendStore = useFriendStore(pinia)
app.config.globalProperties.groupStore = useGroupStore(pinia)
app.config.globalProperties.userStore = useUserStore(pinia)
app.config.globalProperties.configStore = useConfigStore(pinia)

app.mount('#app')

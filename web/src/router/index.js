import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    name: 'Login',
    path: '/login',
    component: () => import('../view/Login')
  },
  {
    name: 'Register',
    path: '/register',
    component: () => import('../view/Register')
  },
  {
    name: 'Home',
    path: '/home',
    component: () => import('../view/Home'),
    children: [
      {
        name: 'Chat',
        path: '/home/chat',
        component: () => import('../view/Chat')
      },
      {
        name: 'Friend',
        path: '/home/friend',
        component: () => import('../view/Friend')
      },
      {
        name: 'GROUP',
        path: '/home/group',
        component: () => import('../view/Group')
      }
    ]
  }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})

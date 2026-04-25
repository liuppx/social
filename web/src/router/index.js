import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../view/Login'
import Register from '../view/Register'
import Home from '../view/Home'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    name: 'Login',
    path: '/login',
    component: Login
  },
  {
    name: 'Register',
    path: '/register',
    component: Register
  },
  {
    name: 'Home',
    path: '/home',
    component: Home,
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

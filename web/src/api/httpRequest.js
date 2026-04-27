import axios from 'axios'

import { ElMessage } from 'element-plus'

const http = axios.create({
	baseURL: process.env.VUE_APP_BASE_API,
	timeout: 1000 * 30,
	withCredentials: true
})

/**
 * 请求拦截
 */
http.interceptors.request.use(config => {
	let accessToken = sessionStorage.getItem("accessToken");
	if (accessToken) {
		config.headers.accessToken = encodeURIComponent(accessToken);
	}
	return config
}, error => {
	return Promise.reject(error)
})

/**
 * 响应拦截
 */
http.interceptors.response.use(async response => {
	if (response.data.code == 200) {
		return response.data.data;
	} else if (response.data.code == 400) {
		location.href = "/";
	} else if (response.data.code == 401) {
		console.log("token失效，尝试重新获取")
		let refreshToken = sessionStorage.getItem("refreshToken");
		if (!refreshToken) {
			location.href = "/";
		}
		// 发送请求, 进行刷新token操作, 获取新的token
		const data = await http({
			method: 'put',
			url: '/refreshToken',
			headers: {
				refreshToken: refreshToken
			}
		}).catch(() => {
			location.href = "/";
		})
		// 保存token
		sessionStorage.setItem("accessToken", data.accessToken);
		sessionStorage.setItem("refreshToken", data.refreshToken);
		// 重新发送刚才的请求
		return http(response.config)
	} else {
		const message = response.data.message || '请求失败';
		ElMessage({
			message,
			type: 'error',
			duration: 1500,
			customClass: 'element-error-message-zindex'
		})
		return Promise.reject(response.data)
	}
}, error => {
	const { response } = error || {};
	if (!response) {
		ElMessage({
			message: '网络异常或服务不可用，请检查服务是否启动',
			type: 'error',
			duration: 2000,
			customClass: 'element-error-message-zindex'
		})
		return Promise.reject(error)
	}

	const { status, data } = response;
	switch (status) {
		case 400:
			ElMessage({
				message: data && (data.message || data) ? (data.message || data) : '请求参数错误',
				type: 'error',
				duration: 1500,
				customClass: 'element-error-message-zindex'
			})
			break
		case 401:
			location.href = "/";
			break
		case 405:
			ElMessage({
				message: 'http请求方式有误',
				type: 'error',
				duration: 1500,
				customClass: 'element-error-message-zindex'
			})
			break
		case 404:
		case 500: {
			// 后端返回了业务错误信息时，优先展示后端提示
			const serverMessage = data && (data.message || data.msg);
			ElMessage({
				message: serverMessage || '服务器出了点小差，请稍后再试',
				type: 'error',
				duration: 1500,
				customClass: 'element-error-message-zindex'
			})
			break
		}
		case 501:
			ElMessage({
				message: '服务器不支持当前请求所需要的某个功能',
				type: 'error',
				duration: 1500,
				customClass: 'element-error-message-zindex'
			})
			break
		default:
			ElMessage({
				message: '请求失败，请稍后重试',
				type: 'error',
				duration: 1500,
				customClass: 'element-error-message-zindex'
			})
			break
	}

	return Promise.reject(error)
})


export default http

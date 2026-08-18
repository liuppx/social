import http from './httpRequest'

export const createPassportLoginSession = () => http({
	url: '/passport/login/session',
	method: 'post'
})

export const getPassportLoginStatus = (sessionId) => http({
	url: '/passport/login/status',
	method: 'get',
	params: { sessionId }
})

import http from './httpRequest'

const rtcHttpRequest = (config) => {
	return http({
		...config,
		baseURL: process.env.VUE_APP_RTC_BASE_API
	})
}

export default rtcHttpRequest

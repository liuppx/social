import http from './httpRequest'

export function rewriteMessage(data) {
	return http({
		url: '/ai/rewrite',
		method: 'post',
		data
	})
}

export function suggestReplies(data) {
	return http({
		url: '/ai/reply/suggest',
		method: 'post',
		data
	})
}

export function summarizeChat(data) {
	return http({
		url: '/ai/summary',
		method: 'post',
		data
	})
}

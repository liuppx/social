import rtcHttp from './rtcHttpRequest.js'

class RtcGroupApi { }

RtcGroupApi.prototype.setup = function (groupId, userInfos) {
	let formData = {
		groupId,
		userInfos
	}
	return rtcHttp({
		url: '/webrtc/group/setup',
		method: 'post',
		data: formData
	})
}

RtcGroupApi.prototype.accept = function (groupId) {
	return rtcHttp({
		url: '/webrtc/group/accept?groupId=' + groupId,
		method: 'post'
	})
}

RtcGroupApi.prototype.reject = function (groupId) {
	return rtcHttp({
		url: '/webrtc/group/reject?groupId=' + groupId,
		method: 'post'
	})
}

RtcGroupApi.prototype.failed = function (groupId, reason) {
	let formData = {
		groupId,
		reason
	}
	return rtcHttp({
		url: '/webrtc/group/failed',
		method: 'post',
		data: formData
	})
}


RtcGroupApi.prototype.join = function (groupId) {
	return rtcHttp({
		url: '/webrtc/group/join?groupId=' + groupId,
		method: 'post'
	})
}

RtcGroupApi.prototype.invite = function (groupId, userInfos) {
	let formData = {
		groupId,
		userInfos
	}
	return rtcHttp({
		url: '/webrtc/group/invite',
		method: 'post',
		data: formData
	})
}


RtcGroupApi.prototype.offer = function (groupId, userId, offer) {
	let formData = {
		groupId,
		userId,
		offer
	}
	return rtcHttp({
		url: '/webrtc/group/offer',
		method: 'post',
		data: formData
	})
}

RtcGroupApi.prototype.answer = function (groupId, userId, answer) {
	let formData = {
		groupId,
		userId,
		answer
	}
	return rtcHttp({
		url: '/webrtc/group/answer',
		method: 'post',
		data: formData
	})
}

RtcGroupApi.prototype.quit = function (groupId) {
	return rtcHttp({
		url: '/webrtc/group/quit?groupId=' + groupId,
		method: 'post'
	})
}

RtcGroupApi.prototype.cancel = function (groupId) {
	return rtcHttp({
		url: '/webrtc/group/cancel?groupId=' + groupId,
		method: 'post'
	})
}

RtcGroupApi.prototype.candidate = function (groupId, userId, candidate) {
	let formData = {
		groupId,
		userId,
		candidate
	}
	return rtcHttp({
		url: '/webrtc/group/candidate',
		method: 'post',
		data: formData
	})
}

RtcGroupApi.prototype.device = function (groupId, isCamera, isMicroPhone) {
	let formData = {
		groupId,
		isCamera,
		isMicroPhone
	}
	return rtcHttp({
		url: '/webrtc/group/device',
		method: 'post',
		data: formData
	})
}

RtcGroupApi.prototype.heartbeat = function (groupId) {
	return rtcHttp({
		url: '/webrtc/group/heartbeat?groupId=' + groupId,
		method: 'post'
	})
}

export default RtcGroupApi;

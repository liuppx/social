import rtcHttp from './rtcHttpRequest.js'

class RtcPrivateApi {
}

RtcPrivateApi.prototype.call = function (uid, mode, offer) {
	return rtcHttp({
		url: `/webrtc/private/call?uid=${uid}&mode=${mode}`,
		method: 'post',
		data: JSON.stringify(offer),
		headers: {
			'Content-Type': 'application/json; charset=utf-8'
		}
	})
}

RtcPrivateApi.prototype.accept = function (uid, answer) {
	return rtcHttp({
		url: `/webrtc/private/accept?uid=${uid}`,
		method: 'post',
		data: JSON.stringify(answer),
		headers: {
			'Content-Type': 'application/json; charset=utf-8'
		}
	})
}


RtcPrivateApi.prototype.handup = function (uid) {
	return rtcHttp({
		url: `/webrtc/private/handup?uid=${uid}`,
		method: 'post'
	})
}

RtcPrivateApi.prototype.cancel = function (uid) {
	return rtcHttp({
		url: `/webrtc/private/cancel?uid=${uid}`,
		method: 'post'
	})
}

RtcPrivateApi.prototype.reject = function (uid) {
	return rtcHttp({
		url: `/webrtc/private/reject?uid=${uid}`,
		method: 'post'
	})
}

RtcPrivateApi.prototype.failed = function (uid, reason) {
	return rtcHttp({
		url: `/webrtc/private/failed?uid=${uid}&reason=${reason}`,
		method: 'post'
	})
}

RtcPrivateApi.prototype.sendCandidate = function (uid, candidate) {
	return rtcHttp({
		url: `/webrtc/private/candidate?uid=${uid}`,
		method: 'post',
		data: JSON.stringify(candidate),
		headers: {
			'Content-Type': 'application/json; charset=utf-8'
		}
	});
}

RtcPrivateApi.prototype.heartbeat = function (uid) {
	return rtcHttp({
		url: `/webrtc/private/heartbeat?uid=${uid}`,
		method: 'post'
	})
}

export default RtcPrivateApi;

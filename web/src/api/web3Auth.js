let web3LibPromise = null

const loadWeb3Lib = async () => {
	if (!web3LibPromise) {
		web3LibPromise = import('@yeying-community/web3-bs')
	}
	return web3LibPromise
}

const DEFAULT_PROVIDER_TIMEOUT = 3000

const resolveAuthBaseUrl = () => {
	const base = process.env.VUE_APP_WEB3_BASE_API || ''
	return `${base}/api/v1/public/auth`
}

const extractRefreshToken = (payload) => {
	if (!payload || typeof payload !== 'object') return null
	const envelope = payload.data || {}
	if (typeof envelope.refreshToken === 'string') return envelope.refreshToken
	if (typeof payload.refreshToken === 'string') return payload.refreshToken
	return null
}

const toErrorCode = (error) => {
	if (!error || typeof error !== 'object') return null
	if (typeof error.code === 'number' || typeof error.code === 'string') return error.code
	return null
}

const toErrorMessage = (error) => {
	if (!error) return ''
	if (typeof error === 'string') return error
	if (typeof error.message === 'string') return error.message
	return ''
}

const isUserRejectedError = (error) => {
	const code = toErrorCode(error)
	if (code === 4001 || code === '4001') return true
	return /rejected|denied|用户取消|用户拒绝|cancel/i.test(toErrorMessage(error))
}

const isRequestPendingError = (error) => {
	const code = toErrorCode(error)
	if (code === -32002 || code === '-32002') return true
	return /already processing|pending/i.test(toErrorMessage(error))
}

const normalizeWalletError = (error) => {
	if (isUserRejectedError(error)) {
		return new Error('你取消了钱包授权，请在钱包弹窗中确认连接后重试')
	}
	if (isRequestPendingError(error)) {
		return new Error('钱包授权请求正在处理中，请先在钱包弹窗中完成或取消后再试')
	}
	const message = toErrorMessage(error)
	if (/failed to connect to metamask/i.test(message)) {
		return new Error('当前钱包插件连接 MetaMask 失败，请先解锁 MetaMask 并确认站点授权')
	}
	if (error instanceof Error) {
		return error
	}
	return new Error(message || '钱包登录失败')
}

const shouldFallbackProvider = (error, provider, preferYeYing, isYeYingProviderFn) => {
	if (!preferYeYing) return false
	if (!isYeYingProviderFn(provider)) return false
	if (isUserRejectedError(error) || isRequestPendingError(error)) return false
	return true
}

const loginWithProvider = async (provider, options = {}) => {
	const {
		requestAccounts,
		signMessage,
		requestPassportAssertion
	} = await loadWeb3Lib()

	const baseUrl = options.baseUrl || resolveAuthBaseUrl()
	const accounts = await requestAccounts({ provider })
	const address = accounts[0]
	if (!address) throw new Error('未获取到钱包账户')
	const challengeResponse = await fetch(`${baseUrl}/challenge`, {
		method: 'POST', headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ address })
	})
	const challengePayload = await challengeResponse.json()
	const loginRequest = challengePayload && challengePayload.data
	if (!challengeResponse.ok || !loginRequest || !loginRequest.challenge) throw new Error((challengePayload && challengePayload.message) || '无法创建钱包登录请求')
	const signature = await signMessage({ provider, address, message: loginRequest.challenge })
	const passportLogin = await requestPassportAssertion({
		provider, appId: loginRequest.appId, audience: loginRequest.audience, nonce: loginRequest.nonce,
		scopes: loginRequest.scope, passportEndpoint: loginRequest.passportEndpoint, ensureConnected: false
	})
	const verifyResponse = await fetch(`${baseUrl}/verify`, {
		method: 'POST', headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ address, signature, nonce: loginRequest.nonce, passportAssertion: passportLogin.passportAssertion, walletProof: passportLogin.walletProof })
	})
	const verifyPayload = await verifyResponse.json()
	const result = verifyPayload && verifyPayload.data
	if (!verifyResponse.ok || !result || !result.token) throw new Error((verifyPayload && verifyPayload.message) || '钱包登录失败')

	return {
		accessToken: result.token,
		refreshToken: extractRefreshToken(result),
		address: result.address || address
	}
}

export async function walletLogin(options = {}) {
	const { getProvider, isYeYingProvider } = await loadWeb3Lib()
	const timeoutMs = options.timeoutMs || DEFAULT_PROVIDER_TIMEOUT
	const preferYeYing = options.preferYeYing !== false
	const provider = await getProvider({ timeoutMs, preferYeYing })
	if (!provider) {
		throw new Error('未检测到钱包插件')
	}

	try {
		return await loginWithProvider(provider, options)
	} catch (error) {
		if (!shouldFallbackProvider(error, provider, preferYeYing, isYeYingProvider)) {
			throw normalizeWalletError(error)
		}

		const fallbackProvider = await getProvider({ timeoutMs, preferYeYing: false })
		if (!fallbackProvider || fallbackProvider === provider) {
			throw normalizeWalletError(error)
		}

		try {
			return await loginWithProvider(fallbackProvider, options)
		} catch (fallbackError) {
			throw normalizeWalletError(fallbackError)
		}
	}
}

export async function fetchWeb3Profile(ucan, options = {}) {
	const { authUcanFetch } = await loadWeb3Lib()
	if (!ucan) {
		throw new Error('缺少 UCAN Token')
	}
	const baseUrl = options.baseUrl || resolveAuthBaseUrl()
	const profileUrl = `${baseUrl}/profile`
	const response = await authUcanFetch(profileUrl, { method: 'GET' }, { ucan })
	return response.json()
}

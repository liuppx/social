import {
	getProvider,
	loginWithChallenge,
	createUcanSession,
	getOrCreateUcanRoot,
	createInvocationUcan,
	authUcanFetch
} from '@yeying-community/web3-bs'

const DEFAULT_CAPABILITIES = [{ resource: 'profile', action: 'read' }]

const resolveAuthBaseUrl = () => {
	const base = process.env.VUE_APP_WEB3_BASE_API || ''
	return `${base}/api/v1/public/auth`
}

const resolveAudience = () => {
	return process.env.VUE_APP_WEB3_AUDIENCE || (typeof window !== 'undefined' ? `did:web:${window.location.host}` : '')
}

const extractRefreshToken = (payload) => {
	if (!payload || typeof payload !== 'object') return null
	const envelope = payload.data || {}
	if (typeof envelope.refreshToken === 'string') return envelope.refreshToken
	if (typeof payload.refreshToken === 'string') return payload.refreshToken
	return null
}

export async function walletLogin(options = {}) {
	const provider = await getProvider({ timeoutMs: 3000, preferYeYing: true })
	if (!provider) {
		throw new Error('未检测到钱包插件')
	}

	const baseUrl = options.baseUrl || resolveAuthBaseUrl()
	const login = await loginWithChallenge({
		provider,
		baseUrl,
		storeToken: false
	})

	const refreshToken = extractRefreshToken(login.response)

	const capabilities = options.capabilities || DEFAULT_CAPABILITIES
	const session = await createUcanSession({ provider })
	const root = await getOrCreateUcanRoot({
		provider,
		session,
		capabilities
	})

	const audience = options.audience || resolveAudience()
	const ucan = await createInvocationUcan({
		issuer: session,
		audience,
		capabilities,
		proofs: [root]
	})

	return {
		accessToken: login.token,
		refreshToken,
		address: login.address,
		ucan,
		ucanSession: session,
		ucanRoot: root
	}
}

export async function fetchWeb3Profile(ucan, options = {}) {
	if (!ucan) {
		throw new Error('缺少 UCAN Token')
	}
	const baseUrl = options.baseUrl || resolveAuthBaseUrl()
	const profileUrl = `${baseUrl}/profile`
	const response = await authUcanFetch(profileUrl, { method: 'GET' }, { ucan })
	return response.json()
}

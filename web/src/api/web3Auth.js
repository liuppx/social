import { createWalletIdentityLoginSession, verifyWalletIdentityLogin } from './passportAuth'

let web3LibPromise = null

const loadWeb3Lib = async () => {
	if (!web3LibPromise) {
		web3LibPromise = import('@yeying-community/web3-bs')
	}
	return web3LibPromise
}

const DEFAULT_PROVIDER_TIMEOUT = 3000

const isUserRejectedError = (error) => {
	const code = error?.code
	if (code === 4001 || code === '4001') return true
	const msg = error?.message || ''
	return /rejected|denied|用户取消|用户拒绝|cancel/i.test(msg)
}

const isRequestPendingError = (error) => {
	const code = error?.code
	if (code === -32002 || code === '-32002') return true
	const msg = error?.message || ''
	return /already processing|pending/i.test(msg)
}

const normalizeWalletError = (error) => {
	if (isUserRejectedError(error)) {
		return new Error('你取消了钱包授权，请在钱包弹窗中确认连接后重试')
	}
	if (isRequestPendingError(error)) {
		return new Error('钱包授权请求正在处理中，请先在钱包弹窗中完成或取消后再试')
	}
	const msg = error?.message || ''
	if (/failed to connect to metamask/i.test(msg)) {
		return new Error('当前钱包插件连接 MetaMask 失败，请先解锁 MetaMask 并确认站点授权')
	}
	if (error instanceof Error) return error
	return new Error(msg || '钱包登录失败')
}

const shouldFallbackProvider = (error, provider, preferYeYing, isYeYingProviderFn) => {
	if (!preferYeYing) return false
	if (!isYeYingProviderFn(provider)) return false
	if (isUserRejectedError(error) || isRequestPendingError(error)) return false
	return true
}

export async function walletLogin(options = {}) {
	const { getProvider, isYeYingProvider, requestIdentityPresentation } = await loadWeb3Lib()
	const timeoutMs = options.timeoutMs || DEFAULT_PROVIDER_TIMEOUT
	const preferYeYing = options.preferYeYing !== false
	const provider = await getProvider({ timeoutMs, preferYeYing })
	if (!provider) {
		throw new Error('未检测到钱包插件')
	}

	const loginOnce = async (activeProvider) => {
		const accounts = await activeProvider.request({ method: 'eth_requestAccounts' })
		const address = accounts && accounts[0]
		if (!address) throw new Error('未获取到钱包账户')
		const chainId = await activeProvider.request({ method: 'eth_chainId' }).catch(() => null)
		const session = await createWalletIdentityLoginSession()
		const presentation = await requestIdentityPresentation({
			provider: activeProvider,
			appId: session.appId,
			audience: session.audience,
			nonce: session.nonce,
			scopes: session.scopes || ['identity.basic', 'identity.wallet', 'identity.email'],
			requestId: session.requestId,
			account: {
				chainKey: normalizeChainKey(chainId),
				address
			},
			ensureConnected: false
		})
		const data = await verifyWalletIdentityLogin({
			sessionId: session.sessionId,
			requestId: session.requestId,
			address,
			presentation
		})
		if (!data.accessToken) throw new Error('钱包登录失败')
		return {
			accessToken: data.accessToken,
			refreshToken: data.refreshToken || null,
			address,
			did: presentation.holder
		}
	}

	try {
		return await loginOnce(provider)
	} catch (error) {
		if (!shouldFallbackProvider(error, provider, preferYeYing, isYeYingProvider)) {
			throw normalizeWalletError(error)
		}
		const fallbackProvider = await getProvider({ timeoutMs, preferYeYing: false })
		if (!fallbackProvider || fallbackProvider === provider) {
			throw normalizeWalletError(error)
		}
		try {
			return await loginOnce(fallbackProvider)
		} catch (fallbackError) {
			throw normalizeWalletError(fallbackError)
		}
	}
}

function normalizeChainKey(chainId) {
	if (!chainId) return 'eip155:1'
	const value = String(chainId).startsWith('0x') ? Number.parseInt(chainId, 16).toString() : String(chainId)
	return `eip155:${value}`
}

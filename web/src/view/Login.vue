<template>
	<div class="page-login">
		<div class="login-body">
			<img class="login-logo" src="../../public/logo.png" alt="Yeying Social" />
			<el-form class="login-box" :model="loginForm" status-icon :rules="rules" ref="loginForm"
				@keyup.enter="loginMode === 'wallet' && emailLoginVisible && submitForm('loginForm')">
				<div class="login-mode-switch">
					<el-tooltip :content="loginMode === 'passport' ? '钱包身份登录' : '通行证登录'" placement="left">
						<div class="login-mode-switch-box" @click="switchLoginMode">
							<span class="login-mode-switch-icon">
								<svg v-if="loginMode === 'passport'" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M23 16a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h18a2 2 0 0 1 2 2v12ZM21 4H3v9h18V4ZM3 15v1h18v-1H3Zm3 6a1 1 0 0 1 1-1h10a1 1 0 1 1 0 2H7a1 1 0 0 1-1-1Z" fill="currentColor"></path></svg>
								<svg v-else viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M6.5 7.5a1 1 0 0 1 1-1h1a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1v-1Z" fill="currentColor"></path><path d="M4.5 2.5c-1.1 0-2 .9-2 2v7c0 1.1.9 2 2 2h7c1.1 0 2-.9 2-2v-7c0-1.1-.9-2-2-2h-7Zm0 2h7v7h-7v-7ZM11 16a1 1 0 1 1 2 0 1 1 0 0 1-2 0Zm0 3.5a1 1 0 1 1 2 0v1a1 1 0 1 1-2 0v-1Zm4-7.5a1 1 0 1 1 2 0 1 1 0 0 1-2 0Zm3.5 0a1 1 0 0 1 1-1h1a1 1 0 1 1 0 2h-1a1 1 0 0 1-1-1ZM15 17c0-1.1.9-2 2-2h2.5c1.1 0 2 .9 2 2v2.5c0 1.1-.9 2-2 2H17c-1.1 0-2-.9-2-2V17Zm4.5 0H17v2.5h2.5V17Zm-15-2c-1.1 0-2 .9-2 2v2.5c0 1.1.9 2 2 2H7c1.1 0 2-.9 2-2V17c0-1.1-.9-2-2-2H4.5Zm0 2H7v2.5H4.5V17ZM15 4.5c0-1.1.9-2 2-2h2.5c1.1 0 2 .9 2 2V7c0 1.1-.9 2-2 2H17c-1.1 0-2-.9-2-2V4.5Zm4.5 0H17V7h2.5V4.5Z" fill="currentColor"></path></svg>
							</span>
						</div>
					</el-tooltip>
				</div>
				<div class="login-title">{{ loginTitle }}</div>
				<div class="login-subtitle">{{ loginSubtitle }}</div>
				<el-form-item prop="terminal" v-show="false">
					<el-input type="text" v-model="loginForm.terminal" autocomplete="off"></el-input>
				</el-form-item>
				<transition name="login-mode" mode="out-in">
					<div v-if="loginMode === 'passport'" class="login-qrcode">
						<div class="login-qrcode-frame" @click="refreshPassportLogin">
							<img v-if="passportQrCode" class="login-qrcode-image" :src="passportQrCode" alt="通行证登录二维码" />
							<span v-else class="login-qrcode-loading">{{ passportLoading ? '二维码生成中...' : '点击刷新二维码' }}</span>
						</div>
						<div v-if="passportStatus" class="login-qrcode-status">{{ passportStatus }}</div>
						<el-button class="login-passport-local" text @click="openPassportAuthorize">无法扫码？使用本机通行证登录</el-button>
					</div>
					<div v-else class="login-access">
						<el-button class="wallet-login-button" type="primary" size="large" :loading="walletLoading" @click="walletSignIn">钱包登录</el-button>
					</div>
				</transition>
			</el-form>
		</div>
		<icp></icp>
	</div>
</template>

<script>
import Icp from '../components/common/Icp.vue'
import { createPassportLoginSession, getPassportLoginStatus } from '../api/passportAuth'
import { walletLogin } from '../api/web3Auth'
import QRCode from 'qrcode'
export default {
	name: "login",
	components: {
		Icp
	},
	data() {
		var checkEmail = (rule, value, callback) => {
			if (!value) {
				return callback(new Error('请输入邮箱'));
			}
			if (!/^\S+@\S+\.\S+$/.test(value)) return callback(new Error('邮箱格式不正确'))
			callback();
		};
		var checkPassword = (rule, value, callback) => {
			if (value === '') {
				callback(new Error('请输入密码'));
			}
			callback();

		};
		return {
			loginForm: {
				terminal: this.$enums.TERMINAL_TYPE.WEB,
				email: '',
				password: ''
			},
			rules: {
				email: [{
					validator: checkEmail,
					trigger: 'blur'
				}],
				password: [{
					validator: checkPassword,
					trigger: 'blur'
				}]
			},
			loginMode: 'wallet',
			passportLoading: false,
			walletLoading: false,
			emailLoginVisible: false,
			passportSession: null,
			passportQrCode: '',
			passportStatus: '',
			passportTimer: null,
			passportRequestSeq: 0,
			passportBroadcastChannel: null
		};
	},
	computed: {
		loginTitle() {
			return 'Social'
		},
		loginSubtitle() {
			return this.loginMode === 'passport'
				? '使用通行证完成身份验证后进入 Social。'
				: '使用夜莺钱包授权钱包身份，Social 将读取已验证邮箱后进入工作区。'
		}
	},
	methods: {
		switchLoginMode() {
			if (this.loginMode === 'passport') {
				this.loginMode = 'wallet'
				this.cancelPassportLogin()
				return
			}
			this.loginMode = 'passport'
			this.startPassportLogin()
		},
		async walletSignIn() {
			this.walletLoading = true
			try {
				const result = await walletLogin()
				sessionStorage.setItem('accessToken', result.accessToken)
				if (result.refreshToken) sessionStorage.setItem('refreshToken', result.refreshToken)
				if (result.ucan) sessionStorage.setItem('ucanToken', result.ucan)
				this.$message.success('钱包登录成功')
				this.$router.push('/home/chat')
			} catch (error) {
				this.$message.error(error && error.message ? error.message : '钱包登录失败')
			} finally {
				this.walletLoading = false
			}
		},
		toggleEmailLogin() {
			this.emailLoginVisible = !this.emailLoginVisible
		},
		async startPassportLogin() {
			this.cancelPassportLogin()
			const requestSeq = ++this.passportRequestSeq
			this.passportLoading = true
			try {
				const session = await createPassportLoginSession()
				if (requestSeq !== this.passportRequestSeq || this.loginMode !== 'passport') return
				if (!session.verifyUrl) throw new Error('通行证服务未返回确认地址')
				this.passportSession = session
				this.passportStatus = '请使用手机相机或夜莺钱包扫码确认'
				await this.renderPassportQrCode(session.verifyUrl)
				if (requestSeq !== this.passportRequestSeq || this.loginMode !== 'passport') return
				this.pollPassportStatus()
			} catch (error) {
				this.$message.error(error && error.message ? error.message : "无法发起通行证登录")
			} finally {
				if (requestSeq === this.passportRequestSeq) this.passportLoading = false
			}
		},
		async refreshPassportLogin() {
			if (this.passportLoading) return
			await this.startPassportLogin()
		},
		async renderPassportQrCode(verifyUrl) {
			this.passportQrCode = ''
			this.passportQrCode = await QRCode.toDataURL(verifyUrl, {
				width: 200,
				margin: 2,
				errorCorrectionLevel: 'M',
				color: {
					dark: '#202124',
					light: '#ffffff'
				}
			})
		},
		openPassportAuthorize() {
			if (!this.passportSession || !this.passportSession.verifyUrl) {
				this.refreshPassportLogin()
				return
			}
			window.open(this.passportSession.verifyUrl, '_blank')
		},
		async pollPassportStatus() {
			if (!this.passportSession) return
			const sessionId = this.passportSession.sessionId
			try {
				const result = await getPassportLoginStatus(sessionId)
				if (!this.passportSession || this.passportSession.sessionId !== sessionId) return
				if (result.status === 'approved' && result.login) {
					sessionStorage.setItem('accessToken', result.login.accessToken)
					sessionStorage.setItem('refreshToken', result.login.refreshToken)
					this.cancelPassportLogin()
					this.$message.success('通行证登录成功')
					this.$router.push('/home/chat')
					return
				}
				if (['expired', 'rejected', 'cancelled'].includes(result.status)) {
					this.cancelPassportLogin()
					this.$message.error(result.message || '通行证登录未完成，请重新发起')
					return
				}
				this.passportStatus = result.status === 'approved' || result.status === 'confirmed'
					? '已确认，正在登录…' : (result.message || '')
				this.passportTimer = window.setTimeout(() => this.pollPassportStatus(), 2000)
			} catch (error) {
				this.cancelPassportLogin()
				this.$message.error(error && error.message ? error.message : '通行证登录失败')
			}
		},
		cancelPassportLogin() {
			this.passportRequestSeq += 1
			if (this.passportTimer) window.clearTimeout(this.passportTimer)
			this.passportTimer = null
			this.passportSession = null
			this.passportQrCode = ''
			this.passportStatus = ''
			this.passportLoading = false
		},
		submitForm(formName) {
			this.$refs[formName].validate((valid) => {
				if (valid) {
						this.$http({
							url: "/login",
							method: 'post',
							data: this.loginForm
						})
							.then((data) => {
							// 保存token
							sessionStorage.setItem("accessToken", data.accessToken);
								sessionStorage.setItem("refreshToken", data.refreshToken);
								this.$message.success("登录成功");
								this.$router.push("/home/chat");
							}).catch(() => {
								// 错误提示由http拦截器统一处理，这里吞掉异常避免Uncaught
							})

					}
				});
			},
		resetForm(formName) {
			this.$refs[formName].resetFields();
		},
		bindPassportCallbackEvents() {
			window.addEventListener('storage', this.onPassportCallbackStorage)
			window.addEventListener('message', this.onPassportCallbackMessage)
			window.addEventListener('focus', this.checkPassportCallbackAfterReturn)
			document.addEventListener('visibilitychange', this.onPassportVisibilityChange)
			if ('BroadcastChannel' in window) {
				this.passportBroadcastChannel = new BroadcastChannel('social-passport-login')
				this.passportBroadcastChannel.onmessage = event => this.handlePassportCallbackEvent(event.data)
			}
		},
		unbindPassportCallbackEvents() {
			window.removeEventListener('storage', this.onPassportCallbackStorage)
			window.removeEventListener('message', this.onPassportCallbackMessage)
			window.removeEventListener('focus', this.checkPassportCallbackAfterReturn)
			document.removeEventListener('visibilitychange', this.onPassportVisibilityChange)
			if (this.passportBroadcastChannel) {
				this.passportBroadcastChannel.close()
				this.passportBroadcastChannel = null
			}
		},
		onPassportCallbackStorage(event) {
			if (event.key !== '__social_passport_callback__' || !event.newValue) return
			this.handlePassportCallbackEvent(this.parsePassportCallback(event.newValue))
		},
		onPassportCallbackMessage(event) {
			if (event.origin !== window.location.origin) return
			this.handlePassportCallbackEvent(this.parsePassportCallback(event.data))
		},
		onPassportVisibilityChange() {
			if (!document.hidden) this.checkPassportCallbackAfterReturn()
		},
		checkPassportCallbackAfterReturn() {
			this.handlePassportCallbackEvent(this.parsePassportCallback(window.localStorage.getItem('__social_passport_callback__')))
		},
		parsePassportCallback(value) {
			if (!value) return null
			if (typeof value === 'object') return value
			try {
				return JSON.parse(value)
			} catch (error) {
				return null
			}
		},
		handlePassportCallbackEvent(callback) {
			if (!callback || callback.action !== 'social-passport-callback') return
			if (!this.passportSession || callback.sessionId !== this.passportSession.sessionId) return
			if (this.passportTimer) window.clearTimeout(this.passportTimer)
			this.passportTimer = null
			this.passportStatus = '已确认，正在登录...'
			this.pollPassportStatus()
		},
	},
	mounted() {
		this.bindPassportCallbackEvents()
	},
	beforeUnmount() {
		this.unbindPassportCallbackEvents()
		this.cancelPassportLogin()
	}
}
</script>

<style scoped lang="scss">
.page-login {
	width: 100%;
	height: 100%;
	box-sizing: border-box;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #f8f8f8;
	color: #202124;

	.login-body {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 100%;
		max-height: 100%;
		padding: 32px 0;
		overflow: auto;

		.login-logo {
			flex-shrink: 0;
			width: 84px;
			height: 84px;
			object-fit: contain;
		}

		.login-box {
			flex-shrink: 0;
			position: relative;
			width: 400px;
			max-width: 90%;
			margin-top: 36px;
			border-radius: 12px;
			background-color: #ffffff;
			box-shadow: 0 0 10px #e6ecfa;
			overflow: hidden;

			.login-mode-switch {
				position: absolute;
				top: 4px;
				right: 4px;
				z-index: 2;
				border-radius: 8px;
				overflow: hidden;

				.login-mode-switch-box {
					width: 80px;
					height: 80px;
					transform: translate(40px, -40px) rotate(45deg);
					cursor: pointer;
					background-color: rgba(64, 158, 255, 0.82);
					transition: background-color 0.2s ease;
					overflow: hidden;

					&:hover {
						background-color: var(--yeying-color-primary, #409eff);
					}

					.login-mode-switch-icon {
						position: absolute;
						bottom: -20px;
						left: 16px;
						display: flex;
						align-items: flex-start;
						justify-content: flex-start;
						width: 50px;
						height: 50px;
						color: #ffffff;
						transform: rotate(-45deg);

						> svg {
							width: 32px;
							height: 32px;
							margin-top: 3px;
							margin-left: 13px;
						}
					}
				}
			}

			.login-title {
				margin-top: 46px;
				text-align: center;
				font-size: 24px;
				font-weight: 600;
				line-height: 32px;
			}

			.login-subtitle {
				width: calc(100% - 80px);
				margin-top: 12px;
				margin-right: auto;
				margin-left: auto;
				text-align: center;
				color: #aaaaaa;
				font-size: 14px;
				line-height: 20px;
			}

			.login-qrcode {
				display: flex;
				flex-direction: column;
				align-items: center;
				justify-content: center;
				margin: 40px auto 34px;

				.login-qrcode-frame {
					position: relative;
					display: flex;
					align-items: center;
					justify-content: center;
					width: 208px;
					height: 208px;
					border-radius: 8px;
					background: #ffffff;
					cursor: pointer;
				}

				.login-qrcode-image {
					width: 200px;
					height: 200px;
					object-fit: contain;
				}

				.login-qrcode-loading {
					color: #aaaaaa;
					font-size: 13px;
				}

				.login-qrcode-status {
					min-height: 20px;
					margin: 14px 32px 0;
					color: #aaaaaa;
					font-size: 13px;
					line-height: 20px;
					text-align: center;
				}

				.login-passport-local {
					height: 30px;
					margin-top: 8px;
					padding: 0;
					color: var(--yeying-color-primary, #409eff);
				}
			}

			.login-mode-enter-active,
			.login-mode-leave-active {
				transition: opacity 0.12s ease;
			}

			.login-mode-enter-from,
			.login-mode-leave-to {
				opacity: 0;
			}

			.login-access {
				margin: 26px 40px 30px;

				> * {
					margin-top: 18px;
				}

				.wallet-login-button {
					width: 100%;
					height: 44px;
					font-size: 16px;
				}

				.email-login-toggle {
					width: 100%;
					height: 36px;
					margin-left: 0;
					color: #777777;
					font-size: 14px;

					.email-login-arrow {
						margin-left: 6px;
						font-size: 14px;
					}
				}

				.email-login-panel {
					overflow: hidden;

					:deep(.el-form-item) {
						margin-bottom: 18px;
					}

					:deep(.el-input__wrapper) {
						min-height: 44px;
						border-radius: 4px;
						box-shadow: 0 0 0 1px #f1f1f1 inset;
					}

					.email-login-actions {
						margin-bottom: 0;

						:deep(.el-form-item__content) {
							display: grid;
							grid-template-columns: 1fr 1fr;
							gap: 12px;
						}

						.el-button {
							width: 100%;
							margin-left: 0;
						}
					}
				}

				.login-expand-enter-active,
				.login-expand-leave-active {
					transition: opacity 0.2s ease, transform 0.2s ease;
					transform-origin: top;
				}

				.login-expand-enter-from,
				.login-expand-leave-to {
					opacity: 0;
					transform: translateY(-8px);
				}

			}
		}
	}
}

@media screen and (max-width: 520px) {
	.page-login {
		align-items: flex-start;

		.login-body {
			padding: 24px 0 96px;

			.login-logo {
				width: 76px;
				height: 76px;
			}

			.login-box {
				width: 100%;
				max-width: 460px;
				margin-top: 12px;
				border-radius: 12px;
				background-color: transparent;
				box-shadow: none;

				.login-title {
					margin-top: 20px;
					font-size: 26px;
				}

				.login-subtitle {
					width: calc(100% - 72px);
					margin-top: 4px;
				}

				.login-access {
					margin: 20px 36px;
				}
			}
		}
	}
}

@media screen and (max-height: 720px) {
	.page-login {
		.login-body {
			.login-box {
				.login-title {
					margin-top: 24px;
				}

				.login-access {
					margin-top: 18px;
					margin-bottom: 20px;

					> * {
						margin-top: 14px;
					}
				}
			}
		}
	}
}
</style>

<template>
	<div class="login-view">
		<div class="decoration decoration-1"></div>
		<div class="decoration decoration-2"></div>
		<div class="decoration decoration-3"></div>
		<div class="content">
			<el-form class="form" :model="loginForm" status-icon :rules="rules" ref="loginForm"
				@keyup.enter="emailLoginVisible && submitForm('loginForm')">
				<div class="title">
					<img class="logo" src="../../public/logo.png" />
					<div>登录Yeying Social</div>
				</div>
				<el-form-item prop="terminal" v-show="false">
					<el-input type="text" v-model="loginForm.terminal" autocomplete="off"></el-input>
				</el-form-item>
				<el-form-item>
					<el-button type="primary" :loading="walletLoading" @click="walletSignIn">钱包登录</el-button>
					<el-button type="success" plain :loading="passportLoading" @click="startPassportLogin">通行证登录</el-button>
				</el-form-item>
				<el-button class="email-login-toggle" text @click="toggleEmailLogin">邮箱密码登录</el-button>
				<div v-if="emailLoginVisible" class="email-login">
					<el-form-item prop="email">
						<el-input v-model="loginForm.email" autocomplete="email" placeholder="邮箱">
							<template #prefix><el-icon><User /></el-icon></template>
						</el-input>
					</el-form-item>
					<el-form-item prop="password">
						<el-input type="password" v-model="loginForm.password" autocomplete="current-password" placeholder="密码">
							<template #prefix><el-icon><Lock /></el-icon></template>
						</el-input>
					</el-form-item>
					<el-form-item>
						<el-button @click="resetForm('loginForm')">清空</el-button>
						<el-button type="primary" @click="submitForm('loginForm')">邮箱登录</el-button>
					</el-form-item>
				</div>
				<div v-if="passportSession" class="passport-login">
					<iframe class="passport-verify" :src="passportSession.verifyUrl" title="夜莺通行证登录确认"></iframe>
					<div class="passport-status">{{ passportStatus }}</div>
					<el-button text @click="cancelPassportLogin">取消通行证登录</el-button>
				</div>
				<div class="register">
					<router-link to="/register">没有账号,前往注册</router-link>
				</div>
			</el-form>
		</div>
		<icp></icp>
	</div>
</template>

<script>
import Icp from '../components/common/Icp.vue'
import { createPassportLoginSession, getPassportLoginStatus } from '../api/passportAuth'
import { walletLogin } from '../api/web3Auth'
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
			passportLoading: false,
			walletLoading: false,
			emailLoginVisible: false,
			passportSession: null,
			passportStatus: '',
			passportTimer: null
		};
	},
	methods: {
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
			this.passportLoading = true
			try {
				const session = await createPassportLoginSession()
				if (!session.verifyUrl) throw new Error('通行证服务未返回确认地址')
				this.passportSession = session
				this.passportStatus = '请使用通行证确认登录'
				this.pollPassportStatus()
			} catch (error) {
				this.$message.error(error && error.message ? error.message : "无法发起通行证登录")
			} finally {
				this.passportLoading = false
			}
		},
		async pollPassportStatus() {
			if (!this.passportSession) return
			try {
				const result = await getPassportLoginStatus(this.passportSession.sessionId)
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
					? '已确认，正在登录…' : (result.message || '等待通行证确认')
				this.passportTimer = window.setTimeout(() => this.pollPassportStatus(), 2000)
			} catch (error) {
				this.cancelPassportLogin()
				this.$message.error(error && error.message ? error.message : '通行证登录失败')
			}
		},
		cancelPassportLogin() {
			if (this.passportTimer) window.clearTimeout(this.passportTimer)
			this.passportTimer = null
			this.passportSession = null
			this.passportStatus = ''
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
	},
	beforeUnmount() {
		this.cancelPassportLogin()
	}
}
</script>

<style scoped lang="scss">
.login-view {
	width: 100%;
	height: 100%;
	box-sizing: border-box;
	background: linear-gradient(15deg, var(--yeying-color-primary-light-9) 0%, var(--yeying-color-primary-light-4) 100%);
	
		/* 装饰性元素 */
	.decoration {
		position: absolute;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.2);
	}

	.decoration-1 {
		width: 150px;
		height: 150px;
		background: rgba(255, 255, 255, 0.2);
		top: -150px;
		right: 0px;
		animation: float 16s infinite ease-in-out;
	}

	.decoration-2 {
		width: 200px;
		height: 200px;
		background: rgba(255, 255, 255, 0.18);
		bottom: -100px;
		left: -50px;
		animation: float 12s infinite ease-in-out;
	}

	.decoration-3 {
		width: 100px;
		height: 100px;
		background: rgba(255, 255, 255, 0.15);
		top: 50%;
		right: 50px;
		animation: float 8s infinite ease-in-out;
	}

	@keyframes float {
		0%,
		100% {
			transform: translateY(0) translateX(0);
		}

		25% {
			transform: translateY(-60px) translateX(30px);
		}

		50% {
			transform: translateY(30px) translateX(-45px);
		}

		75% {
			transform: translateY(-30px) translateX(-30px);
		}
	}
		

	.content {
		position: relative;
		display: flex;
		justify-content: space-around;
		align-items: center;
		padding: 10%;

		.form {
			width: 360px;
			min-height: 380px;
			padding: 30px;
			background: rgba(255, 255, 255, 0.95);
			border-radius: 3%;
			overflow: hidden;

			.title {
				display: flex;
				justify-content: center;
				align-items: center;
				line-height: 50px;
				margin: 30px 0 40px 0;
				font-size: 22px;
				font-weight: 600;
				letter-spacing: 2px;
				text-transform: uppercase;
				text-align: center;

				.logo {
					width: 30px;
					height: 30px;
					margin-right: 10px;
				}
			}

			.register {
				display: flex;
				flex-direction: row-reverse;
				line-height: 40px;
				text-align: left;
				padding-left: 20px;
			}

			.email-login-toggle { display: block; margin: -10px auto 14px; }

			.email-login { margin-top: 4px; }

			.passport-login {
				margin-top: -8px;
				text-align: center;
				color: #606266;
				font-size: 13px;

				.passport-verify {
					width: 100%;
					height: 280px;
					border: 1px solid #dcdfe6;
					border-radius: 4px;
					background: #fff;
				}

				.passport-status { min-height: 24px; line-height: 24px; }
			}
		}
	}
}
</style>

<template>
	<div class="chat-message-item">
		<div class="message-tip" v-if="msgInfo.type == $enums.MESSAGE_TYPE.TIP_TEXT">
			{{ msgInfo.content }}
		</div>
		<div class="message-tip" v-else-if="msgInfo.type == $enums.MESSAGE_TYPE.TIP_TIME">
			{{ $date.toTimeText(msgInfo.sendTime) }}
		</div>
		<div class="message-normal" v-else-if="isNormal" :class="{ 'message-mine': mine }">
			<div class="head-image">
				<head-image :name="showName" :size="38" :url="headImage" :id="msgInfo.sendId"></head-image>
			</div>
			<div class="content">
				<div v-show="mode == 1 && msgInfo.groupId && !msgInfo.selfSend" class="message-top">
					<span>{{ showName }}</span>
				</div>
				<div v-show="mode == 2" class="message-top">
					<span>{{ showName }}</span>
					<span>{{ $date.toTimeText(msgInfo.sendTime) }}</span>
				</div>
				<div class="message-bottom" @contextmenu.prevent="showRightMenu($event)">
					<div ref="chatMsgBox" class="message-content-wrapper">
						<span class="message-text" v-if="isTextMessage" v-html="htmlText"></span>
						<div class="message-image" v-else-if="msgInfo.type == $enums.MESSAGE_TYPE.IMAGE"
							@click="showFullImageBox()">
							<img :style="imageStyle" :src="contentData.thumbUrl" loading="lazy" />
						</div>
						<div class="message-file" v-else-if="msgInfo.type == $enums.MESSAGE_TYPE.FILE">
							<div class="chat-file-box" v-loading="sending">
								<div class="chat-file-info">
									<el-link class="chat-file-name" :underline="true" target="_blank" type="primary"
										:href="contentData.url" :download="contentData.name">{{ contentData.name
										}}</el-link>
									<div class="chat-file-size">{{ fileSize }}</div>
								</div>
								<div class="chat-file-icon">
									<el-icon><Document /></el-icon>
								</div>
							</div>
						</div>
						<div class="message-voice" v-else-if="msgInfo.type == $enums.MESSAGE_TYPE.AUDIO"
							@click="onPlayVoice()">
							<audio controls :src="JSON.parse(msgInfo.content).url"></audio>
						</div>
						<div title="发送中" v-if="sending" class="sending" v-loading="'true'"></div>
						<div title="发送失败" v-else-if="sendFail" @click="onSendFail" class="send-fail">
							<el-icon><Warning /></el-icon>
						</div>
					</div>
					<div class="chat-action message-text" v-if="isAction">
						<span v-if="msgInfo.type == $enums.MESSAGE_TYPE.ACT_RT_VOICE" title="重新呼叫"
							@click="$emit('call')" class="iconfont icon-chat-voice"></span>
						<span v-if="msgInfo.type == $enums.MESSAGE_TYPE.ACT_RT_VIDEO" title="重新呼叫"
							@click="$emit('call')" class="iconfont icon-chat-video"></span>
						<span>{{ msgInfo.content }}</span>
					</div>
					<div class="message-status" v-if="!isAction && msgInfo.selfSend && !isGroupMessage">
						<span class="chat-readed" v-if="msgInfo.status == $enums.MESSAGE_STATUS.READED">已读</span>
						<span class="chat-unread" v-else>未读</span>
					</div>
					<div class="chat-receipt" v-show="msgInfo.receipt" @click="onShowReadedBox">
						<span v-if="msgInfo.receiptOk" class="icon iconfont icon-ok" title="全体已读"></span>
						<span v-else>{{ msgInfo.readedCount }}人已读</span>
					</div>
				</div>
			</div>
		</div>
		<right-menu ref="rightMenu" @select="onSelectMenu"></right-menu>
		<chat-group-readed ref="chatGroupReadedBox" :msgInfo="msgInfo" :groupMembers="groupMembers"></chat-group-readed>
	</div>
</template>

<script>
import HeadImage from "../common/HeadImage.vue";
import RightMenu from '../common/RightMenu.vue';
import ChatGroupReaded from './ChatGroupReaded.vue';
export default {
	name: "messageItem",
	components: {
		HeadImage,
		RightMenu,
		ChatGroupReaded
	},
	props: {
		mode: {
			type: Number,
			default: 1
		},
		mine: {
			type: Boolean,
			required: true
		},
		headImage: {
			type: String,
			required: true
		},
		showName: {
			type: String,
			required: true
		},
		msgInfo: {
			type: Object,
			required: true
		},
		groupMembers: {
			type: Array
		},
		menu: {
			type: Boolean,
			default: true
		}
	},
	data() {
		return {
			audioPlayState: 'STOP'
		}
	},
	methods: {
		onSendFail() {
			this.$emit("resend", this.msgInfo);
		},
		showFullImageBox() {
			let imageUrl = JSON.parse(this.msgInfo.content).originUrl;
			if (imageUrl) {
				this.$eventBus.emit("openFullImage", imageUrl);
			}
		},
		onPlayVoice() {
			if (!this.audio) {
				this.audio = new Audio();
				this.audio.onended = () => {
					this.audioPlayState = 'STOP';
				};
			}
			this.audio.src = JSON.parse(this.msgInfo.content).url;
			this.audio.play();
			this.audioPlayState = 'RUNNING';
		},
		showRightMenu(e) {
			this.$refs.rightMenu.open(e, this.menuItems);
		},
		onSelectMenu(item) {
			this.$emit(item.key.toLowerCase(), this.msgInfo);
		},
		onShowReadedBox() {
			let rect = this.$refs.chatMsgBox.getBoundingClientRect();
			this.$refs.chatGroupReadedBox.open(rect);
		}
	},
	computed: {
		sending() {
			return this.msgInfo.status == this.$enums.MESSAGE_STATUS.SENDING;
		},
		sendFail() {
			return this.msgInfo.status == this.$enums.MESSAGE_STATUS.FAILED;
		},
		contentData() {
			return JSON.parse(this.msgInfo.content)
		},
		fileSize() {
			let size = this.contentData.size;
			if (size > 1024 * 1024) {
				return Math.round(size / 1024 / 1024) + "M";
			}
			if (size > 1024) {
				return Math.round(size / 1024) + "KB";
			}
			return size + "B";
		},
		menuItems() {
			let items = [];
				items.push({
					key: 'DELETE',
					name: '删除',
					icon: 'Delete'
				});
				if (this.msgInfo.selfSend && this.msgInfo.id > 0) {
					items.push({
						key: 'RECALL',
						name: '撤回',
						icon: 'RefreshLeft'
					});
				}
			return items;
		},
		isTextMessage() {
			return this.msgInfo.type == this.$enums.MESSAGE_TYPE.TEXT
		},
		isAction() {
			return this.$msgType.isAction(this.msgInfo.type);
		},
		isNormal() {
			const type = this.msgInfo.type;
			return this.$msgType.isNormal(type) || this.$msgType.isAction(type)
		},
		htmlText() {
			let color = this.msgInfo.selfSend ? 'white' : '';
			let text = this.$str.html2Escape(this.msgInfo.content)
			text = this.$url.replaceURLWithHTMLLinks(text, color)
			return this.$emo.transform(text, 'emoji-normal')
		},
		isGroupMessage() {
			return !!this.msgInfo.groupId;
		},
		imageStyle() {
			// 计算图片的显示宽高，要求：任意边不能高于360px,不能低于60px,不能拉伸图片比例
			let maxSize = this.configStore.fullScreen ? 360 : 240;
			let minSize = 60;
			let width = this.contentData.width;
			let height = this.contentData.height;
			if (width && height) {
				let ratio = Math.min(width, height) / Math.max(width, height);
				let w = Math.max(Math.min(width > height ? maxSize : ratio * maxSize, width), minSize);
				let h = Math.max(Math.min(width > height ? ratio * maxSize : maxSize, height), minSize);
				return `width: ${w}px;height:${h}px;object-fit: cover;`
			} else {
				// 兼容历史版本，历史数据没有记录宽高
				return `max-width: ${maxSize}px;min-width:60px;max-height: ${maxSize}px;min-height:60px;`
			}
		}
	}
}
</script>

<style lang="scss">
.chat-message-item {
	padding: 8px 18px;
	border-radius: 8px;

	.message-tip {
		line-height: 34px;
		font-size: var(--yeying-font-size-small);
		color: var(--yeying-text-color-light);
		text-align: center;
	}

	.message-normal {
		position: relative;
		font-size: 0;
		padding-left: 50px;
		min-height: 44px;
		margin-top: 8px;

		.head-image {
			position: absolute;
			width: 40px;
			height: 40px;
			top: 0;
			left: 0;
		}

		.content {
			text-align: left;

			.message-top {
				display: flex;
				flex-wrap: nowrap;
				color: var(--yeying-text-color-light);
				font-size: var(--yeying-font-size-smaller);
				line-height: 18px;
				margin-bottom: 4px;

				span {
					margin-right: 12px;
				}
			}

			.message-bottom {
				display: inline-flex;
				flex-direction: column;
				align-items: flex-start;
				max-width: min(68%, 620px);
				padding: 0;
				vertical-align: top;


				.message-content-wrapper {
					position: relative;
					display: flex;
					align-items: center;
					max-width: 100%;

					.sending {
						width: 22px;
						height: 22px;
						margin-left: 8px;

						.circular {
							width: 22px;
							height: 22px;
						}
					}

						.send-fail {
							color: #e45050;
							font-size: 22px;
							cursor: pointer;
							margin: 0 8px;

							.el-icon {
								font-size: inherit;
							}
						}
				}

				.message-text {
					flex: 1;
					display: inline-block;
					position: relative;
					max-width: 100%;
					line-height: 24px;
					padding: 8px 12px;
					background-color: var(--yeying-background);
					border: 1px solid #edf0f5;
					border-radius: 8px;
					font-size: var(--yeying-font-size);
					text-align: left;
					white-space: pre-wrap;
					word-break: break-word;
					box-sizing: border-box;

				}

				.message-image {
					border-radius: 8px;
					border: 1px solid #edf0f5;
					overflow: hidden;
					cursor: pointer;
					background: var(--yeying-background);
					box-shadow: var(--yeying-box-shadow-light);
				}

				.message-file {
					display: flex;
					flex-wrap: nowrap;
					flex-direction: row;
					align-items: center;
					cursor: pointer;
					margin-bottom: 2px;
					background: var(--yeying-background);

					.chat-file-box {
						display: flex;
						flex-wrap: nowrap;
						align-items: center;
						min-height: 64px;
						min-width: 260px;
						max-width: 340px;
						border: 1px solid #edf0f5;
						border-radius: 8px;
						padding: 12px 14px;
						background: #ffffff;
						box-sizing: border-box;

						.chat-file-info {
							flex: 1;
							min-width: 0;
							height: 100%;
							text-align: left;
							font-size: 14px;
							margin-right: 10px;

							.chat-file-name {
								display: inline-block;
								max-width: 220px;
								font-size: 14px;
								margin-bottom: 4px;
								white-space: nowrap;
								text-overflow: ellipsis;
								overflow: hidden;
							}

							.chat-file-size {
								font-size: var(--yeying-font-size-smaller);
								color: var(--yeying-text-color-light);
							}
						}

							.chat-file-icon {
								display: flex;
								align-items: center;
								justify-content: center;
								width: 38px;
								height: 38px;
								border-radius: 8px;
								background: var(--yeying-color-primary-light-9);
								font-size: 24px;
								color: var(--yeying-color-primary);

								.el-icon {
									font-size: inherit;
								}
							}
					}

					.send-fail {
						color: #e45050;
						font-size: 22px;
						cursor: pointer;
						margin: 0 8px;
					}

				}

				.message-voice {
					font-size: 14px;
					cursor: pointer;

					audio {
						width: 280px;
						height: 40px;
						padding: 0;
					}
				}

				.chat-action {
					display: flex;
					align-items: center;

					.iconfont {
						cursor: pointer;
						font-size: 22px;
						padding-right: 8px;
					}
				}

				.message-status {
					margin-top: 5px;
					display: block;

					.chat-readed {
						font-size: 12px;
						color: var(--yeying-text-color-light);
					}

					.chat-unread {
						font-size: var(--yeying-font-size-smaller);
						color: var(--yeying-color-danger);
					}
				}

				.chat-receipt {
					margin-top: 5px;
					font-size: var(--yeying-font-size-smaller);
					cursor: pointer;
					color: var(--yeying-text-color-light);

					.icon-ok {
						font-size: 20px;
						color: var(--yeying-color-sucess);
					}
				}

				.chat-at-user {
					padding: 2px 5px;
					border-radius: 3px;
					cursor: pointer;
				}
			}
		}


		&.message-mine {
			text-align: right;
			padding-left: 0;
			padding-right: 50px;

			.head-image {
				left: auto;
				right: 0;
			}

			.content {
				text-align: right;

				.message-top {
					flex-direction: row-reverse;

					span {
						margin-left: 12px;
						margin-right: 0;
					}
				}

				.message-bottom {
					align-items: flex-end;
					padding: 0;

					.message-content-wrapper {
						flex-direction: row-reverse;
					}

					.message-text {
						background-color: var(--yeying-color-primary-light-2);
						border-color: var(--yeying-color-primary-light-2);
						color: #fff;
					}

					.chat-action {
						flex-direction: row-reverse;

						.iconfont {
							transform: rotateY(180deg);
						}
					}
				}
			}
		}

	}
}
</style>

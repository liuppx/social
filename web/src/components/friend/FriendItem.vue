<template>
	<div class="friend-item" :class="active ? 'active' : ''" @contextmenu.prevent="showRightMenu($event)">
		<div class="friend-avatar">
			<head-image :size="42" :name="friend.nickName" :url="friend.headImage" :online="friend.online">
			</head-image>
		</div>
		<div class="friend-info">
				<div class="friend-name">{{ friend.nickName }}</div>
				<div class="friend-online">
					<span class="online-wrap" v-show="friend.onlineWeb" title="电脑设备在线">
						<el-icon class="online"><Monitor /></el-icon>
						<span class="online-icon"></span>
					</span>
					<span class="online-wrap" v-show="friend.onlineApp" title="移动设备在线">
						<el-icon class="online"><Iphone /></el-icon>
						<span class="online-icon"></span>
					</span>
				</div>
		</div>
		<right-menu ref="rightMenu" @select="onSelectMenu"></right-menu>
		<slot></slot>
	</div>
</template>

<script>
import HeadImage from '../common/HeadImage.vue';
import RightMenu from "../common/RightMenu.vue";

export default {
	name: "frinedItem",
	components: {
		HeadImage,
		RightMenu
	},
	data() {
		return {
				menuItems: [{
					key: 'CHAT',
					name: '发送消息',
					icon: 'ChatDotRound'
				}, {
					key: 'DELETE',
					name: '删除好友',
					icon: 'Delete'
				}]
		}
	},
	methods: {
		showRightMenu(e) {
			if (this.menu) {
				this.$refs.rightMenu.open(e, this.menuItems);
			}
		},
		onSelectMenu(item) {
			this.$emit(item.key.toLowerCase(), this.msgInfo);
		}
	},
	props: {
		active: {
			type: Boolean
		},
		friend: {
			type: Object
		},
		menu: {
			type: Boolean,
			default: true
		}
	}

}
</script>

<style scoped lang="scss">
.friend-item {
	height: 60px;
	display: flex;
	position: relative;
	align-items: center;
	white-space: nowrap;
	border-radius: 8px;
	margin: 4px 8px;
	padding: 8px;
	cursor: pointer;
	box-sizing: border-box;
	transition: background-color 0.15s ease;

	&:hover {
		background-color: var(--yeying-background-active);
	}

	&.active {
		background-color: var(--yeying-background-active-dark);
	}

	.friend-avatar {
		display: flex;
		justify-content: center;
		align-items: center;
	}

	.friend-info {
		flex: 1;
		display: flex;
		flex-direction: column;
		padding-left: 10px;
		text-align: left;
		min-width: 0;

		.friend-name {
			font-size: var(--yeying-font-size);
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
			line-height: 22px;
		}

			.friend-online {
				height: 20px;
				line-height: 20px;

				.online-wrap {
					display: inline-flex;
					position: relative;
				}

				.online {
					font-weight: bold;
					padding-right: 2px;
					font-size: 16px;
				}

			.online-icon {
				position: absolute;
				right: 0;
				bottom: 0;
				width: 6px;
				height: 6px;
				background: limegreen;
				border-radius: 50%;
				border: 1px solid white;
			}
		}
	}
}
</style>

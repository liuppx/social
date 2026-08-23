<template>
			<el-dialog draggable class="add-friend" title="添加好友" :model-value="dialogVisible"
				@update:model-value="onDialogVisibleChange" width="440px" :before-close="onClose">
				<el-input placeholder="输入用户名或昵称搜索" class="input-with-select" v-model="searchText"
					@keyup.enter="onSearch()">
					<template #suffix>
						<el-icon @click="onSearch()"><Search /></el-icon>
					</template>
				</el-input>
		<el-scrollbar class="user-list">
			<div v-for="(user) in users" :key="user.id" v-show="user.id != userStore.userInfo.id">
				<div class="item">
					<div class="avatar">
						<head-image :name="user.nickName" :url="user.headImage" :online="user.online"></head-image>
					</div>
					<div class="friend-info">
						<div class="nick-name">
							<div>{{ user.nickName }}</div>
							<div :class="user.online ? 'online-status  online' : 'online-status'">{{
								user.online ? "[在线]" : "[离线]" }}</div>
						</div>
						<div class="user-name">
							<div>用户名:{{ user.userName }}</div>
						</div>
					</div>
						<el-button type="primary" size="small" v-show="!isFriend(user.id)"
							@click="onAddFriend(user)">添加</el-button>
						<el-button type="info" size="small" v-show="isFriend(user.id)" plain disabled>已添加</el-button>
				</div>
			</div>
		</el-scrollbar>
	</el-dialog>
</template>

<script>
import HeadImage from '../common/HeadImage.vue'


export default {
	name: "addFriend",
	components: { HeadImage },
	data() {
		return {
			users: [],
			searchText: ""
		}
	},
	props: {
		dialogVisible: {
			type: Boolean
		}
	},
	methods: {
		onDialogVisibleChange(value) {
			if (!value) {
				this.onClose();
			}
		},
		onClose() {
			this.$emit("close");
		},
		onSearch() {
			if (!this.searchText) {
				this.users = [];
				return;
			}
			this.$http({
				url: "/user/findByName",
				method: "get",
				params: {
					name: this.searchText
				}
			}).then((data) => {
				this.users = data;
			})
		},
		onAddFriend(user) {
			this.$http({
				url: "/friend/add",
				method: "post",
				params: {
					friendId: user.id
				}
			}).then(() => {
				this.$message.success("添加成功，对方已成为您的好友");
				let friend = {
					id: user.id,
					nickName: user.nickName,
					headImage: user.headImageThumb,
					online: user.online,
					deleted: false
				}
				this.friendStore.addFriend(friend);
			})
		},
		isFriend(userId) {
			return this.friendStore.isFriend(userId);
		}
	}
}
</script>

<style lang="scss" scoped>
.add-friend {
	.input-with-select {
		margin-bottom: 12px;
	}

	.user-list {
		height: 400px;
		border: 1px solid #edf0f5;
		border-radius: 8px;
		background: #ffffff;
	}

	.item {
		height: 68px;
		display: flex;
		position: relative;
		gap: 12px;
		padding: 10px 12px;
		align-items: center;
		box-sizing: border-box;
		border-bottom: 1px solid #f2f3f5;

		&:hover {
			background: #f8f9fb;
		}

		.friend-info {
			flex: 1;
			min-width: 0;
			display: flex;
			flex-direction: column;
			overflow: hidden;

			.nick-name {
				display: flex;
				flex-direction: row;
				align-items: center;
				gap: 8px;
				font-weight: 600;
				font-size: var(--yeying-font-size);
				line-height: 22px;
				min-width: 0;

				> div:first-child {
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}

				.online-status {
					font-size: 12px;
					font-weight: 500;
					color: var(--yeying-text-color-light);

					&.online {
						color: #5fb878;
					}
				}
			}

			.user-name {
				display: flex;
				flex-direction: row;
				font-size: 12px;
				line-height: 20px;
				color: var(--yeying-text-color-light);
				overflow: hidden;
				text-overflow: ellipsis;
				white-space: nowrap;
			}

		}
	}
}
</style>

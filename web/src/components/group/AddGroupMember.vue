<template>
	<el-dialog draggable class="add-group-member-dialog" title="邀请好友" v-model="show" width="680px" :before-close="close">
		<div class="add-group-member">
				<div class="left-box">
					<div class="search">
							<el-input placeholder="搜索好友" v-model="searchText">
								<template #suffix>
									<el-icon><Search /></el-icon>
								</template>
							</el-input>
				</div>
				<el-scrollbar class="select-scroll">
					<div v-for="friend in friends" :key="friend.id">
						<friend-item v-show="friend.nickName.includes(searchText)" :showDelete="false"
							@click="onSwitchCheck(friend)" :menu="false" :friend="friend" :active="false">
							<el-checkbox :disabled="friend.disabled" @click.stop="" class="checkbox"
								v-model="friend.isCheck"></el-checkbox>
						</friend-item>
					</div>
				</el-scrollbar>
			</div>
				<div class="arrow">
					<el-icon><DArrowRight /></el-icon>
				</div>
			<div class="right-box">
				<div class="tip"> 已勾选{{ checkCount }}位好友</div>
				<el-scrollbar class="select-scroll">
					<div v-for="friend in friends" :key="friend.id">
						<friend-item v-if="friend.isCheck && !friend.disabled" :friend="friend" :active="false"
							@del="onRemoveFriend(friend)" :menu="false">
						</friend-item>
					</div>
				</el-scrollbar>
			</div>
		</div>
			<template #footer>
				<span class="dialog-footer">
					<el-button @click="close()">取 消</el-button>
					<el-button type="primary" @click="onOk()">确 定</el-button>
				</span>
			</template>
		</el-dialog>
</template>

<script>
import FriendItem from '../friend/FriendItem.vue';

export default {
	name: "addGroupMember",
	components: {
		FriendItem
	},
	data() {
		return {
			show: false,
			searchText: "",
			friends: []
		}
	},
	methods: {
		open() {
			this.show = true;
			this.friends = [];
			this.friendStore.friends.forEach((f) => {
				if (f.deleted) {
					return;
				}
				let friend = JSON.parse(JSON.stringify(f))
				let m = this.members.filter((m) => !m.quit)
					.find((m) => m.userId == f.id);
				if (m) {
					// 好友已经在群里
					friend.disabled = true;
					friend.isCheck = true
				} else {
					friend.disabled = false;
					friend.isCheck = false;
				}
				this.friends.push(friend);
			})
		},
		close() {
			this.show = false;
		},
		onOk() {
			let inviteVO = {
				groupId: this.groupId,
				friendIds: []
			}
			this.friends.forEach((f) => {
				if (f.isCheck && !f.disabled) {
					inviteVO.friendIds.push(f.id);
				}
			})
			if (inviteVO.friendIds.length > 0) {
				this.$http({
					url: "/group/invite",
					method: 'post',
					data: inviteVO
				}).then(() => {
					this.$message.success("邀请成功");
					this.$emit("reload");
					this.close()
				})
			}
		},
		onRemoveFriend(friend) {
			friend.isCheck = false;
		},
		onSwitchCheck(friend) {
			if (!friend.disabled) {
				friend.isCheck = !friend.isCheck
			}
		}
	},
	props: {
		groupId: {
			type: Number
		},
		members: {
			type: Array
		}
	},
	computed: {
		checkCount() {
			return this.friends.filter((f) => f.isCheck && !f.disabled).length;
		}
	}
}
</script>

<style lang="scss" scoped>
.add-group-member {
	display: flex;
	gap: 12px;

	.left-box {
		flex: 1;
		overflow: hidden;
		border: 1px solid #edf0f5;
		border-radius: 8px;
		background: #ffffff;

		.search {
			height: 50px;
			display: flex;
			align-items: center;
			padding: 8px;
			box-sizing: border-box;
			border-bottom: 1px solid #edf0f5;
		}

		.select-scroll {
			height: 400px;
		}

		.checkbox {
			margin-right: 8px;
		}
	}

	.arrow {
		display: flex;
		align-items: center;
		font-size: 18px;
		padding: 0 2px;
		font-weight: 600;
		color: var(--yeying-color-primary);
	}

	.right-box {
		flex: 1;
		overflow: hidden;
		border: 1px solid #edf0f5;
		border-radius: 8px;
		background: #ffffff;

		.tip {
			text-align: left;
			height: 50px;
			line-height: 50px;
			padding: 0 12px;
			box-sizing: border-box;
			border-bottom: 1px solid #edf0f5;
			color: var(--yeying-text-color-light);
		}

		.select-scroll {
			height: 400px;
		}
	}
}

.dialog-footer {
	display: flex;
	justify-content: flex-end;
	gap: 8px;

	.el-button {
		margin-left: 0;
		border-radius: 6px;
	}
}
</style>

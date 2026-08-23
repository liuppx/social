<template>
	<el-dialog draggable class="group-member-selector-dialog" :title="title" v-model="isShow" width="720px">
		<div class="group-member-selector">
				<div class="left-box">
					<div class="search">
						<el-input placeholder="搜索" v-model="searchText">
							<template #suffix>
								<el-icon><Search /></el-icon>
							</template>
						</el-input>
					</div>
				<virtual-scroller class="scroll-box" :items="showMembers">
					<template v-slot="{ item }">
						<group-member-item :group="group" :groupMembers="showMembers" :member="item" :menu="false"
							@click="onClickMember(item)">
							<el-checkbox :disabled="item.locked" v-model="item.checked" @change="onChange(item)"
								@click.stop=""></el-checkbox>
						</group-member-item>
					</template>
				</virtual-scroller>
			</div>
				<div class="arrow">
					<el-icon><DArrowRight /></el-icon>
				</div>
			<div class="right-box">
				<div class="select-tip"> 已勾选{{ checkedMembers.length }}位成员</div>
				<el-scrollbar class="scroll-box">
					<div class="member-items">
						<div v-for="m in members" :key="m.userId">
							<group-member class="member-item" v-if="m.checked" :member="m"></group-member>
						</div>
					</div>
				</el-scrollbar>
			</div>
		</div>
			<template #footer>
				<span class="dialog-footer">
					<el-button @click="close()">取 消</el-button>
					<el-button type="primary" @click="ok()">确 定</el-button>
				</span>
			</template>
		</el-dialog>
</template>

<script>
import VirtualScroller from '../common/VirtualScroller.vue';
import GroupMemberItem from './GroupMemberItem.vue';
import GroupMember from './GroupMember.vue';

export default {
	name: "addGroupMember",
	components: {
		GroupMemberItem,
		GroupMember,
		VirtualScroller
	},
	data() {
		return {
			isShow: false,
			searchText: "",
			maxSize: -1,
			members: []
		}
	},
	props: {
		group: {
			type: Object
		},
		title: {
			type: String,
			default: "选择成员"
		}
	},
	methods: {
		open(maxSize, checkedIds, lockedIds, hideIds) {
			this.maxSize = maxSize;
			this.isShow = true;
			this.loadGroupMembers(checkedIds, lockedIds, hideIds);
		},
		loadGroupMembers(checkedIds, lockedIds, hideIds) {
			this.$http({
				url: `/group/members/${this.group.id}`,
				method: 'get'
			}).then((members) => {
				members.forEach((m) => {
					// 默认选择和锁定的用户
					m.checked = checkedIds.indexOf(m.userId) >= 0;
					m.locked = lockedIds.indexOf(m.userId) >= 0;
					m.hide = hideIds.indexOf(m.userId) >= 0;
				});
				this.members = members;
			});
		},
		onClickMember(m) {
			if (!m.locked) {
				m.checked = !m.checked;
			}
			if (this.maxSize > 0 && this.checkedMembers.length > this.maxSize) {
				this.$message.error(`最多选择${this.maxSize}位成员`)
				m.checked = false;
			}
		},
		onChange(m) {
			if (this.maxSize > 0 && this.checkedMembers.length > this.maxSize) {
				this.$message.error(`最多选择${this.maxSize}位成员`)
				m.checked = false;
			}
		},
		ok() {
			this.$emit("complete", this.checkedMembers);
			this.isShow = false;
		},
		close() {
			this.isShow = false;
		}
	},
	computed: {
		checkedMembers() {
			let ids = [];
			this.members.forEach((m) => {
				if (m.checked) {
					ids.push(m);
				}
			})
			return ids;
		},
		showMembers() {
			return this.members.filter((m) => !m.hide && !m.quit && m.showNickName.includes(this.searchText))
		}
	}
}
</script>

<style lang="scss" scoped>
.group-member-selector {
	display: flex;
	gap: 12px;

	.scroll-box {
		height: 400px;
	}

	.left-box {
		flex: 1;
		overflow: hidden;
		border: 1px solid #edf0f5;
		border-radius: 8px;
		background: #ffffff;

		.search {
			height: 50px;
			padding: 8px;
			box-sizing: border-box;
			border-bottom: 1px solid #edf0f5;
		}
	}

	.arrow {
		display: flex;
		align-items: center;
		font-size: 20px;
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

		.select-tip {
			text-align: left;
			height: 50px;
			line-height: 50px;
			padding: 0 12px;
			box-sizing: border-box;
			border-bottom: 1px solid #edf0f5;
			color: var(--yeying-text-color-light);
		}

		.member-items {
			padding: 12px;
			display: flex;
			flex-direction: row;
			flex-wrap: wrap;
			gap: 8px 4px;

			.member-item {
				padding: 0;
			}
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

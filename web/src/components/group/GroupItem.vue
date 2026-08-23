<template>
	<div class="group-item" :class="active ? 'active' : ''">
		<div class="group-avatar">
			<head-image :size="42" :name="group.showGroupName" :url="group.headImageThumb"> </head-image>
		</div>
		<div class="group-name">
			<div>{{ group.showGroupName }}</div>
		</div>
	</div>
</template>

<script>
import HeadImage from '../common/HeadImage.vue';

export default {
	name: "groupItem",
	components: {
		HeadImage
	},
	data() {
		return {}
	},
	props: {
		group: {
			type: Object
		},
		active: {
			type: Boolean
		}
	}
}
</script>

<style lang="scss" scoped>
.group-item {
	height: 60px;
	display: flex;
	position: relative;
	align-items: center;
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

	.group-name {
		padding-left: 10px;
		flex: 1;
		text-align: left;
		line-height: 22px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		font-size: var(--yeying-font-size);
	}
}
</style>

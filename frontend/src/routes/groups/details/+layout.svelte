<script>
	import { page } from '$app/stores';
	import messages from '$lib/messages.json';
	import groupContext from '../../../stores/groupContext';
	import groupDetailsButton from '../../../stores/groupDetailsButton';
	import GroupDetails from '../GroupDetails.svelte';
	import headerTitle from '../../../stores/headerTitle';
	import detailView from '../../../stores/detailView';

	$: if ($groupDetailsButton == null) {
		groupDetailsButton.set('Users');
	}

	const selectButton = (label) => {
		groupDetailsButton.set(label);
	}
</script>

<style>
	button {
		color: buttontext;
		background-color: buttonface;
		padding: 6px 6px 6px 6px;
		border: none;
	}
	button:hover {
		background-color: #e8def8;
	}
	.active {
		background-color: #e8def8;
	}
</style>

{#if $groupContext && $groupContext.name}
		<div style="margin-block-start: 0.67em;margin-block-end: 1.67em;margin-inline-start: 0px;margin-inline-end: 0px;">
			<GroupDetails
				group={$groupContext}
				on:groupList={() => {
					headerTitle.set(messages['group']['title']);
					detailView.set();
				}}
			/>
		</div>

	<div style="margin-block-start: 0.67em;margin-block-end: 0.67em;margin-inline-start: 0px;margin-inline-end: 0px;">
		{#each $page.data.menuOptions as menuOption, i}
			<button
				class:active={$groupDetailsButton == menuOption.label ? true : false}
				on:click={() => selectButton(menuOption.label)}
				>
				{menuOption.label}
			</button>
		{/each}
	</div>

	<slot></slot>

	<p style="margin-top: 8rem">{messages['footer']['message']}</p>
{/if}

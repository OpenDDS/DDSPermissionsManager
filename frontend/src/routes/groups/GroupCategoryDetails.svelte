<script>
	import { page } from '$app/stores';
	import groupDetailsButton from '../../stores/groupDetailsButton';
	import UserTable from '../../lib/UsersTable.svelte';
	import TopicTable from '../../lib/TopicsTable.svelte';
	import ApplicationTable from '../../lib/ApplicationsTable.svelte';
	import GrantTable from '../../lib/GrantsTable.svelte';

	const selectButton = (label) => {
		groupDetailsButton.set(label);
	}
</script>

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

{#if $groupDetailsButton == 'Users'}
	<UserTable />
{:else if $groupDetailsButton == 'Topics'}
	<TopicTable/>
{:else if $groupDetailsButton == 'Applications'}
	<ApplicationTable/>
{:else if $groupDetailsButton == 'Grants'}
	<GrantTable/>
{/if}

<style>
	button {
		color: buttontext;
		background-color: buttonface;
		padding: 14px 10px 14px 10px;
		border: none;
	}
	button:hover {
		background-color: #e8def8;
	}
	.active {
		background-color: #e8def8;
	}
</style>

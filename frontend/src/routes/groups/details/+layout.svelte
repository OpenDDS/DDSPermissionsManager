<script>
	import { page } from '$app/stores';
	import groupContext from '../../../stores/groupContext';
	import groupDetailsButton from '../../../stores/groupDetailsButton';

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
	<h1>Group: {$groupContext.name}</h1>

	{#each $page.data.menuOptions as menuOption, i}
		<button
			class:active={$groupDetailsButton == menuOption.label ? true : false}
			on:click={() => selectButton(menuOption.label)}
			>
			{menuOption.label}
		</button>
	{/each}

	<slot></slot>
{/if}

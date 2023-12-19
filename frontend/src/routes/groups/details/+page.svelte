<script>
	import { onMount } from 'svelte';
	import groupContext from '../../../stores/groupContext';

	$: selectedGroup = null;

	onMount(async () => {
		groupContext.subscribe((value) => {
			if (value?.name) selectedGroup = value;
		});
	});
</script>

{#if selectedGroup}
	<h1>Group: {selectedGroup.name} ({selectedGroup.id})</h1>
{/if}

<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import moment from 'moment';
	import { onMount, onDestroy } from 'svelte';

	export let retrievedTimestamp;
    let refreshInterval = 60000;
	let timeAgo; // Make it a regular variable
	$: browserFormat = retrievedTimestamp?.toLocaleString();

	let clear;
	onMount(() => {
		const updateTime = () => { // Create a function to update timeAgo
			timeAgo = moment(retrievedTimestamp).fromNow();
		};
		updateTime(); // Call it once immediately
		clear = setInterval(updateTime, refreshInterval); // Use the function in setInterval
	});

	onDestroy(() => {
		clearInterval(clear);
	});
</script>

{#if retrievedTimestamp}
	<p>Retrieved {timeAgo} at ({browserFormat})</p>
{/if}

<style>
	p {
		font-weight: 200;
	}
</style>

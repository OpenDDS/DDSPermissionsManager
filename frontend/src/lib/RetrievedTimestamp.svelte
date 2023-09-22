<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import moment from 'moment';
	import { onMount, onDestroy } from 'svelte';

	export let retrievedTimestamp;
    let refreshInterval = 60000;
	$: timeAgo = moment(retrievedTimestamp).fromNow();
	$: browserFormat = retrievedTimestamp?.toLocaleString();

	let clear;
	onMount(() => {
		clear = setInterval(() => {
			timeAgo = moment(retrievedTimestamp).fromNow();
		}, refreshInterval);
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

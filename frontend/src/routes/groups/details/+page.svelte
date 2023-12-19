<script>
	import groupContext from '../../../stores/groupContext';
</script>

{#if $groupContext}
	<h1>Group: {$groupContext.name}</h1>
{/if}

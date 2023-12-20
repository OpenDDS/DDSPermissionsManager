<script>
	import groupContext from '../../../stores/groupContext';
	import groupDetailsButton from '../../../stores/groupDetailsButton';
	import UserTable from './UserTable.svelte';
</script>

{#if $groupContext && $groupContext.name}

		{#if $groupDetailsButton == 'Users'}
			<UserTable />
		{:else if $groupDetailsButton == 'Topics'}
			<p>Topics Table</p>
		{:else if $groupDetailsButton == 'Applications'}
			<p>Applications Table</p>
		{:else if $groupDetailsButton == 'Grants'}
			<p>Grants Table</p>
		{/if}

{/if}
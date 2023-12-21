<script>
	import groupContext from '../../../stores/groupContext';
	import groupDetailsButton from '../../../stores/groupDetailsButton';
	import UserTable from './UserTable.svelte';
	import TopicTable from './TopicTable.svelte';
	import ApplicationTable from './ApplicationTable.svelte';
	import GrantTable from './GrantTable.svelte';
</script>

{#if $groupContext && $groupContext.name}

		{#if $groupDetailsButton == 'Users'}
			<UserTable />
		{:else if $groupDetailsButton == 'Topics'}
			<TopicTable/>
		{:else if $groupDetailsButton == 'Applications'}
			<ApplicationTable/>
		{:else if $groupDetailsButton == 'Grants'}
			<GrantTable/>
		{/if}

{/if}
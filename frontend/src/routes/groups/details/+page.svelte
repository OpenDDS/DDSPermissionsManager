<script>
	import groupContext from '../../../stores/groupContext';
	import groupDetailsButton from '../../../stores/groupDetailsButton';
	import UserTable from './UsersTable.svelte';
	import TopicTable from './TopicsTable.svelte';
	import ApplicationTable from './ApplicationsTable.svelte';
	import GrantTable from './GrantsTable.svelte';
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
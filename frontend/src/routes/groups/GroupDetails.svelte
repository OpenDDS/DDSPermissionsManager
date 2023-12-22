<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { page } from '$app/stores';
	import { createEventDispatcher, onMount } from 'svelte';
	import { isAdmin, isAuthenticated } from '../../stores/authentication';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import groupsTotalPages from '../../stores/groupsTotalPages';
	import groupsTotalSize from '../../stores/groupsTotalSize';
	import groups from '../../stores/groups';
	import Modal from '../../lib/Modal.svelte';
	import editSVG from '../../icons/edit.svg';
	import { httpAdapter } from '../../appconfig';
	import messages from '$lib/messages.json';
	import groupContext from '../../stores/groupContext';
	import groupDetailsButton from '../../stores/groupDetailsButton';
	import UserTable from '../../lib/UsersTable.svelte';
	import TopicTable from '../../lib/TopicsTable.svelte';
	import ApplicationTable from '../../lib/ApplicationsTable.svelte';
	import GrantTable from '../../lib/GrantsTable.svelte';

	const dispatch = createEventDispatcher();
	let groupsPerPage = 10;
	let groupsCurrentPage = 0;

	export let group;

	$: if (group) isPublic = group.public;

	let descriptionSelector;
	let isPublic = group.public;
	let editGroupVisible = false;

	headerTitle.set(group.name);
	detailView.set(true);

	$: if ($detailView === 'backToList') dispatch('groupList');

	$: if ($groupDetailsButton == null) {
		groupDetailsButton.set('Users');
	}

	const selectButton = (label) => {
		groupDetailsButton.set(label);
	}

	const reloadAllGroups = async (page = 0) => {
		try {
			const res = await httpAdapter.get(`/groups?page=${page}&size=${groupsPerPage}`);

			if (res.data) {
				groupsTotalPages.set(res.data.totalPages);
				groupsTotalSize.set(res.data.totalSize);
			}
			groups.set(res.data.content);
			groupsTotalPages.set(res.data.totalPages);
			groupsCurrentPage = page;
		} catch (err) {
			userValidityCheck.set(true);

			errorMessage(errorMessages['group']['loading.error.title'], err.message);
		}
	};

	onMount(() => {
		// Adjust style when there is no description
		if (!group.description) descriptionSelector.style.marginLeft = '0.27rem';
	});

	const editGroup = async (id, name, description, isPublic) => {
		const res = await httpAdapter.post(`/groups/save`, {
			id: id,
			name: name,
			description: description,
			public: isPublic
		});

		group = res.data;
		headerTitle.set(group.name);

		await reloadAllGroups();
		dispatch('update-search');
	};
</script>

{#if $isAuthenticated}
	<div style="width: 100%; min-width: 45rem; margin-right: 1rem">
		{#if editGroupVisible}
			<Modal
				title={messages['group']['edit.title']}
				actionEditGroup={true}
				groupCurrentName={group.name}
				groupCurrentDescription={group.description}
				groupCurrentPublic={group.public}
				groupNewName={true}
				groupId={group.id}
				on:addGroup={(e) =>
					editGroup(
						e.detail.groupId,
						e.detail.newGroupName,
						e.detail.newGroupDescription,
						e.detail.newGroupPublic
					)}
				on:cancel={() => (editGroupVisible = false)}
			/>
		{/if}

		<table style="margin-top: 1.6rem">
			<tr>
				<td style="font-weight: 300; width: 10.2rem">
					{messages['group.detail']['row.one']}
				</td>

				<td style="font-weight: 500">{group.name} </td>

				{#if $isAdmin || $permissionsByGroup.find((permission) => permission.groupId === group.id && permission.isGroupAdmin)}
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={editSVG}
						alt="edit group"
						width="18rem"
						style="margin-left: 1.5rem; float: right; cursor: pointer"
						on:click={() => (editGroupVisible = true)}
					/>
				{/if}
			</tr>

			<tr>
				<td style="font-weight: 300; width: 6.2rem">
					{messages['group.detail']['row.two']}
				</td>
				<td style="font-weight: 400" bind:this={descriptionSelector}
					>{group.description ? group.description : ` -`}
				</td>
			</tr>

			<tr>
				<td style="font-weight: 300">
					{messages['group.detail']['row.three']}
				</td>
				<td>
					<input
						type="checkbox"
						style="width: 15px; height: 15px"
						bind:checked={isPublic}
						on:change={() => (isPublic = group.public)}
					/>
				</td>
			</tr>
		</table>
	</div>

	{#if $groupContext && $groupContext.name}
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

	{/if}

	<p style="margin-top: 8rem">{messages['footer']['message']}</p>
{/if}

<style>
	td {
		height: 2.2rem;
	}
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
	p {
		font-size: large;
	}
</style>

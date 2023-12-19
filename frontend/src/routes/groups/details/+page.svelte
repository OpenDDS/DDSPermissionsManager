<script>
	import groupContext from '../../../stores/groupContext';
	import messages from '$lib/messages.json';
	import groupMembershipList from '../../../stores/groupMembershipList';
	import groupsSVG from '../../../icons/groups.svg';
	import topicsSVG from '../../../icons/topics.svg';
	import appsSVG from '../../../icons/apps.svg';
	import { httpAdapter } from '../../../appconfig';
	import userValidityCheck from '../../../stores/userValidityCheck';
	import groupAdminGroups from '../../../stores/groupAdminGroups';
	import groupMembershipsTotalPages from '../../../stores/groupMembershipsTotalPages';
	import groupMembershipsTotalSize from '../../../stores/groupMembershipsTotalSize';

	import { afterUpdate, onMount } from 'svelte';

	// Promises
	let promise;

	// Pagination
	let groupMembershipsPerPage = 10;
	let groupMembershipsCurrentPage = 0;

	// Group Membership List
	let groupMembershipListArray = [];

	const reloadGroupMemberships = async (page = 0) => {
		try {
			let res;

			res = await httpAdapter.get(
				`/group_membership?page=${page}&size=${groupMembershipsPerPage}&group=${$groupContext.id}`
			);

			if (res.data) {
				groupMembershipsTotalPages.set(res.data.totalPages);
				groupMembershipsTotalSize.set(res.data.totalSize);
			}
			if (res.data.content) {
				createGroupMembershipList(res.data.content, res.data.totalPages);
			} else {
				groupMembershipList.set();
			}
			groupMembershipsCurrentPage = page;
			// updateRetrievalTimestamp(retrievedTimestamps, 'users');
		} catch (err) {
			// userValidityCheck.set(true);

			// if (err.response.status === 500) onLoggedIn(false);
			// errorMessage(errorMessages['group_membership']['loading.error.title'], err.message);
			console.log(err);
		}
	};

	const createGroupMembershipList = async (data, totalPages, totalSize) => {
		data?.forEach((groupMembership) => {
			let newGroupMembership = {
				applicationAdmin: groupMembership.applicationAdmin,
				groupAdmin: groupMembership.groupAdmin,
				topicAdmin: groupMembership.topicAdmin,
				groupName: groupMembership.permissionsGroupName,
				groupId: groupMembership.permissionsGroup,
				groupMembershipId: groupMembership.id,
				userId: groupMembership.permissionsUser,
				userEmail: groupMembership.permissionsUserEmail
			};
			groupMembershipListArray.push(newGroupMembership);
		});
		groupMembershipList.set(groupMembershipListArray);

		groupMembershipListArray = [];
		groupMembershipsTotalPages.set(totalPages);
		if (totalSize !== undefined) groupMembershipsTotalSize.set(totalSize);
		groupMembershipsCurrentPage = 0;
	};

	onMount(async () => {
		if ($groupContext && $groupContext.id)
			promise = await reloadGroupMemberships();
	});

	afterUpdate(async () => {
		if ($groupContext && $groupContext.id)
			promise = await reloadGroupMemberships();
	})
</script>

{#if $groupContext && $groupContext.name}
	<h1>Group: {$groupContext.name}</h1>

	{#await promise then _}
		{#if $groupMembershipList?.length > 0}
			<table
				data-cy="users-table"
				id="group-memberships-table"
				style="margin-top:0.5rem; min-width: 50rem; width:max-content"
			>
				<thead>
					<tr style="border-top: 1px solid black; border-bottom: 2px solid">
						<td class="header-column" style="font-stretch:ultra-condensed; width:fit-content">
							{messages['user']['table.user.column.one']}
						</td>
						<td class="header-column" style="font-stretch:ultra-condensed; width:6.25rem">
							<center>{messages['user']['table.user.column.three']}</center>
						</td>
						<td class="header-column" style="font-stretch:ultra-condensed; width:6rem">
							<center>{messages['user']['table.user.column.four']}</center>
						</td>
						<td class="header-column" style="font-stretch:ultra-condensed; width:7.8rem">
							<center>{messages['user']['table.user.column.five']}</center>
						</td>
					</tr>
				</thead>
				<tbody>
				{#each $groupMembershipList as groupMembership, i}
					<tr>
						<td style="width:fit-content">{groupMembership.userEmail}</td>
						<td>
							<center>
								{#if groupMembership.groupAdmin}
									<img
										src={groupsSVG}
										alt="group admin"
										width="21rem"
										height="21rem"
										style="vertical-align:middle"
									/>
								{:else}
									-
								{/if}
							</center>
						</td>
						<td>
							<center
								>{#if groupMembership.topicAdmin}
									<img
										src={topicsSVG}
										alt="topic admin"
										width="21rem"
										height="21rem"
										style="vertical-align:middle"
									/>
								{:else}
									-
								{/if}
							</center>
						</td>
						<td data-cy="is-application-admin">
							<center
								>{#if groupMembership.applicationAdmin}
									<img
										src={appsSVG}
										alt="application admin"
										width="21rem"
										height="21rem"
										style="vertical-align:middle"
									/>
								{:else}
									-
								{/if}
							</center>
						</td>
					</tr>
				{/each}
				</tbody>
			</table>
		{/if}
	{/await}

{/if}
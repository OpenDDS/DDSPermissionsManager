<script>
	import groupContext from '../../../stores/groupContext';
	import groupMembershipList from '../../../stores/groupMembershipList';
	import { httpAdapter } from '../../../appconfig';
	import groupMembershipsTotalPages from '../../../stores/groupMembershipsTotalPages';
	import groupMembershipsTotalSize from '../../../stores/groupMembershipsTotalSize';
	import UserTable from './UserTable.svelte';

	import { afterUpdate, onMount } from 'svelte';

	// Promises
	let promise;

	// Pagination
	let groupMembershipsPerPage = 10;
	let groupMembershipsCurrentPage = 0;

	// Group Membership List
	let groupMembershipListArray = [];

	const errorMessage = (errMsg, errObj) => {
		errorMsg = errMsg;
		errorObject = errObj;
		errorMessageVisible = true;
	};

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
		} catch (err) {
			errorMessage(errorMessages['group_membership']['loading.error.title'], err.message);
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

	{#await promise then _}

		<!-- User Table -->
		{#if $groupMembershipList?.length > 0}
			<UserTable users={groupMembershipList}/>
		{/if}

		<!-- Topic Table -->

		<!-- Application Table -->

		<!-- Grant Table -->

	{/await}

{/if}
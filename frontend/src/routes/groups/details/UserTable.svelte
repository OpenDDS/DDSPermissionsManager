<script>
	import messages from '$lib/messages.json';
	import groupsSVG from '../../../icons/groups.svg';
	import topicsSVG from '../../../icons/topics.svg';
	import appsSVG from '../../../icons/apps.svg';
	import pageforwardSVG from '../../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../../icons/pagefirst.svg';
	import pagelastSVG from '../../../icons/pagelast.svg';
	import groupContext from '../../../stores/groupContext';
	import groupMembershipList from '../../../stores/groupMembershipList';
	import { httpAdapter } from '../../../appconfig';
	import groupMembershipsTotalPages from '../../../stores/groupMembershipsTotalPages';
	import groupMembershipsTotalSize from '../../../stores/groupMembershipsTotalSize';
	import { afterUpdate, onMount } from 'svelte';
	import retrievedTimestamps from '../../../stores/retrievedTimestamps.js';
	import RetrievedTimestamp from './../../../lib/RetrievedTimestamp.svelte';
	import { updateRetrievalTimestamp } from '../../../utils.js';

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
			updateRetrievalTimestamp(retrievedTimestamps, 'users');
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

	const deselectAllGroupMembershipCheckboxes = () => {
		usersAllRowsSelectedTrue = false;
		usersRowsSelectedTrue = false;
		usersRowsSelected = [];
		let checkboxes = document.querySelectorAll('.group-membership-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	onMount(async () => {
		if ($groupContext && $groupContext.id)
			promise = await reloadGroupMemberships();
	});

	afterUpdate(async () => {
		if ($groupContext && $groupContext.id)
			promise = await reloadGroupMemberships();
	});
</script>

{#await promise then _}
	<div class="content">
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
				{#each $groupMembershipList as user, i}
					<tr>
						<td style="width:fit-content">{user.userEmail}</td>
						<td>
							<center>
								{#if user.groupAdmin}
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
								>{#if user.topicAdmin}
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
								>{#if user.applicationAdmin}
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

			{#if $groupMembershipsTotalSize !== undefined && $groupMembershipsTotalSize != NaN}
				<div class="pagination">
					<span>{messages['pagination']['rows.per.page']}</span>
					<select
						tabindex="-1"
						on:change={(e) => {
							groupMembershipsPerPage = e.target.value;

							reloadGroupMemberships();
						}}
						name="RowsPerPage"
					>
						<option value="10">10</option>
						<option value="25">25</option>
						<option value="50">50</option>
						<option value="75">75</option>
						<option value="100">100&nbsp;</option>
					</select>
					<span style="margin: 0 2rem 0 2rem">
						{#if $groupMembershipsTotalSize > 0}
							{1 + groupMembershipsCurrentPage * groupMembershipsPerPage}
						{:else}
							0
						{/if}
						- {Math.min(
							groupMembershipsPerPage * (groupMembershipsCurrentPage + 1),
							$groupMembershipsTotalSize
						)} of {$groupMembershipsTotalSize}
					</span>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagefirstSVG}
						alt="first page"
						class="pagination-image"
						class:disabled-img={groupMembershipsCurrentPage === 0}
						on:click={() => {
							deselectAllGroupMembershipCheckboxes();
							if (groupMembershipsCurrentPage > 0) {
								groupMembershipsCurrentPage = 0;

								reloadGroupMemberships();
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagebackwardsSVG}
						alt="previous page"
						class="pagination-image"
						class:disabled-img={groupMembershipsCurrentPage === 0}
						on:click={() => {
							deselectAllGroupMembershipCheckboxes();
							if (groupMembershipsCurrentPage > 0) {
								groupMembershipsCurrentPage--;
								reloadGroupMemberships(groupMembershipsCurrentPage);
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pageforwardSVG}
						alt="next page"
						class="pagination-image"
						class:disabled-img={groupMembershipsCurrentPage + 1 === $groupMembershipsTotalPages ||
							$groupMembershipList?.length === undefined}
						on:click={() => {
							deselectAllGroupMembershipCheckboxes();
							if (groupMembershipsCurrentPage + 1 < $groupMembershipsTotalPages) {
								groupMembershipsCurrentPage++;
								reloadGroupMemberships(groupMembershipsCurrentPage);
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagelastSVG}
						alt="last page"
						class="pagination-image"
						class:disabled-img={groupMembershipsCurrentPage + 1 === $groupMembershipsTotalPages ||
							$groupMembershipList?.length === undefined}
						on:click={() => {
							deselectAllGroupMembershipCheckboxes();
							if (groupMembershipsCurrentPage < $groupMembershipsTotalPages) {
								groupMembershipsCurrentPage = $groupMembershipsTotalPages - 1;
								reloadGroupMemberships(groupMembershipsCurrentPage);
							}
						}}
					/>
				</div>

				<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['users']} />
			{/if}
		{/if}
	</div>
{/await}

<style>
	.content {
		width: 100%;
		min-width: 32rem;
		margin-right: 1rem;
	}

	table {
		width: fit-content;
	}

	tr {
		line-height: 2.2rem;
	}

	p {
		font-size: large;
	} 

	.header-column {
		font-weight: 600;
	}
</style>

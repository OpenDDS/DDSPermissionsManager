<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAuthenticated, isAdmin, onLoggedIn } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import { browser } from '$app/environment';
	import RetrievedTimestamp from '../../lib/RetrievedTimestamp.svelte';
	import groupAdminGroups from '../../stores/groupAdminGroups';
	import groupMembershipList from '../../stores/groupMembershipList';
	import userValidityCheck from '../../stores/userValidityCheck';
	import refreshPage from '../../stores/refreshPage';
	import Modal from '$lib/Modal.svelte';
	import deleteSVG from '../../icons/delete.svg';
	import addSVG from '../../icons/add.svg';
	import editSVG from '../../icons/edit.svg';
	import groupsSVG from '../../icons/groups.svg';
	import topicsSVG from '../../icons/topics.svg';
	import appsSVG from '../../icons/apps.svg';
	import pageforwardSVG from '../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../icons/pagefirst.svg';
	import pagelastSVG from '../../icons/pagelast.svg';
	import userEmail from '../../stores/userEmail';
	import groupContext from '../../stores/groupContext';
	import showSelectGroupContext from '../../stores/showSelectGroupContext';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import singleGroupCheck from '../../stores/singleGroupCheck';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import createItem from '../../stores/createItem';
	import groupMembershipsTotalPages from '../../stores/groupMembershipsTotalPages';
	import groupMembershipsTotalSize from '../../stores/groupMembershipsTotalSize';
	import retrievedTimestamps from '../../stores/retrievedTimestamps';
	import { updateRetrievalTimestamp } from '../../utils.js';

	// Group Context
	$: if ($groupContext?.id) reloadGroupMemberships();

	$: if ($groupContext === 'clear') {
		groupContext.set();
		singleGroupCheck.set();
		selectedGroup = '';
		reloadGroupMemberships();
	}

	// Permission Badges Create
	$: if ($createItem === 'user') {
		createItem.set(false);
		addGroupMembershipVisible = true;
	}

	// Checkboxes selection
	$: if ($groupMembershipList?.length === usersRowsSelected?.length) {
		usersRowsSelectedTrue = false;
		usersAllRowsSelectedTrue = true;
	} else if (usersRowsSelected?.length > 0) {
		usersRowsSelectedTrue = true;
	} else {
		usersAllRowsSelectedTrue = false;
	}

	// Messages
	let deleteToolip;
	let deleteMouseEnter = false;

	let addTooltip;
	let addMouseEnter = false;

	// Promises
	let promise;

	// Constants
	const returnKey = 13;
	const waitTime = 1000;

	// Modals
	let addGroupMembershipVisible = false;
	let errorMessageVisible = false;
	let updateGroupMembershipVisible = false;
	let deleteSelectedGroupMembershipsVisible = false;

	// SearchBox
	let searchString;
	const searchStringLength = 3;
	let searchGroups;
	let searchGroupResults;
	let searchGroupsResultsVisible = false;
	let searchGroupActive = false;
	let selectedGroup;
	let timer;

	// Forms
	let emailValue = '';
	let selectedIsGroupAdmin = false;
	let selectedIsApplicationAdmin = false;
	let selectedIsTopicAdmin = false;
	let groupsDropdownSuggestion = 7;

	// Tables
	let usersRowsSelected = [];
	let usersRowsSelectedTrue = false;
	let usersAllRowsSelectedTrue = false;

	// Group Permissions
	let isGroupAdmin = false;

	// Pagination
	let groupMembershipsPerPage = 10;
	let groupMembershipsCurrentPage = 0;

	// Error Handling
	let errorMsg, errorObject;

	// Group Membership List
	let groupMembershipListArray = [];

	// Locks the background scroll when modal is open
	$: if (
		browser &&
		(addGroupMembershipVisible ||
			deleteSelectedGroupMembershipsVisible ||
			updateGroupMembershipVisible)
	) {
		document.body.classList.add('modal-open');
	} else if (
		browser &&
		!(
			addGroupMembershipVisible ||
			deleteSelectedGroupMembershipsVisible ||
			updateGroupMembershipVisible
		)
	) {
		document.body.classList.remove('modal-open');
	}

	// Search Group Membership Feature
	$: if (searchString?.trim().length >= searchStringLength) {
		searchGroupActive = false;
		clearTimeout(timer);
		timer = setTimeout(() => {
			searchGroupMemberships(searchString.trim());
		}, waitTime);
	}

	$: if (searchString?.trim().length < searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadGroupMemberships();
		}, waitTime);
	}

	// Search Groups Feature
	$: if (searchGroups?.trim().length >= searchStringLength && searchGroupActive) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			searchGroup(searchGroups.trim());
		}, waitTime);
	} else {
		searchGroupsResultsVisible = false;
	}

	// Search Groups Dropdown Visibility
	$: if (searchGroupResults?.data?.content?.length >= 1 && searchGroupActive) {
		searchGroupsResultsVisible = true;
	} else {
		searchGroupsResultsVisible = false;
	}

	// Reset add group form once closed
	$: if (addGroupMembershipVisible === false) {
		searchGroups = '';
		emailValue = '';
		selectedIsGroupAdmin = false;
		selectedIsApplicationAdmin = false;
		selectedIsTopicAdmin = false;
		searchGroupResults = '';
	}

	// Selection
	let selectedGroupMembership = {
		userEmail: '',
		groupName: '',
		groupId: '',
		groupAdmin: '',
		applicationAdmin: '',
		topicAdmin: '',
		groupMembershipId: '',
		userId: ''
	};

	const reloadGroupMemberships = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/group_membership?page=${page}&size=${groupMembershipsPerPage}&filter=${searchString}`
				);
			} else if ($groupContext) {
				res = await httpAdapter.get(
					`/group_membership?page=${page}&size=${groupMembershipsPerPage}&group=${$groupContext.id}`
				);
			} else {
				res = await httpAdapter.get(
					`/group_membership?page=${page}&size=${groupMembershipsPerPage}`
				);
			}

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
			userValidityCheck.set(true);

			if (err.response.status === 500) onLoggedIn(false);
			errorMessage(errorMessages['group_membership']['loading.error.title'], err.message);
		}

		if ($groupAdminGroups?.length > 0) {
			isGroupAdmin = true;
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
		if (document.querySelector('#group-memberships-table') == null)
			promise = await reloadGroupMemberships();
	});

	const addGroupMembership = async (
		forwardedSelectedGroup,
		selectedIsGroupAdmin,
		selectedIsTopicAdmin,
		selectedIsApplicationAdmin,
		forwardedSearchGroups,
		emailValue
	) => {
		if (!forwardedSelectedGroup) {
			const groupId = await httpAdapter.get(
				`/groups?page=0&size=1&filter=${forwardedSearchGroups}`
			);
			if (
				groupId.data.content &&
				groupId.data.content[0]?.name.toUpperCase() === forwardedSearchGroups.toUpperCase()
			) {
				selectedGroup = groupId.data.content[0]?.id;
				searchGroupActive = false;
			}
		} else {
			selectedGroup = forwardedSelectedGroup;
		}

		await httpAdapter
			.post(`/group_membership`, {
				email: emailValue,
				permissionsGroup: selectedGroup,
				groupAdmin: selectedIsGroupAdmin,
				topicAdmin: selectedIsTopicAdmin,
				applicationAdmin: selectedIsApplicationAdmin
			})
			.catch((err) => {
				if (err.response.status === 403) {
					errorMessage(errorMessages['group_membership']['saving.error.title'], err.message);
				} else if (err.response.status === 400 || 401) {
					const decodedError = decodeError(Object.create(...err.response.data));
					errorMessage(
						errorMessages['group_membership']['adding.error.title'],
						errorMessages[decodedError.category][decodedError.code]
					);
				}
			});

		addGroupMembershipVisible = false;

		userValidityCheck.set(true);

		await reloadGroupMemberships();
	};

	const searchGroupMemberships = async (searchStr) => {
		const res = await httpAdapter.get(
			`/group_membership?filter=${searchStr}&size=${groupMembershipsPerPage}`
		);
		createGroupMembershipList(res.data.content, res.data.totalPages, res.data.totalSize);
		updateRetrievalTimestamp(retrievedTimestamps, 'users');
	};

	const searchGroup = async (searchGroupStr) => {
		setTimeout(async () => {
			searchGroupResults = await httpAdapter.get(
				`/groups?page=0&size=${groupsDropdownSuggestion}&filter=${searchGroupStr}`
			);
		}, waitTime);
	};

	const errorMessage = (errMsg, errObj) => {
		errorMsg = errMsg;
		errorObject = errObj;
		errorMessageVisible = true;
	};

	const errorMessageClear = () => {
		errorMessageVisible = false;
		errorMsg = '';
		errorObject = '';
	};

	const decodeError = (errorObject) => {
		errorObject = errorObject.code.replaceAll('-', '_');
		const cat = errorObject
			.substring(0, errorObject.lastIndexOf('.'))
			.substring(errorObject.indexOf('.') + 1, errorObject.length);
		const code = errorObject.substring(errorObject.lastIndexOf('.') + 1, errorObject.length);
		return { category: cat, code: code };
	};

	const updateGroupMembershipSelection = (selectedGM) => {
		updateGroupMembershipVisible = true;
		selectedGroupMembership.userEmail = selectedGM.userEmail;
		selectedGroupMembership.groupName = selectedGM.groupName;
		selectedGroupMembership.groupAdmin = selectedGM.groupAdmin;
		selectedGroupMembership.applicationAdmin = selectedGM.applicationAdmin;
		selectedGroupMembership.topicAdmin = selectedGM.topicAdmin;
		selectedGroupMembership.groupMembershipId = selectedGM.groupMembershipId;
		selectedGroupMembership.groupId = selectedGM.groupId;
		selectedGroupMembership.userId = selectedGM.userId;
	};

	const updateGroupMembership = async (gm) => {
		const data = {
			id: selectedGroupMembership.groupMembershipId,
			groupAdmin: gm.groupAdmin,
			topicAdmin: gm.topicAdmin,
			applicationAdmin: gm.applicationAdmin,
			permissionsGroup: selectedGroupMembership.groupId,
			email: selectedGroupMembership.userEmail
		};
		try {
			await httpAdapter.put(`/group_membership`, data);
			updateGroupMembershipVisible = false;

			userValidityCheck.set(true);

			await reloadGroupMemberships();
		} catch (err) {
			updateGroupMembershipVisible = false;
			const decodedError = decodeError(Object.create(...err.response.data));
			errorMessage(
				errorMessages['group_membership']['updating.error.title'],
				errorMessages[decodedError.category][decodedError.code]
			);
		}
	};

	const deleteSelectedGroupMemberships = async () => {
		try {
			for (const groupMembership of usersRowsSelected) {
				await httpAdapter.delete(`/group_membership`, {
					data: { id: groupMembership.groupMembershipId }
				});
			}

			userValidityCheck.set(true);

			await reloadGroupMemberships();
		} catch (err) {
			errorMessage(
				errorMessages['group_membership']['deleting.error.title'],
				errorMessages['group_membership']['not_found']
			);
		}
	};

	const deselectAllGroupMembershipCheckboxes = () => {
		usersAllRowsSelectedTrue = false;
		usersRowsSelectedTrue = false;
		usersRowsSelected = [];
		let checkboxes = document.querySelectorAll('.group-membership-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.group-membership-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};
</script>

{#key $refreshPage}
	{#if $isAuthenticated}
		{#await promise then _}
			{#if errorMessageVisible}
				<Modal
					title={errorMsg}
					errorMsg={true}
					errorDescription={errorObject}
					closeModalText={errorMessages['modal']['button.close']}
					on:cancel={() => errorMessageClear()}
				/>
			{/if}

			{#if addGroupMembershipVisible}
				<Modal
					title={messages['user']['add.title']}
					actionAddUser={true}
					email={true}
					group={true}
					adminRoles={true}
					on:cancel={() => (addGroupMembershipVisible = false)}
					on:addGroupMembership={async (e) => {
						addGroupMembershipVisible = false;
						await addGroupMembership(
							e.detail.selectedGroup,
							e.detail.selectedIsGroupAdmin,
							e.detail.selectedIsTopicAdmin,
							e.detail.selectedIsApplicationAdmin,
							e.detail.searchGroups,
							e.detail.emailValue
						);
						await reloadGroupMemberships(groupMembershipsCurrentPage);
					}}
				/>
			{/if}

			{#if updateGroupMembershipVisible}
				<Modal
					title={messages['user']['change.title']}
					actionEditUser={true}
					email={true}
					emailValue={selectedGroupMembership.userEmail}
					searchGroups={selectedGroupMembership.groupName}
					adminRoles={true}
					noneditable={true}
					{selectedGroupMembership}
					on:cancel={() => (updateGroupMembershipVisible = false)}
					on:reloadGroupMemberships={() => reloadGroupMemberships(groupMembershipsCurrentPage)}
					on:updateGroupMembership={(e) => updateGroupMembership(e.detail)}
				/>
			{/if}

			{#if deleteSelectedGroupMembershipsVisible}
				<Modal
					title="{messages['user']['delete.title.user']} {usersRowsSelected.length > 1
						? messages['user']['delete.multiple.user']
						: messages['user']['delete.single.user']}"
					actionDeleteUsers={true}
					on:cancel={() => {
						if (usersRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
							usersRowsSelected = [];

						deleteSelectedGroupMembershipsVisible = false;
					}}
					on:deleteGroupMemberships={async () => {
						await deleteSelectedGroupMemberships();

						reloadGroupMemberships();
						deselectAllGroupMembershipCheckboxes();
						deleteSelectedGroupMembershipsVisible = false;
					}}
				/>
			{/if}

			<div class="content">
				<form class="searchbox">
					<input
						data-cy="search-users-table"
						class="searchbox"
						type="search"
						placeholder={messages['user']['search.placeholder.user']}
						bind:value={searchString}
						on:blur={() => {
							searchString = searchString?.trim();
						}}
						on:keydown={(event) => {
							if (event.which === returnKey) {
								document.activeElement.blur();
								searchString = searchString?.trim();
							}
						}}
					/>
				</form>

				{#if searchString?.length > 0}
					<button
						class="button-blue"
						style="cursor: pointer; width: 4rem; height: 2.1rem"
						on:click={() => (searchString = '')}
					>
						{messages['user']['search.clear.button.user']}
					</button>
				{/if}

				<img
					src={deleteSVG}
					alt="options"
					class="dot"
					class:button-disabled={(!$isAdmin && !isGroupAdmin) || usersRowsSelected.length === 0}
					style="margin-left: 0.5rem; margin-right: 1rem"
					on:click={() => {
						if (usersRowsSelected.length > 0) deleteSelectedGroupMembershipsVisible = true;
					}}
					on:keydown={(event) => {
						if (event.which === returnKey) {
							if (usersRowsSelected.length > 0) deleteSelectedGroupMembershipsVisible = true;
						}
					}}
					on:mouseenter={() => {
						deleteMouseEnter = true;
						if ($isAdmin || isGroupAdmin) {
							if (usersRowsSelected.length === 0) {
								deleteToolip = messages['user']['delete.tooltip.user'];
								const tooltip = document.querySelector('#delete-users');
								setTimeout(() => {
									if (deleteMouseEnter) {
										tooltip.classList.remove('tooltip-hidden');
										tooltip.classList.add('tooltip');
										if ($groupMembershipList === undefined)
											tooltip.setAttribute('style', 'margin-left:10.5rem; margin-top: -1.8rem');
									}
								}, waitTime);
							}
						} else {
							deleteToolip = messages['user']['delete.tooltip.group.admin.required'];
							const tooltip = document.querySelector('#delete-users');
							setTimeout(() => {
								if (deleteMouseEnter) {
									tooltip.classList.remove('tooltip-hidden');
									tooltip.classList.add('tooltip');
									tooltip.setAttribute('style', 'margin-left:26.3rem; margin-top: -1.8rem');
								}
							}, waitTime);
						}
					}}
					on:mouseleave={() => {
						deleteMouseEnter = false;

						const tooltip = document.querySelector('#delete-users');
						setTimeout(() => {
							if (!deleteMouseEnter) {
								tooltip.classList.add('tooltip-hidden');
								tooltip.classList.remove('tooltip');
							}
						}, waitTime);
					}}
				/>
				<span
					id="delete-users"
					class="tooltip-hidden"
					style="margin-left: 28.5rem; margin-top: -1.8rem"
					>{deleteToolip}
				</span>

				<img
					data-cy="add-user"
					src={addSVG}
					alt="options"
					class="dot"
					class:button-disabled={(!$isAdmin && !isGroupAdmin) || !$groupContext}
					on:click={() => {
						if ($isAdmin || isGroupAdmin) {
							if ($groupContext) addGroupMembershipVisible = true;
							else showSelectGroupContext.set(true);
						}
					}}
					on:keydown={(event) => {
						if (event.which === returnKey) {
							if ($isAdmin || isGroupAdmin) if ($groupContext) addGroupMembershipVisible = true;
						}
					}}
					on:mouseenter={() => {
						addMouseEnter = true;
						if (!$isAdmin && !isGroupAdmin) {
							addTooltip = messages['user']['add.tooltip.group.admin.required'];
							const tooltip = document.querySelector('#add-users');
							setTimeout(() => {
								if (addMouseEnter) {
									tooltip.classList.remove('tooltip-hidden');
									tooltip.classList.add('tooltip');
								}
							}, waitTime);
						} else if (!$groupContext && $isAdmin) {
							addTooltip = messages['user']['add.tooltip.select.group'];
							const tooltip = document.querySelector('#add-users');
							setTimeout(() => {
								if (addMouseEnter) {
									tooltip.classList.remove('tooltip-hidden');
									tooltip.classList.add('tooltip');
									tooltip.setAttribute('style', 'margin-left:25rem; margin-top: -1.8rem');

									if ($groupMembershipList === undefined)
										tooltip.setAttribute('style', 'margin-left:7rem; margin-top: -1.8rem');
								}
							}, 1000);
						}
					}}
					on:mouseleave={() => {
						addMouseEnter = false;
						const tooltip = document.querySelector('#add-users');
						setTimeout(() => {
							if (!addMouseEnter) {
								tooltip.classList.add('tooltip-hidden');
								tooltip.classList.remove('tooltip');
							}
						}, waitTime);
					}}
				/>
				<span id="add-users" class="tooltip-hidden" style="margin-left: 24rem; margin-top: -1.8rem">
					{addTooltip}
				</span>

				{#if $groupMembershipList?.length > 0}
					<table
						data-cy="users-table"
						id="group-memberships-table"
						style="margin-top:0.5rem; min-width: 50rem; width:max-content"
					>
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								{#if $isAdmin || isGroupAdmin}
									<td>
										<input
											tabindex="-1"
											type="checkbox"
											class="group-membership-checkbox"
											style="margin-right: 0.5rem"
											bind:indeterminate={usersRowsSelectedTrue}
											on:click={(e) => {
												if (e.target.checked) {
													usersRowsSelected = $groupMembershipList;
													usersRowsSelectedTrue = false;
													usersAllRowsSelectedTrue = true;
												} else {
													usersAllRowsSelectedTrue = false;
													usersRowsSelectedTrue = false;
													usersRowsSelected = [];
												}
											}}
											checked={usersAllRowsSelectedTrue}
										/>
									</td>
								{/if}
								<td style="font-stretch:ultra-condensed; width:fit-content">
									{messages['user']['table.user.column.one']}
								</td>
								<td style="font-stretch:ultra-condensed; width:fit-content">
									{messages['user']['table.user.column.two']}
								</td>
								<td style="font-stretch:ultra-condensed; width:5.25rem">
									<center>{messages['user']['table.user.column.three']}</center>
								</td>
								<td style="font-stretch:ultra-condensed; width:5rem">
									<center>{messages['user']['table.user.column.four']}</center>
								</td>
								<td style="font-stretch:ultra-condensed; width:7.4rem">
									<center>{messages['user']['table.user.column.five']}</center>
								</td>
								<td /><td />
							</tr>
						</thead>
						<tbody>
							{#each $groupMembershipList as groupMembership, i}
								<tr class:highlighted={groupMembership.userEmail === $userEmail}>
									{#if $isAdmin || isGroupAdmin}
										<td style="width: 2rem">
											<input
												tabindex="-1"
												type="checkbox"
												style="margin-right: 0.5rem"
												class="group-membership-checkbox"
												checked={usersAllRowsSelectedTrue}
												on:change={(e) => {
													if (e.target.checked === true) {
														usersRowsSelected.push(groupMembership);
														// reactive statement
														usersRowsSelected = usersRowsSelected;
														usersRowsSelectedTrue = true;
													} else {
														usersRowsSelected = usersRowsSelected.filter(
															(selection) => selection !== groupMembership
														);
														if (usersRowsSelected.length === 0) {
															usersRowsSelectedTrue = false;
														}
													}
												}}
											/>
										</td>
									{/if}
									<td style="width:fit-content">{groupMembership.userEmail}</td>
									<td style="width:fit-content">{groupMembership.groupName}</td>
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

									{#if $isAdmin || $groupAdminGroups?.some((group) => group.groupName === groupMembership.groupName)}
										<td
											style="cursor: pointer; text-align: right"
											on:keydown={(event) => {
												if (event.which === returnKey) {
													updateGroupMembershipSelection(groupMembership);
												}
											}}
										>
											<!-- svelte-ignore a11y-click-events-have-key-events -->
											<img
												data-cy="edit-users-icon"
												src={editSVG}
												height="17rem"
												width="17rem"
												style="vertical-align: -0.225rem"
												alt="edit user"
												on:click={() => updateGroupMembershipSelection(groupMembership)}
											/>
										</td>
										<td
											style="cursor: pointer; text-align: right; padding-right: 0.25rem; width: 1rem"
										>
											<!-- svelte-ignore a11y-click-events-have-key-events -->
											<img
												data-cy="delete-users-icon"
												src={deleteSVG}
												height="27px"
												width="27px"
												style="vertical-align: -0.5rem"
												alt="delete user"
												on:click={() => {
													if (!usersRowsSelected.some((gm) => gm === groupMembership))
														usersRowsSelected.push(groupMembership);
													deleteSelectedGroupMembershipsVisible = true;
												}}
											/>
										</td>
									{:else}
										<td /><td />
									{/if}
								</tr>
							{/each}
						</tbody>
					</table>
				{:else if !$groupContext}
					<p>
						{messages['user']['empty.users']}
						<br />
						{messages['user']['empty.users.action']}
					</p>
				{:else if $groupContext && ($permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isGroupAdmin === true) || $isAdmin)}
					<p>
						{messages['user']['empty.users']} <br />
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<span
							class="link"
							on:click={() => {
								if ($groupContext) addGroupMembershipVisible = true;
								else showSelectGroupContext.set(true);
							}}
						>
							{messages['user']['empty.users.action.two']}
						</span>
						{messages['user']['empty.users.action.result']}
					</p>
				{/if}

				<br />
			</div>

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
		{/await}
	{/if}
{/key}

<style>
	.content {
		width: 100%;
		min-width: 32rem;
		margin-right: 1rem;
	}

	.dot {
		float: right;
	}

	input {
		vertical-align: middle;
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
</style>

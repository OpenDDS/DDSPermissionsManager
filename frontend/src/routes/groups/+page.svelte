<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAdmin, isAuthenticated } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import groups from '../../stores/groups';
	import Modal from '../../lib/Modal.svelte';
	import RetrievedTimestamp from '../../lib/RetrievedTimestamp.svelte';
	import refreshPage from '../../stores/refreshPage';
	import { goto } from '$app/navigation';
	import { browser } from '$app/environment';
	import deleteSVG from '../../icons/delete.svg';
	import editSVG from '../../icons/edit.svg';
	import addSVG from '../../icons/add.svg';
	import groupSVG from '../../icons/groups.svg';
	import topicsSVG from '../../icons/topics.svg';
	import appsSVG from '../../icons/apps.svg';
	import pageforwardSVG from '../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../icons/pagefirst.svg';
	import pagelastSVG from '../../icons/pagelast.svg';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import groupContext from '../../stores/groupContext';
	import singleGroupCheck from '../../stores/singleGroupCheck';
	import userEmail from '../../stores/userEmail';
	import retrievedTimestamps from '../../stores/retrievedTimestamps.js';
	import tooltips from '$lib/tooltips.json';
	import contextMessage from '../../stores/contextMessage';
	import userValidityCheck from '../../stores/userValidityCheck';
	import groupsTotalPages from '../../stores/groupsTotalPages';
	import groupsTotalSize from '../../stores/groupsTotalSize';
	import permissionsForAllGroups from '../../stores/permissionsForAllGroups';
	import createItem from '../../stores/createItem';
	import modalOpen from '../../stores/modalOpen';
	import GroupDetails from './GroupDetails.svelte';
	import { updateRetrievalTimestamp } from '../../utils.js';

	export let data;
	export let errors;

	$: if ($groupContext === 'clear') {
		groupContext.set();
		singleGroupCheck.set();
		reloadAllGroups();
	}

	// Redirects the User to the Login screen if not authenticated
	$: if (browser) {
		setTimeout(() => {
			if (!$isAuthenticated) goto(`/`, true);
		}, waitTime);
	}

	// Locks the background scroll when modal is open
	$: if (browser && (addGroupVisible || deleteGroupVisible || errorMessageVisible)) {
		document.body.classList.add('modal-open');
	} else if (browser && !(addGroupVisible || deleteGroupVisible || errorMessageVisible)) {
		document.body.classList.remove('modal-open');
	}

	// Checkboxes selection
	$: if ($groups?.length === groupsRowsSelected?.length) {
		groupsRowsSelectedTrue = false;
		groupsAllRowsSelectedTrue = true;
	} else if (groupsRowsSelected?.length > 0) {
		groupsRowsSelectedTrue = true;
	} else {
		groupsAllRowsSelectedTrue = false;
	}

	// Messages
	let deleteToolip;
	let deleteMouseEnter = false;

	let addTooltip;
	let addMouseEnter = false;

	let activateToolip = messages['group']['activate.tooltip'];

	let activateMouseEnter = new Array($groups?.length).fill(false);

	let createUserMouseEnter = new Array($groups?.length).fill(false);
	let createTopicMouseEnter = new Array($groups?.length).fill(false);
	let createApplicationMouseEnter = new Array($groups?.length).fill(false);

	// Promises
	let promise;

	// Group Detail View
	let groupDetailView = false;
	let selectedGroup;

	// Constants
	const returnKey = 13;
	const waitTime = 1000;
	const searchStringLength = 3;

	// Error Handling
	let errorMsg, errorObject;

	// Search
	let searchString;
	let timer;

	// Modals
	let errorMessageVisible = false;
	let addGroupVisible = false;
	let deleteGroupVisible = false;
	let editGroupVisible = false;

	// Tables
	let groupsRowsSelected = [];
	let groupsRowsSelectedTrue = false;
	let groupsAllRowsSelectedTrue = false;

	// Pagination
	let groupsPerPage = 10;
	let groupsCurrentPage = 0;

	// Selection
	let selectedGroupId, selectedGroupName, selectedGroupDescription, selectedGroupPublic;

	// Search Feature
	$: if (searchString?.trim().length >= searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			searchGroups(searchString.trim());
		}, waitTime);
	}

	$: if (searchString?.trim().length < searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadAllGroups();
		}, waitTime);
	}

	const refreshGroups = () => {
		if (!$modalOpen) {
			promise = reloadAllGroups();
		} else {
			setTimeout(refreshGroups, 5000);
		}
	};

	const reloadAllGroups = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/groups?page=${page}&size=${groupsPerPage}&filter=${searchString}`
				);
			} else {
				res = await httpAdapter.get(`/groups?page=${page}&size=${groupsPerPage}`);
			}
			if (res.data) {
				groupsTotalPages.set(res.data.totalPages);
				groupsTotalSize.set(res.data.totalSize);
			}
			groups.set(res.data.content);
			groupsTotalPages.set(res.data.totalPages);
			groupsCurrentPage = page;

			updateRetrievalTimestamp(retrievedTimestamps, 'groups');
		} catch (err) {
			userValidityCheck.set(true);

			errorMessage(errorMessages['group']['loading.error.title'], err.message);
		}
	};

	const getPermissionsForAllGroups = async () => {
		if ($userEmail) {
			const res = await httpAdapter.get(
				`/group_membership?page=0&size=${groupsPerPage}&filter=${$userEmail}`
			);
			if (res?.data?.content) permissionsForAllGroups.set(res.data.content);
		}
	};

	onMount(async () => {
		headerTitle.set(messages['group']['title']);
		detailView.set();

		if (document.querySelector('#groups-table') == null) {
			await getPermissionsForAllGroups();
			promise = await reloadAllGroups();
		}
	});

	const searchGroups = async (searchStr) => {
		const res = await httpAdapter.get(`/groups?page=0&size=${groupsPerPage}&filter=${searchStr}`);
		if (res.data.content) {
			groups.set(res.data.content);
		} else {
			groups.set([]);
		}
		groupsTotalPages.set(res.data.totalPages);
		if (res.data.totalSize !== undefined) groupsTotalSize.set(res.data.totalSize);
		groupsCurrentPage = 0;

		updateRetrievalTimestamp(retrievedTimestamps, 'groups');
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

	const addGroup = async (newGroupName, newGroupDescription, newGroupIsPublic) => {
		await httpAdapter
			.post(`/groups/save/`, {
				name: newGroupName,
				description: newGroupDescription,
				public: newGroupIsPublic
			})
			.catch((err) => {
				addGroupVisible = false;
				if (err.response.status === 401) {
					errorMessage(
						errorMessages['group']['adding.error.title'],
						errorMessages['group']['error.unauthorized']
					);
				} else {
					const decodedError = decodeError(Object.create(...err.response.data));
					errorMessage(
						errorMessages['group']['adding.error.title'],
						errorMessages[decodedError.category][decodedError.code]
					);
				}
			});

		searchString = '';
		addGroupVisible = false;
		groupsCurrentPage = 0;

		await reloadAllGroups();
	};

	const editGroupName = async (groupId, groupNewName, groupNewDescription, groupNewPublic) => {
		await httpAdapter
			.post(`/groups/save/`, {
				id: groupId,
				name: groupNewName,
				description: groupNewDescription,
				public: groupNewPublic
			})
			.catch((err) => {
				errorMessage(errorMessages['group']['editing.error.title'], err.message);
			});

		await reloadAllGroups();

		editGroupVisible = false;
	};

	const deleteSelectedGroups = async () => {
		try {
			groupContext.set();
			for (const group of groupsRowsSelected) {
				await httpAdapter.delete(`/groups/${group.id}`);
			}
		} catch (err) {
			deleteGroupVisible = false;
			if (err.response.status === 401) {
				errorMessage(
					errorMessages['group']['deleting.error.title'],
					errorMessages['group']['error.unauthorized']
				);
			} else {
				const decodedError = decodeError(Object.create(...err.response.data));
				errorMessage(
					errorMessages['group']['deleting.error.title'],
					errorMessages[decodedError.category][decodedError.code]
				);
			}
		}
	};

	const findPermission = (group, permissionType) => {
		if ($isAdmin) return true;
		else if ($permissionsForAllGroups) {
			return $permissionsForAllGroups?.find(
				(groupPermission) =>
					groupPermission?.permissionsGroup === group.id && groupPermission[permissionType] === true
			);
		} else return false;
	};

	const deselectAllGroupsCheckboxes = () => {
		groupsAllRowsSelectedTrue = false;
		groupsRowsSelectedTrue = false;
		groupsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.groups-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.groups-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const decodeError = (errorObject) => {
		errorObject = errorObject.code.replaceAll('-', '_');
		const cat = errorObject.substring(0, errorObject.indexOf('.'));
		const code = errorObject.substring(errorObject.indexOf('.') + 1, errorObject.length);
		return { category: cat, code: code };
	};
</script>

<svelte:head>
	<title>{messages['group']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Groups" />
</svelte:head>

{#key $refreshPage}
	{#if $isAuthenticated}
		{#await promise then _}
			{#if errorMessageVisible}
				<Modal
					title={errorMsg}
					errorMsg={true}
					errorDescription={errorObject}
					closeModalText={errorMessages['modal']['button.close']}
					on:cancel={() => {
						errorMessageVisible = false;
						errorMessageClear();
					}}
				/>
			{/if}

			{#if deleteGroupVisible}
				<Modal
					title="{messages['group']['delete.title']} {groupsRowsSelected.length > 1
						? messages['group']['delete.multiple']
						: messages['group']['delete.single']}"
					actionDeleteGroups={true}
					on:deleteGroups={async () => {
						await deleteSelectedGroups();
						reloadAllGroups();

						deselectAllGroupsCheckboxes();
						deleteGroupVisible = false;
					}}
					on:cancel={() => {
						if (groupsRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
							groupsRowsSelected = [];

						deleteGroupVisible = false;
					}}
				/>
			{/if}

			{#if addGroupVisible}
				<Modal
					title={messages['group']['add.title']}
					actionAddGroup={true}
					groupNewName={true}
					on:addGroup={(e) =>
						addGroup(e.detail.newGroupName, e.detail.newGroupDescription, e.detail.newGroupPublic)}
					on:cancel={() => (addGroupVisible = false)}
				/>
			{/if}

			{#if editGroupVisible}
				<Modal
					title={messages['group']['edit.title']}
					actionEditGroup={true}
					groupCurrentName={selectedGroupName}
					groupNewName={true}
					groupId={selectedGroupId}
					groupCurrentDescription={selectedGroupDescription}
					groupCurrentPublic={selectedGroupPublic}
					on:addGroup={(e) => {
						editGroupName(
							e.detail.groupId,
							e.detail.newGroupName,
							e.detail.newGroupDescription,
							e.detail.newGroupPublic
						);
					}}
					on:cancel={() => (editGroupVisible = false)}
				/>
			{/if}

			{#if groupDetailView}
				<GroupDetails
					group={selectedGroup}
					on:groupList={() => {
						headerTitle.set(messages['group']['title']);
						detailView.set();
						groupDetailView = false;
					}}
				/>
			{/if}

			{#if $groupsTotalSize !== undefined && $groupsTotalSize != NaN && !groupDetailView}
				<div class="content">
					<h1 data-cy="groups">{messages['group']['title']}</h1>

					<form class="searchbox">
						<input
							data-cy="search-groups-table"
							class="searchbox"
							type="search"
							placeholder={messages['group']['search.placeholder']}
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
							{messages['group']['search.clear.button']}
						</button>
					{/if}

					<img
						src={deleteSVG}
						alt="options"
						class="dot"
						class:button-disabled={!$isAdmin || groupsRowsSelected.length === 0}
						style="margin-left: 0.5rem; margin-right: 1rem"
						on:click={() => {
							if (groupsRowsSelected.length > 0) deleteGroupVisible = true;
						}}
						on:keydown={(event) => {
							if (event.which === returnKey) {
								if (groupsRowsSelected.length > 0) deleteGroupVisible = true;
							}
						}}
						on:mouseenter={() => {
							deleteMouseEnter = true;
							if ($isAdmin) {
								deleteToolip = messages['group']['delete.tooltip'];
								if (groupsRowsSelected.length === 0) {
									const tooltip = document.querySelector('#delete-groups');
									setTimeout(() => {
										if (deleteMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
											if ($groupContext === null || $groupContext === undefined)
												tooltip.setAttribute('style', 'margin-left:20rem; margin-top: -1.8rem');
											else tooltip.setAttribute('style', 'margin-left:20rem; margin-top: -1.8rem');
										}
									}, 1000);
								}
							} else {
								deleteToolip = messages['group']['delete.tooltip.superuser.required'];
								const tooltip = document.querySelector('#delete-groups');
								setTimeout(() => {
									if (deleteMouseEnter) {
										tooltip.classList.remove('tooltip-hidden');
										tooltip.classList.add('tooltip');
										tooltip.setAttribute('style', 'margin-left:9.2rem; margin-top: -1.8rem');
									}
								}, 1000);
							}
						}}
						on:mouseleave={() => {
							deleteMouseEnter = false;

							if (groupsRowsSelected.length === 0) {
								const tooltip = document.querySelector('#delete-groups');
								setTimeout(() => {
									if (!deleteMouseEnter) {
										tooltip.classList.add('tooltip-hidden');
										tooltip.classList.remove('tooltip');
									}
								}, 1000);
							}
						}}
					/>
					<span
						id="delete-groups"
						class="tooltip-hidden"
						style="margin-left: 33.2rem; margin-top: -1.8rem"
						>{deleteToolip}
					</span>
					<img
						data-cy="add-group"
						src={addSVG}
						alt="options"
						class="dot"
						class:button-disabled={!$isAdmin}
						on:click={() => {
							if ($isAdmin) addGroupVisible = true;
						}}
						on:keydown={(event) => {
							if (event.which === returnKey) {
								addGroupVisible = true;
							}
						}}
						on:mouseenter={() => {
							addMouseEnter = true;
							if (!$isAdmin) {
								addTooltip = messages['group']['add.tooltip.superuser.required'];
								const tooltip = document.querySelector('#add-applications');
								setTimeout(() => {
									if (addMouseEnter) {
										tooltip.classList.remove('tooltip-hidden');
										tooltip.classList.add('tooltip');
									}
								}, waitTime);
							}
						}}
						on:mouseleave={() => {
							addMouseEnter = false;
							const tooltip = document.querySelector('#add-applications');
							setTimeout(() => {
								if (!addMouseEnter) {
									tooltip.classList.add('tooltip-hidden');
									tooltip.classList.remove('tooltip');
								}
							}, waitTime);
						}}
					/>
					<span
						id="add-applications"
						class="tooltip-hidden"
						style="margin-left: 7rem; margin-top: -1.8rem"
						>{addTooltip}
					</span>

					{#if $groups?.length > 0}
						<table
							class="main"
							id="groups-table"
							data-cy="groups-table"
							style="margin-top: 0.5rem; min-width: 45rem; width:max-content"
						>
							<thead>
								<tr style="border-top: 1px solid black; border-bottom: 2px solid">
									{#if $isAdmin}
										<td>
											<input
												tabindex="-1"
												type="checkbox"
												class="groups-checkbox"
												style="margin-right: 0.5rem; vertical-align: middle;"
												bind:indeterminate={groupsRowsSelectedTrue}
												on:click={(e) => {
													if (e.target.checked) {
														groupsRowsSelected = $groups;
														groupsRowsSelectedTrue = false;
														groupsAllRowsSelectedTrue = true;
													} else {
														groupsAllRowsSelectedTrue = false;
														groupsRowsSelectedTrue = false;
														groupsRowsSelected = [];
													}
												}}
												checked={groupsAllRowsSelectedTrue}
											/>
										</td>
									{/if}
									<td class="header-column" style="width: 5rem; text-align:center"
										>{messages['group']['table.column.one']}
									</td>
									<td class="header-column" style="min-width: 7rem">
										{messages['group']['table.column.two']}
									</td>
									<td class="header-column" style="text-align:center; width: 7rem">
										{messages['group']['table.column.three']}
									</td>
									<td class="header-column" style="width: 4rem; text-align:center">
										{messages['group']['table.column.four']}
									</td>
									<td class="header-column" style="width: 4rem; text-align:center">
										{messages['group']['table.column.five']}
									</td>
									<td class="header-column" style="width: 4rem; text-align:center">
										{messages['group']['table.column.six']}
									</td>
									<td
										class="header-column"
										style="width: 4rem; text-align:right; padding-right: 1rem"
									>
										{messages['group']['table.column.seven']}
									</td>
								</tr>
							</thead>
							<tbody>
								{#each $groups as group, i}
									<tr>
										{#if $isAdmin}
											<td style="width: 2rem">
												<input
													tabindex="-1"
													type="checkbox"
													class="groups-checkbox"
													style="vertical-align: middle;"
													checked={groupsAllRowsSelectedTrue}
													on:change={(e) => {
														if (e.target.checked === true) {
															groupsRowsSelected.push(group);
															// reactive statement
															groupsRowsSelected = groupsRowsSelected;
															groupsRowsSelectedTrue = true;
														} else {
															groupsRowsSelected = groupsRowsSelected.filter(
																(selection) => selection !== group
															);
															if (groupsRowsSelected.length === 0) {
																groupsRowsSelectedTrue = false;
															}
														}
													}}
												/>
											</td>
										{/if}

										<td style="text-align: center; cursor: pointer">
											<!-- svelte-ignore a11y-click-events-have-key-events -->
											<img
												data-cy="activate-group-context{i}"
												src={groupSVG}
												alt="select group"
												width="27rem"
												height="27rem"
												style="vertical-align: middle;cursor: pointer"
												on:click={() => {
													if (!$groupContext) {
														groupContext.set(group);
														contextMessage.set(true);
													} else if ($groupContext !== group) {
														groupContext.set(group);
														contextMessage.set(true);
													} else groupContext.set('clear');
												}}
												on:mouseenter={() => {
													activateMouseEnter[i] = true;
													const tooltip = document.querySelector(`#activate-groups${i}`);
													setTimeout(() => {
														if (activateMouseEnter[i]) {
															tooltip.classList.remove('tooltip-hidden');
															tooltip.classList.add('tooltip');
														}
													}, waitTime);
												}}
												on:mouseleave={() => {
													activateMouseEnter[i] = false;
													const tooltip = document.querySelector(`#activate-groups${i}`);
													setTimeout(() => {
														if (!activateMouseEnter[i]) {
															tooltip.classList.add('tooltip-hidden');
															tooltip.classList.remove('tooltip');
														}
													}, waitTime);
												}}
											/>
										</td>
										<!-- svelte-ignore a11y-click-events-have-key-events -->
										<td
											style="width: max-content; cursor: pointer"
											class:highlighted={group.name === $groupContext?.name}
											on:click={() => {
												selectedGroup = group;
												groupDetailView = true;
												history.pushState({ path: '/groups' }, 'My Groups', '/groups');
												goto(`/groups/details/`);
											}}
										>
											{group.name}
										</td>

										<td>
											<div
												style="display:inline-flex; vertical-align:middle; justify-content: center; width: 7rem"
											>
												<!-- svelte-ignore a11y-click-events-have-key-events -->
												<img
													src={groupSVG}
													alt="create new user"
													width="23rem"
													height="23rem"
													style="margin-right: 0.4rem"
													class:permission-badges-blue={findPermission(group, 'groupAdmin')}
													class:permission-badges-grey={!findPermission(group, 'groupAdmin')}
													disabled={!findPermission(group, 'groupAdmin')}
													on:mouseenter={() => {
														createUserMouseEnter[i] = true;
														const tooltip = document.querySelector(`#user-create${i}`);
														setTimeout(() => {
															if (createUserMouseEnter[i]) {
																tooltip.classList.remove('tooltip-hidden');
																tooltip.classList.add('tooltip');
															}
														}, 1000);
													}}
													on:mouseleave={() => {
														createUserMouseEnter[i] = false;
														const tooltip = document.querySelector(`#user-create${i}`);
														setTimeout(() => {
															if (!createUserMouseEnter[i]) {
																tooltip.classList.add('tooltip-hidden');
																tooltip.classList.remove('tooltip');
															}
														}, 1000);
													}}
													on:click={() => {
														if (findPermission(group, 'groupAdmin')) {
															groupContext.set(group);
															createItem.set('user');
															goto(`/users`, true);
														}
													}}
												/>

												<span
													id="user-create{i}"
													class="tooltip-hidden"
													style="margin-top: 1.8rem; margin-left: -3rem"
												>
													{#if findPermission(group, 'groupAdmin')}
														{tooltips['createUserAllowed']} {group.name}
													{:else}
														{tooltips['createUserNotAllowed']}
													{/if}
												</span>

												<!-- svelte-ignore a11y-click-events-have-key-events -->
												<img
													src={topicsSVG}
													alt="create new topic"
													width="23rem"
													height="23rem"
													style="margin-right: 0.2rem"
													class:permission-badges-blue={findPermission(group, 'topicAdmin')}
													class:permission-badges-grey={!findPermission(group, 'topicAdmin')}
													disabled={!findPermission(group, 'topicAdmin')}
													on:mouseenter={() => {
														createTopicMouseEnter[i] = true;
														const tooltip = document.querySelector(`#topic-create${i}`);
														setTimeout(() => {
															if (createTopicMouseEnter[i]) {
																tooltip.classList.remove('tooltip-hidden');
																tooltip.classList.add('tooltip');
															}
														}, 1000);
													}}
													on:mouseleave={() => {
														createTopicMouseEnter[i] = false;
														const tooltip = document.querySelector(`#topic-create${i}`);
														setTimeout(() => {
															if (!createTopicMouseEnter[i]) {
																tooltip.classList.add('tooltip-hidden');
																tooltip.classList.remove('tooltip');
															}
														}, 1000);
													}}
													on:click={() => {
														if (findPermission(group, 'topicAdmin')) {
															groupContext.set(group);
															createItem.set('topic');
															goto(`/topics`, true);
														}
													}}
												/>

												<span id="topic-create{i}" class="tooltip-hidden" style="margin-top: 1.8rem"
													>{#if findPermission(group, 'topicAdmin')}
														{tooltips['createTopicAllowed']} {group.name}
													{:else}
														{tooltips['createTopicNotAllowed']}
													{/if}
												</span>

												<!-- svelte-ignore a11y-click-events-have-key-events -->
												<img
													src={appsSVG}
													alt="create application"
													width="23rem"
													height="23rem"
													class:permission-badges-blue={findPermission(group, 'applicationAdmin')}
													class:permission-badges-grey={!findPermission(group, 'applicationAdmin')}
													disabled={!findPermission(group, 'applicationAdmin')}
													on:mouseenter={() => {
														createApplicationMouseEnter[i] = true;
														const tooltip = document.querySelector(`#application-create${i}`);
														setTimeout(() => {
															if (createApplicationMouseEnter[i]) {
																tooltip.classList.remove('tooltip-hidden');
																tooltip.classList.add('tooltip');
															}
														}, 1000);
													}}
													on:mouseleave={() => {
														createApplicationMouseEnter[i] = false;
														const tooltip = document.querySelector(`#application-create${i}`);
														setTimeout(() => {
															if (!createApplicationMouseEnter[i]) {
																tooltip.classList.add('tooltip-hidden');
																tooltip.classList.remove('tooltip');
															}
														}, 1000);
													}}
													on:click={() => {
														if (findPermission(group, 'applicationAdmin')) {
															groupContext.set(group);
															createItem.set('application');
															goto(`/applications`, true);
														}
													}}
												/>

												<span
													id="application-create{i}"
													class="tooltip-hidden"
													style="margin-top: 1.8rem; margin-left: 3rem"
													>{#if findPermission(group, 'applicationAdmin')}
														{tooltips['createApplicationAllowed']} {group.name}
													{:else}
														{tooltips['createApplicationNotAllowed']}
													{/if}
												</span>
											</div>
										</td>

										<td style="width: max-content">
											<center>
												<a
													tabindex="-1"
													style="vertical-align: middle"
													href="/users"
													on:click={() => groupContext.set(group)}>{group.membershipCount}</a
												>
											</center>
										</td>

										<td style="width: max-content">
											<center>
												<a
													tabindex="-1"
													style="vertical-align: middle"
													href="/topics"
													on:click={() => groupContext.set(group)}>{group.topicCount}</a
												>
											</center>
										</td>

										<td style="width: max-content">
											<center>
												<a
													tabindex="-1"
													style="vertical-align: middle"
													href="/applications"
													on:click={() => groupContext.set(group)}>{group.applicationCount}</a
												>
											</center>
										</td>

										<td style="width: max-content">
											<center>
												<a
													tabindex="-1"
													style="vertical-align: middle"
													href="/grants"
													on:click={() => groupContext.set(group)}>{group.grantCount}</a
												>
											</center>
										</td>

										<span
											id="activate-groups{i}"
											class="tooltip-hidden"
											class:activate-no-context={!$groupContext?.id}
											class:activate-context={$groupContext?.id}
											>{activateToolip}
										</span>

										{#if $isAdmin}
											<td style="cursor: pointer; text-align: right; padding-right: 0.25rem">
												<!-- svelte-ignore a11y-click-events-have-key-events -->
												<img
													data-cy="edit-group-icon-{group.name}"
													src={editSVG}
													alt="edit group"
													style="cursor: pointer; vertical-align: -0.25rem"
													height="17rem"
													width="17rem"
													on:click={() => {
														editGroupVisible = true;
														selectedGroupId = group.id;
														selectedGroupName = group.name;
														selectedGroupDescription = group.description;
														selectedGroupPublic = group.public;
													}}
												/>
											</td>

											<td style="cursor: pointer; text-align: right; padding-right: 0.25rem">
												<!-- svelte-ignore a11y-click-events-have-key-events -->
												<img
													data-cy="delete-group-icon-{group.name}"
													src={deleteSVG}
													alt="delete group"
													style="cursor: pointer; vertical-align: -0.5rem"
													height="27rem"
													on:click={() => {
														if (!groupsRowsSelected.some((grp) => grp === group))
															groupsRowsSelected.push(group);
														deleteGroupVisible = true;
													}}
												/>
											</td>
										{/if}
									</tr>
								{/each}
							</tbody>
						</table>
					{:else}
						<p>
							{messages['group']['empty.groups']}&nbsp;

							<!-- svelte-ignore a11y-click-events-have-key-events -->
							<span class="link" on:click={() => (addGroupVisible = true)}>
								{messages['group']['empty.groups.action']}
							</span>
							{messages['group']['empty.groups.action.result']}
						</p>
					{/if}
				</div>

				<div class="pagination">
					<span>{messages['pagination']['rows.per.page']}</span>
					<select
						tabindex="-1"
						on:change={(e) => {
							groupsPerPage = e.target.value;
							reloadAllGroups();
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
						{#if $groupsTotalSize > 0}
							{1 + groupsCurrentPage * groupsPerPage}
						{:else}
							0
						{/if}
						- {Math.min(groupsPerPage * (groupsCurrentPage + 1), $groupsTotalSize)} of
						{$groupsTotalSize}
					</span>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagefirstSVG}
						alt="first page"
						class="pagination-image"
						class:disabled-img={groupsCurrentPage === 0}
						on:click={() => {
							deselectAllGroupsCheckboxes();

							if (groupsCurrentPage > 0) {
								groupsCurrentPage = 0;
								reloadAllGroups();
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagebackwardsSVG}
						alt="previous page"
						class="pagination-image"
						class:disabled-img={groupsCurrentPage === 0}
						on:click={() => {
							deselectAllGroupsCheckboxes();

							if (groupsCurrentPage > 0) {
								groupsCurrentPage--;
								reloadAllGroups(groupsCurrentPage);
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pageforwardSVG}
						alt="next page"
						class="pagination-image"
						class:disabled-img={groupsCurrentPage + 1 === $groupsTotalPages ||
							$groups?.length === undefined}
						on:click={() => {
							deselectAllGroupsCheckboxes();

							if (groupsCurrentPage + 1 < $groupsTotalPages) {
								groupsCurrentPage++;
								reloadAllGroups(groupsCurrentPage);
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagelastSVG}
						alt="last page"
						class="pagination-image"
						class:disabled-img={groupsCurrentPage + 1 === $groupsTotalPages ||
							$groups?.length === undefined}
						on:click={() => {
							deselectAllGroupsCheckboxes();

							if (groupsCurrentPage < $groupsTotalPages) {
								groupsCurrentPage = $groupsTotalPages - 1;
								reloadAllGroups(groupsCurrentPage);
							}
						}}
					/>
				</div>
				<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['groups']} />
				<p style="margin-top: 8rem">{messages['footer']['message']}</p>
			{/if}
		{/await}
	{/if}
{/key}

<style>
	.content {
		width: 100%;
		min-width: 45rem;
		margin-right: 1rem;
	}

	.dot {
		float: right;
	}

	.activate-context {
		margin-left: -37rem;
		margin-top: -1rem;
	}

	.activate-no-context {
		margin-left: -37rem;
		margin-top: -1rem;
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

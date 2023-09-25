<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import refreshPage from '../../stores/refreshPage';
	import Modal from '../../lib/Modal.svelte';
	import TopicSetDetails from './TopicSetDetails.svelte';
	import RetrievedTimestamp from '../../lib/RetrievedTimestamp.svelte';
	import { goto } from '$app/navigation';
	import { browser } from '$app/environment';
	import userValidityCheck from '../../stores/userValidityCheck';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import deleteSVG from '../../icons/delete.svg';
	import detailSVG from '../../icons/detail.svg';
	import addSVG from '../../icons/add.svg';
	import pageforwardSVG from '../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../icons/pagefirst.svg';
	import pagelastSVG from '../../icons/pagelast.svg';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import groupContext from '../../stores/groupContext';
	import showSelectGroupContext from '../../stores/showSelectGroupContext';
	import singleGroupCheck from '../../stores/singleGroupCheck';
	import createItem from '../../stores/createItem';
	import topicsTotalSize from '../../stores/topicsTotalSize';
	import topicsTotalPages from '../../stores/topicsTotalPages';
	import topicSets from '../../stores/topicSets';
	import retrievedTimestamps from '../../stores/retrievedTimestamps';
	import { updateRetrievalTimestamp } from '../../utils.js';

	// Group Context
	$: if ($groupContext?.id) reloadAllTopicSets();

	$: if ($groupContext === 'clear') {
		groupContext.set();
		singleGroupCheck.set();
		selectedGroup = '';
		reloadAllTopicSets();
	}

	// Permission Badges Create
	$: if ($createItem === 'topic') {
		createItem.set(false);
		addTopicVisible = true;
	}

	// Redirects the User to the Login screen if not authenticated
	$: if (browser) {
		setTimeout(() => {
			if (!$isAuthenticated) goto(`/`, true);
		}, waitTime);
	}

	// Locks the background scroll when modal is open
	$: if (browser && (addTopicVisible || deleteTopicVisible || errorMessageVisible)) {
		document.body.classList.add('modal-open');
	} else if (browser && !(addTopicVisible || deleteTopicVisible || errorMessageVisible)) {
		document.body.classList.remove('modal-open');
	}

	// checkboxes selection
	$: if ($topicSets?.length === topicSetRowsSelected?.length) {
		topicSetRowsSelectedTrue = false;
		topicSetAllRowsSelectedTrue = true;
	} else if (topicSetRowsSelected?.length > 0) {
		topicSetRowsSelectedTrue = true;
	} else {
		topicSetAllRowsSelectedTrue = false;
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
	const searchStringLength = 3;

	// Tables
	let topicSetRowsSelected = [];
	let topicSetRowsSelectedTrue = false;
	let topicSetAllRowsSelectedTrue = false;
	let topicSetsPerPage = 10;

	// Search
	let searchString;
	let timer;

	// Authentication
	let isTopicAdmin = false;

	// Error Handling
	let errorMsg, errorObject;

	//Pagination
	let topicsCurrentPage = 0;

	// Modals
	let errorMessageVisible = false;
	let topicsListVisible = true;
	let topicDetailVisible = false;
	let addTopicVisible = false;
	let deleteTopicVisible = false;

	// Selection
	let selectedTopicSetId;

	// Topic Set Creation
	let newTopicSetName = '';
	let searchGroups = '';
	let selectedGroup = '';


	// TopicSet Filter Feature
	$: if (searchString?.trim().length >= searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			searchTopics(searchString.trim());
		}, waitTime);
	}

	$: if (searchString?.trim().length < searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadAllTopicSets();
		}, waitTime);
	}

	// Return to List view
	$: if ($detailView === 'backToList') {
		headerTitle.set(messages['topic-sets']['title']);
		reloadAllTopicSets();
		returnToTopicsList();
	}

	const reloadAllTopicSets = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/topic-sets?page=${page}&size=${topicSetsPerPage}&filter=${searchString}`
				);
			} else if ($groupContext) {
				res = await httpAdapter.get(
					`/topic-sets?page=${page}&size=${topicSetsPerPage}&group=${$groupContext.id}`
				);
			} else {
				res = await httpAdapter.get(`/topic-sets?page=${page}&size=${topicSetsPerPage}`);
			}
			if (res.data) {
				topicsTotalPages.set(res.data.totalPages);
				topicsTotalSize.set(res.data.totalSize);
			}
			topicSets.set(res.data.content);
			topicsCurrentPage = page;
			updateRetrievalTimestamp(retrievedTimestamps, 'topic-sets');
		} catch (err) {
			userValidityCheck.set(true);

			errorMessage(errorMessages['topic-sets']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		detailView.set('first run');

		headerTitle.set(messages['topic-sets']['title']);

		if (document.querySelector('.content') == null) promise = await reloadAllTopicSets();

		if ($permissionsByGroup) {
			isTopicAdmin = $permissionsByGroup?.some(
				(groupPermission) => groupPermission.isTopicAdmin === true
			);
		}
	});

	const searchTopics = async (searchStr) => {
		let res;

		if ($groupContext?.id)
			res = await httpAdapter.get(
				`/topic-sets?page=0&size=${topicSetsPerPage}&filter=${searchStr}&group=${$groupContext.id}`
			);
		else res = await httpAdapter.get(`/topic-sets?page=0&size=${topicSetsPerPage}&filter=${searchStr}`);

		if (res.data.content) {
			topicSets.set(res.data.content);
		} else {
			topicSets.set([]);
		}
		topicsTotalPages.set(res.data.totalPages);
		if (res.data.totalSize !== undefined) topicsTotalSize.set(res.data.totalSize);
		topicsCurrentPage = 0;
		updateRetrievalTimestamp(retrievedTimestamps, 'topic-sets');
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

	const loadTopic = () => {
		addTopicVisible = false;
		topicsListVisible = false;
		topicDetailVisible = true;
	};

	const deleteSelectedTopics = async () => {
		try {
			for (const topic of topicSetRowsSelected) {
				await httpAdapter.delete(`/topic-sets/${topic.id}`);
			}
			updateRetrievalTimestamp(retrievedTimestamps, 'topic-sets');
		} catch (err) {
			errorMessage(errorMessages['topic-sets']['deleting.error.title'], err.message);
		}
	};

	const returnToTopicsList = () => {
		topicDetailVisible = false;
		topicsListVisible = true;
	};

	const deselectAllTopicsCheckboxes = () => {
		topicSetAllRowsSelectedTrue = false;
		topicSetRowsSelectedTrue = false;
		topicSetRowsSelected = [];
		let checkboxes = document.querySelectorAll('.topics-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.topics-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const addTopicSet = async () => {
		if (!selectedGroup) {
			const groupId = await httpAdapter.get(`/groups?page=0&size=1&filter=${searchGroups}`);
			if (
				groupId.data.content &&
				groupId.data.content[0]?.name.toUpperCase() === searchGroups.toUpperCase()
			) {
				selectedGroup = groupId.data.content[0]?.id;
			}
		}

		const res = await httpAdapter
			.post(`/topic-sets`, {
				name: newTopicSetName,
				groupId: selectedGroup,
			})
			.catch((err) => {
				addTopicVisible = false;
				errorMessage(errorMessages['topic-sets']['adding.error.title'], err.message);
			});

		addTopicVisible = false;
		if (res) {
			selectedTopicSetId = res.data?.id;
			loadTopic();
		}

		if (res === undefined) {
			errorMessage(errorMessages['topic-sets']['adding.error.title'], errorMessages['topic-sets']['exists']);
		}
	};
</script>

<svelte:head>
	<title>{messages['topic-sets']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Topics" />
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

			{#if addTopicVisible}
				<Modal
					title={messages['topic-sets']['add']}
					topicSetName={true}
					group={true}
					actionAddTopicSet={true}
					topicCurrentGroupPublic={$groupContext?.public ?? false}
					on:cancel={() => (addTopicVisible = false)}
					on:addTopicSet={(e) => {
						newTopicSetName = e.detail.newTopicSetName;
						selectedGroup = e.detail.selectedGroup;
						addTopicSet();
					}}
				/>
			{/if}

			{#if topicDetailVisible && !topicsListVisible}
				<TopicSetDetails
					{selectedTopicSetId}
					{isTopicAdmin}
				/>
			{/if}

			{#if deleteTopicVisible}
				<Modal
					actionDeleteTopics={true}
					title="{messages['topic-sets']['delete.title']} {topicSetRowsSelected.length > 1
						? messages['topic-sets']['delete.multiple']
						: messages['topic-sets']['delete.single']}"
					on:cancel={() => {
						if (topicSetRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
							topicSetRowsSelected = [];
						deleteTopicVisible = false;
					}}
					on:deleteTopics={async () => {
						await deleteSelectedTopics();
						reloadAllTopicSets();
						deselectAllTopicsCheckboxes();
						deleteTopicVisible = false;
					}}
				/>
			{/if}

			{#if !topicDetailVisible}
				{#if $topicsTotalSize !== undefined && $topicsTotalSize != NaN}
					<div class="content">
						<h1 data-cy="topics">{messages['topic-sets']['title']}</h1>

						<form class="searchbox">
							<input
								data-cy="search-topics-table"
								class="searchbox"
								type="search"
								placeholder={messages['topic-sets']['search.placeholder']}
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
								>{messages['topic-sets']['search.clear.button']}</button
							>
						{/if}

						<img
							src={deleteSVG}
							alt="options"
							class="dot"
							class:button-disabled={(!$isAdmin && !isTopicAdmin) ||
								topicSetRowsSelected.length === 0}
							style="margin-left: 0.5rem; margin-right: 1rem"
							on:click={() => {
								if (topicSetRowsSelected.length > 0) deleteTopicVisible = true;
							}}
							on:keydown={(event) => {
								if (event.which === returnKey) {
									if (topicSetRowsSelected.length > 0) deleteTopicVisible = true;
								}
							}}
							on:mouseenter={() => {
								deleteMouseEnter = true;
								if ($isAdmin || isTopicAdmin) {
									if (topicSetRowsSelected.length === 0) {
										deleteToolip = messages['topic-sets']['delete.tooltip'];
										const tooltip = document.querySelector('#delete-topics');
										setTimeout(() => {
											if (deleteMouseEnter) {
												tooltip.classList.remove('tooltip-hidden');
												tooltip.classList.add('tooltip');
											}
										}, 1000);
									}
								} else {
									deleteToolip = messages['topic-sets']['delete.tooltip.topic.admin.required'];
									const tooltip = document.querySelector('#delete-topics');
									setTimeout(() => {
										if (deleteMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
											tooltip.setAttribute('style', 'margin-left:10.2rem; margin-top: -1.8rem');
										}
									}, 1000);
								}
							}}
							on:mouseleave={() => {
								deleteMouseEnter = false;
								if (topicSetRowsSelected.length === 0) {
									const tooltip = document.querySelector('#delete-topics');
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
							id="delete-topics"
							class="tooltip-hidden"
							style="margin-left: 12.2rem; margin-top: -1.8rem"
							>{deleteToolip}
						</span>

						<img
							data-cy="add-topic"
							src={addSVG}
							alt="options"
							class="dot"
							class:button-disabled={(!$isAdmin &&
								!$permissionsByGroup?.find(
									(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
								)) ||
								!$groupContext}
							on:click={() => {
								if (
									$groupContext &&
									($isAdmin ||
										$permissionsByGroup?.find(
											(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
										))
								) {
									addTopicVisible = true;
								} else if (
									!$groupContext &&
									($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
								)
									showSelectGroupContext.set(true);
							}}
							on:keydown={(event) => {
								if (event.which === returnKey) {
									if (
										$groupContext &&
										($isAdmin ||
											$permissionsByGroup?.find(
												(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
											))
									) {
										addTopicVisible = true;
									} else if (
										!$groupContext &&
										($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
									)
										showSelectGroupContext.set(true);
								}
							}}
							on:mouseenter={() => {
								addMouseEnter = true;
								if (
									(!$isAdmin &&
										$groupContext &&
										!$permissionsByGroup?.find(
											(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
										)) ||
									(!$isAdmin &&
										!$groupContext &&
										!$permissionsByGroup?.some((gm) => gm.isTopicAdmin === true))
								) {
									addTooltip = messages['topic-sets']['add.tooltip.topic.admin.required'];
									const tooltip = document.querySelector('#add-topics');
									setTimeout(() => {
										if (addMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, waitTime);
								} else if (
									!$groupContext &&
									($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
								) {
									addTooltip = messages['topic-sets']['add.tooltip.select.group'];
									const tooltip = document.querySelector('#add-topics');
									setTimeout(() => {
										if (addMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
											tooltip.setAttribute('style', 'margin-left:8.6rem; margin-top: -1.8rem');
										}
									}, 1000);
								}
							}}
							on:mouseleave={() => {
								addMouseEnter = false;
								const tooltip = document.querySelector('#add-topics');
								setTimeout(() => {
									if (!addMouseEnter) {
										tooltip.classList.add('tooltip-hidden');
										tooltip.classList.remove('tooltip');
									}
								}, waitTime);
							}}
						/>
						<span
							id="add-topics"
							class="tooltip-hidden"
							style="margin-left: 8rem; margin-top: -1.8rem"
							>{addTooltip}
						</span>

						{#if $topicSets?.length > 0 && topicsListVisible && !topicDetailVisible}
							<table data-cy="topics-table" class="main" style="margin-top: 0.5rem">
								<thead>
									<tr style="border-top: 1px solid black; border-bottom: 2px solid">
										{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
											<td style="line-height: 1rem;">
												<input
													tabindex="-1"
													type="checkbox"
													class="topics-checkbox"
													style="margin-right: 0.5rem"
													bind:indeterminate={topicSetRowsSelectedTrue}
													on:click={(e) => {
														if (e.target.checked) {
															topicSetRowsSelected = $topicSets;
															topicSetRowsSelectedTrue = false;
															topicSetAllRowsSelectedTrue = true;
														} else {
															topicSetAllRowsSelectedTrue = false;
															topicSetRowsSelectedTrue = false;
															topicSetRowsSelected = [];
														}
													}}
													checked={topicSetAllRowsSelectedTrue}
												/>
											</td>
										{/if}
										<td style="min-width: 7rem">{messages['topic-sets']['table.column.one']}</td>
										<td>{messages['topic-sets']['table.column.two']}</td>
									</tr>
								</thead>
								<tbody>
									{#each $topicSets as topicSet}
										<tr>
											{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
												<td style="line-height: 1rem; width: 2rem; ">
													<input
														tabindex="-1"
														type="checkbox"
														class="topics-checkbox"
														checked={topicSetAllRowsSelectedTrue}
														on:change={(e) => {
															if (e.target.checked === true) {
																topicSetRowsSelected.push(topicSet);
																// reactive statement
																topicSetRowsSelected = topicSetRowsSelected;
																topicSetRowsSelectedTrue = true;
															} else {
																topicSetRowsSelected = topicSetRowsSelected.filter(
																	(selection) => selection !== topicSet
																);
																if (topicSetRowsSelected.length === 0) {
																	topicSetRowsSelectedTrue = false;
																}
															}
														}}
													/>
												</td>
											{/if}

											<td
												style="cursor: pointer; width: max-content"
												on:click={() => {
													selectedTopicSetId = topicSet.id;
													loadTopic();
													history.pushState({ path: '/topics' }, 'My Topic Sets', '/topic-sets');
												}}
												on:keydown={(event) => {
													if (event.which === returnKey) {
														selectedTopicSetId = topicSet.id;
														loadTopic();
													}
												}}
												>{topicSet.name}
											</td>

											<td style="padding-left: 0.5rem">{topicSet.groupName}</td>

											<td style="cursor: pointer; width:1rem">
												<img
													data-cy="detail-application-icon"
													src={detailSVG}
													height="18rem"
													width="18rem"
													alt="edit user"
													style="vertical-align: -0.2rem"
													on:click={() => {
														selectedTopicSetId = topicSet.id;
														loadTopic();
													}}
													on:keydown={(event) => {
														if (event.which === returnKey) {
															selectedTopicSetId = topicSet.id;
															loadTopic();
														}
													}}
												/>
											</td>

											<td
												style="cursor: pointer; text-align: right; padding-right: 0.25rem; width: 1rem"
											>
												<!-- svelte-ignore a11y-click-events-have-key-events -->
												<img
													data-cy="delete-topic-icon"
													src={deleteSVG}
													width="27px"
													height="27px"
													style="vertical-align: -0.45rem"
													alt="delete topic"
													disabled={!$isAdmin || !isTopicAdmin}
													on:click={() => {
														if (!topicSetRowsSelected.some((tpc) => tpc === topicSet))
															topicSetRowsSelected.push(topicSet);
														deleteTopicVisible = true;
													}}
												/>
											</td>
										</tr>
									{/each}
								</tbody>
							</table>
						{:else if !topicDetailVisible}
							<p>
								{messages['topic-sets']['empty.topics']}
								<br />
								{#if $groupContext && ($permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin)}
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<span
										class="link"
										on:click={() => {
											if (
												$groupContext &&
												($permissionsByGroup?.find(
													(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
												) ||
													$isAdmin)
											)
												addTopicVisible = true;
											else if (
												!$groupContext &&
												($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
											)
												showSelectGroupContext.set(true);
										}}
									>
										{messages['topic-sets']['empty.topics.action.two']}
									</span>
									{messages['topic-sets']['empty.topics.action.result']}
								{:else if !$groupContext && ($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)}
									{messages['topic-sets']['empty.topics.action']}
								{/if}
							</p>
						{/if}
					</div>

					<div class="pagination">
						<span>{messages['pagination']['rows.per.page']}</span>
						<select
							tabindex="-1"
							on:change={(e) => {
								topicSetsPerPage = e.target.value;
								reloadAllTopicSets();
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
							{#if $topicsTotalSize > 0}
								{1 + topicsCurrentPage * topicSetsPerPage}
							{:else}
								0
							{/if}
							- {Math.min(topicSetsPerPage * (topicsCurrentPage + 1), $topicsTotalSize)} of
							{$topicsTotalSize}
						</span>

						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagefirstSVG}
							alt="first page"
							class="pagination-image"
							class:disabled-img={topicsCurrentPage === 0}
							on:click={() => {
								deselectAllTopicsCheckboxes();
								if (topicsCurrentPage > 0) {
									topicsCurrentPage = 0;
									reloadAllTopicSets();
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagebackwardsSVG}
							alt="previous page"
							class="pagination-image"
							class:disabled-img={topicsCurrentPage === 0}
							on:click={() => {
								deselectAllTopicsCheckboxes();
								if (topicsCurrentPage > 0) {
									topicsCurrentPage--;
									reloadAllTopicSets(topicsCurrentPage);
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pageforwardSVG}
							alt="next page"
							class="pagination-image"
							class:disabled-img={topicsCurrentPage + 1 === $topicsTotalPages ||
								$topicSets?.length === undefined}
							on:click={() => {
								deselectAllTopicsCheckboxes();
								if (topicsCurrentPage + 1 < $topicsTotalPages) {
									topicsCurrentPage++;
									reloadAllTopicSets(topicsCurrentPage);
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagelastSVG}
							alt="last page"
							class="pagination-image"
							class:disabled-img={topicsCurrentPage + 1 === $topicsTotalPages ||
								$topicSets?.length === undefined}
							on:click={() => {
								deselectAllTopicsCheckboxes();
								if (topicsCurrentPage < $topicsTotalPages) {
									topicsCurrentPage = $topicsTotalPages - 1;
									reloadAllTopicSets(topicsCurrentPage);
								}
							}}
						/>
					</div>
				<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['topic-sets']} />
					<p style="margin-top: 8rem">{messages['footer']['message']}</p>
				{/if}
			{/if}
		{/await}
	{/if}
{/key}

<style>
	table.main {
		min-width: 43.5rem;
		line-height: 2.2rem;
	}

	.dot {
		float: right;
	}

	.content {
		width: 100%;
		min-width: fit-content;
		margin-right: 1rem;
	}

	p {
		font-size: large;
	}
</style>

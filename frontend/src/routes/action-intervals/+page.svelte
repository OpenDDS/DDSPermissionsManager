<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import refreshPage from '../../stores/refreshPage';
	import Modal from '../../lib/Modal.svelte';
	import RetrievedTimestamp from '../../lib/RetrievedTimestamp.svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { browser } from '$app/environment';
	import userValidityCheck from '../../stores/userValidityCheck';
	import headerTitle from '../../stores/headerTitle';
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
	import topicsTotalSize from '../../stores/topicsTotalSize';
	import topicsTotalPages from '../../stores/topicsTotalPages';
	import actionIntervals from '../../stores/actionIntervals';
	import retrievedTimestamps from '../../stores/retrievedTimestamps';
	import { updateRetrievalTimestamp } from '../../utils.js';
	import ActionIntervalModal from './ActionIntervalModal.svelte';

	// Group Context
	$: if ($groupContext?.id) reloadAllActionIntervals();

	$: if ($groupContext === 'clear') {
		groupContext.set();
		singleGroupCheck.set();
		reloadAllActionIntervals();
	}

	// Redirects the User to the Login screen if not authenticated
	$: if (browser) {
		setTimeout(() => {
			if (!$isAuthenticated) goto(resolve('/'));
		}, waitTime);
	}

	// Locks the background scroll when modal is open
	$: if (
		browser &&
		(addActionIntervalsVisible || deleteActionIntervalsVisible || errorMessageVisible)
	) {
		document.body.classList.add('modal-open');
	} else if (
		browser &&
		!(addActionIntervalsVisible || deleteActionIntervalsVisible || errorMessageVisible)
	) {
		document.body.classList.remove('modal-open');
	}

	// checkboxes selection
	$: if ($actionIntervals?.length === actionIntervalRowsSelected?.length) {
		actionIntervalRowsSelectedTrue = false;
		actionIntervalAllRowsSelectedTrue = true;
	} else if (actionIntervalRowsSelected?.length > 0) {
		actionIntervalRowsSelectedTrue = true;
	} else {
		actionIntervalAllRowsSelectedTrue = false;
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
	let actionIntervalRowsSelected = [];
	let actionIntervalRowsSelectedTrue = false;
	let actionIntervalAllRowsSelectedTrue = false;
	let actionIntervalsPerPage = 10;

	// Search
	let searchString;
	let timer;

	// Authentication
	let isTopicAdmin = false;

	// Error Handling
	let errorMsg, errorObject;

	//Pagination
	let actionIntervalsCurrentPage = 0;

	// Modals
	let errorMessageVisible = false;
	let actionIntervalsListVisible = true;
	let editActionIntervalsVisible = false;
	let addActionIntervalsVisible = false;
	let deleteActionIntervalsVisible = false;
	let selectedActionInterval = {};

	// Search
	$: if (searchString?.trim().length < searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadAllActionIntervals();
		}, waitTime);
	}

	const reloadAllActionIntervals = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/action_intervals?page=${page}&size=${actionIntervalsPerPage}&filter=${searchString}`
				);
			} else if ($groupContext) {
				res = await httpAdapter.get(
					`/action_intervals?page=${page}&size=${actionIntervalsPerPage}&group=${$groupContext.id}`
				);
			} else {
				res = await httpAdapter.get(
					`/action_intervals?page=${page}&size=${actionIntervalsPerPage}`
				);
			}
			if (res.data) {
				topicsTotalPages.set(res.data.totalPages);
				topicsTotalSize.set(res.data.totalSize);
			}
			actionIntervals.set(res.data.content);
			actionIntervalsCurrentPage = page;
			updateRetrievalTimestamp(retrievedTimestamps, 'actionIntervals');
		} catch (err) {
			userValidityCheck.set(true);

			errorMessage(errorMessages['action-intervals']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		headerTitle.set(messages['action-intervals']['title']);

		await reloadAllActionIntervals();

		if ($permissionsByGroup) {
			isTopicAdmin = $permissionsByGroup?.some(
				(groupPermission) => groupPermission.isTopicAdmin === true
			);
		}
	});

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

	const editInterval = (actionInterval) => {
		selectedActionInterval = actionInterval;
		addActionIntervalsVisible = false;
		editActionIntervalsVisible = true;
	};

	const deleteSelectedActionIntervals = async () => {
		try {
			for (const actionInterval of actionIntervalRowsSelected) {
				await httpAdapter.delete(`/action_intervals/${actionInterval.id}`);
			}
			updateRetrievalTimestamp(retrievedTimestamps, 'actionIntervals');
		} catch (err) {
			errorMessage(errorMessages['action-intervals']['deleting.error.title'], err.message);
		}
	};

	const deselectAllActionIntervalsCheckboxes = () => {
		actionIntervalAllRowsSelectedTrue = false;
		actionIntervalRowsSelectedTrue = false;
		actionIntervalRowsSelected = [];
		let checkboxes = document.querySelectorAll('.action-intervals-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.action-intervals-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const addActionInterval = async (newActionInterval) => {
		const res = await httpAdapter.post(`/action_intervals`, newActionInterval).catch((err) => {
			addActionIntervalsVisible = false;
			errorMessage(errorMessages['action-intervals']['adding.error.title'], err.message);
		});

		addActionIntervalsVisible = false;

		if (res === undefined) {
			errorMessage(
				errorMessages['action-intervals']['adding.error.title'],
				errorMessages['action-intervals']['exists']
			);
		} else {
			reloadAllActionIntervals();
		}
	};

	const updateActionInterval = async (actionInterval) => {
		const res = await httpAdapter
			.put(`/action_intervals/${actionInterval.id}`, actionInterval)
			.catch((err) => {
				addActionIntervalsVisible = false;
				errorMessage(errorMessages['action-intervals']['adding.error.title'], err.message);
			});

		addActionIntervalsVisible = false;

		if (res === undefined) {
			errorMessage(
				errorMessages['action-intervals']['adding.error.title'],
				errorMessages['action-intervals']['exists']
			);
		} else {
			reloadAllActionIntervals();
			selectedActionInterval = {};
		}
	};

	const getDateOnly = (dateString) => {
		const dateOnlyString = dateString.substring(0, 10);
		return dateOnlyString;
	};
</script>

<svelte:head>
	<title>{messages['action-intervals']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Action Intervals" />
</svelte:head>

{#key $refreshPage}
	{#if $isAuthenticated}
		{#await promise then _} <!-- eslint-disable-line no-unused-vars -->
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

			{#if addActionIntervalsVisible}
				<ActionIntervalModal
					title={messages['action-intervals']['add']}
					actionAddActionInterval={true}
					on:cancel={() => (addActionIntervalsVisible = false)}
					on:addActionInterval={(e) => {
						addActionInterval(e.detail);
					}}
				/>
			{/if}

			{#if editActionIntervalsVisible}
				<ActionIntervalModal
					title={messages['action-intervals']['add']}
					actionEditActionInterval={true}
					on:cancel={() => (editActionIntervalsVisible = false)}
					{selectedActionInterval}
					on:editActionInterval={(e) => {
						updateActionInterval(e.detail);
					}}
				/>
			{/if}

			{#if deleteActionIntervalsVisible}
				<Modal
					actionDeleteTopics={true}
					title="{messages['action-intervals']['delete.title']} {actionIntervalRowsSelected.length >
					1
						? messages['action-intervals']['delete.multiple']
						: messages['action-intervals']['delete.single']}"
					on:cancel={() => {
						if (actionIntervalRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
							actionIntervalRowsSelected = [];
						deleteActionIntervalsVisible = false;
					}}
					on:deleteTopics={async () => {
						await deleteSelectedActionIntervals();
						reloadAllActionIntervals();
						deselectAllActionIntervalsCheckboxes();
						deleteActionIntervalsVisible = false;
					}}
				/>
			{/if}

			{#if $topicsTotalSize !== undefined && !isNaN($topicsTotalSize)}
				<div class="content">
					<h1 data-cy="action-interval">{messages['action-intervals']['title']}</h1>

					<form class="searchbox">
						<input
							data-cy="search-action-intervals-table"
							class="searchbox"
							type="search"
							placeholder={messages['action-intervals']['search.placeholder']}
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
							>{messages['action-intervals']['search.clear.button']}</button
						>
					{/if}

					<button aria-label="options"  on:click={() => {
							if (actionIntervalRowsSelected.length > 0) deleteActionIntervalsVisible = true;
						}} on:keydown={(event) => {
							if (event.which === returnKey) {
								if (actionIntervalRowsSelected.length > 0) deleteActionIntervalsVisible = true;
							}
						}} on:mouseenter={() => {
							deleteMouseEnter = true;
							if ($isAdmin || isTopicAdmin) {
								if (actionIntervalRowsSelected.length === 0) {
									deleteToolip = messages['action-intervals']['delete.tooltip'];
									const tooltip = document.querySelector('#delete-action-intervals');
									setTimeout(() => {
										if (deleteMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, 1000);
								}
							} else {
								deleteToolip =
									messages['action-intervals']['delete.tooltip.action-interval.admin.required'];
								const tooltip = document.querySelector('#delete-action-intervals');
								setTimeout(() => {
									if (deleteMouseEnter) {
										tooltip.classList.remove('tooltip-hidden');
										tooltip.classList.add('tooltip');
										tooltip.setAttribute('style', 'margin-left:10.2rem; margin-top: -1.8rem');
									}
								}, 1000);
							}
						}} on:mouseleave={() => {
							deleteMouseEnter = false;
							if (actionIntervalRowsSelected.length === 0) {
								const tooltip = document.querySelector('#delete-action-intervals');
								setTimeout(() => {
									if (!deleteMouseEnter) {
										tooltip.classList.add('tooltip-hidden');
										tooltip.classList.remove('tooltip');
									}
								}, 1000);
							}
						}}><img
						src={deleteSVG}
						alt=""
						class="dot icon-button"
						class:button-disabled={(!$isAdmin && !isTopicAdmin) ||
							actionIntervalRowsSelected.length === 0}
						style="margin-left: 0.5rem; margin-right: 1rem"
						
						
						
						
					/></button>
					<span
						id="delete-action-intervals"
						class="tooltip-hidden"
						style="margin-left: 12.2rem; margin-top: -1.8rem"
						>{deleteToolip}
					</span>

					<button aria-label="options"  on:click={() => {
							if (
								$groupContext &&
								($isAdmin ||
									$permissionsByGroup?.find(
										(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
									))
							) {
								addActionIntervalsVisible = true;
							} else if (
								!$groupContext &&
								($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
							)
								showSelectGroupContext.set(true);
						}} on:keydown={(event) => {
							if (event.which === returnKey) {
								if (
									$groupContext &&
									($isAdmin ||
										$permissionsByGroup?.find(
											(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
										))
								) {
									addActionIntervalsVisible = true;
								} else if (
									!$groupContext &&
									($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
								)
									showSelectGroupContext.set(true);
							}
						}} on:mouseenter={() => {
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
								addTooltip =
									messages['action-intervals']['add.tooltip.action-interval.admin.required'];
								const tooltip = document.querySelector('#add-action-intervals');
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
								addTooltip = messages['action-intervals']['add.tooltip.select.group'];
								const tooltip = document.querySelector('#add-action-intervals');
								setTimeout(() => {
									if (addMouseEnter) {
										tooltip.classList.remove('tooltip-hidden');
										tooltip.classList.add('tooltip');
										tooltip.setAttribute('style', 'margin-left:8.6rem; margin-top: -1.8rem');
									}
								}, 1000);
							}
						}} on:mouseleave={() => {
							addMouseEnter = false;
							const tooltip = document.querySelector('#add-action-intervals');
							setTimeout(() => {
								if (!addMouseEnter) {
									tooltip.classList.add('tooltip-hidden');
									tooltip.classList.remove('tooltip');
								}
							}, waitTime);
						}}><img
						data-cy="add-action-interval"
						src={addSVG}
						alt=""
						class="dot icon-button"
						class:button-disabled={(!$isAdmin &&
							!$permissionsByGroup?.find(
								(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
							)) ||
							!$groupContext}
						
						
						
						
					/></button>
					<span
						id="add-action-intervals"
						class="tooltip-hidden"
						style="margin-left: 8rem; margin-top: -1.8rem"
						>{addTooltip}
					</span>

					{#if $actionIntervals?.length > 0 && actionIntervalsListVisible}
						<table data-cy="action-intervals-table" class="main" style="margin-top: 0.5rem">
							<thead>
								<tr style="border-top: 1px solid black; border-bottom: 2px solid">
									{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
										<td style="line-height: 1rem;">
											<input
												tabindex="-1"
												type="checkbox"
												class="action-intervals-checkbox"
												style="margin-right: 0.5rem"
												bind:indeterminate={actionIntervalRowsSelectedTrue}
												on:click={(e) => {
													if (e.target.checked) {
														actionIntervalRowsSelected = $actionIntervals;
														actionIntervalRowsSelectedTrue = false;
														actionIntervalAllRowsSelectedTrue = true;
													} else {
														actionIntervalAllRowsSelectedTrue = false;
														actionIntervalRowsSelectedTrue = false;
														actionIntervalRowsSelected = [];
													}
												}}
												checked={actionIntervalAllRowsSelectedTrue}
											/>
										</td>
									{/if}
									<td class="header-column" style="min-width: 7rem">{messages['action-intervals']['table.column.one']}</td
									>
									<td class="header-column">{messages['action-intervals']['table.column.two']}</td>
									<td class="header-column">{messages['action-intervals']['table.column.three']}</td>
									<td class="header-column">{messages['action-intervals']['table.column.four']}</td>
								</tr>
							</thead>
							<tbody>
								{#each $actionIntervals as actionInterval (actionInterval.id)}
									<tr>
										{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
											<td style="line-height: 1rem; width: 2rem; ">
												<input
													tabindex="-1"
													type="checkbox"
													class="action-intervals-checkbox"
													checked={actionIntervalAllRowsSelectedTrue}
													on:change={(e) => {
														if (e.target.checked === true) {
															actionIntervalRowsSelected.push(actionInterval);
															// reactive statement
															actionIntervalRowsSelected = actionIntervalRowsSelected;
															actionIntervalRowsSelectedTrue = true;
														} else {
															actionIntervalRowsSelected = actionIntervalRowsSelected.filter(
																(selection) => selection !== actionInterval
															);
															if (actionIntervalRowsSelected.length === 0) {
																actionIntervalRowsSelectedTrue = false;
															}
														}
													}}
												/>
											</td>
										{/if}

										
										<td
											style="cursor: pointer; width: max-content"
											data-cy="action-interval-name"
											
											><button class="text-button"  on:click={() => {
												if ($isAdmin || isTopicAdmin) {
													editInterval(actionInterval);
												}
											}}>{actionInterval.name}
										</button></td>

										<td style="padding-left: 0.5rem">{actionInterval.groupName}</td>

										<td data-cy="grant-action-interval" style="padding-left: 0.5rem"
											>{getDateOnly(actionInterval.startDate)}</td
										>

										<td data-cy="grant-action-interval" style="padding-left: 0.5rem"
											>{getDateOnly(actionInterval.endDate)}</td
										>

										{#if $isAdmin || isTopicAdmin}
											<td style="cursor: pointer; width:1rem">
												
												<button aria-label="edit user"  on:click={() => {
														if ($isAdmin || isTopicAdmin) {
															editInterval(actionInterval);
														}
													}}><img class="icon-button"
													data-cy="detail-application-icon"
													src={detailSVG}
													height="18rem"
													width="18rem"
													alt=""
													style="vertical-align: -0.2rem"
													
												/></button>
											</td>

											<td
												style="cursor: pointer; text-align: right; padding-right: 0.25rem; width: 1rem"
											>
												
												<button aria-label="delete action-interval"  on:click={() => {
														if (!actionIntervalRowsSelected.some((tpc) => tpc === actionInterval))
															actionIntervalRowsSelected.push(actionInterval);
														deleteActionIntervalsVisible = true;
													}}><img class="icon-button"
													data-cy="delete-action-interval-icon"
													src={deleteSVG}
													width="27px"
													height="27px"
													style="vertical-align: -0.45rem"
													alt=""
													disabled={!$isAdmin || !isTopicAdmin}
													
												/></button>
											</td>
										{/if}
									</tr>
								{/each}
							</tbody>
						</table>
					{:else if !editActionIntervalsVisible}
						<p>
							{messages['action-intervals']['empty.action-intervals']}
							<br />
							{#if $groupContext && ($permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin)}
								
								<button aria-label="interactive element"  on:click={() => {
										if (
											$groupContext &&
											($permissionsByGroup?.find(
												(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
											) ||
												$isAdmin)
										)
											addActionIntervalsVisible = true;
										else if (
											!$groupContext &&
											($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
										)
											showSelectGroupContext.set(true);
									}}><span
									class="link icon-button"
									
								>
									{messages['action-intervals']['empty.action-intervals.action.two']}
								</span></button>
								{messages['action-intervals']['empty.action-intervals.action.result']}
							{:else if !$groupContext && ($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)}
								{messages['action-intervals']['empty.action-intervals.action']}
							{/if}
						</p>
					{/if}
				</div>

				<div class="pagination">
					<span>{messages['pagination']['rows.per.page']}</span>
					<select
						tabindex="-1"
						on:change={(e) => {
							actionIntervalsPerPage = e.target.value;
							reloadAllActionIntervals();
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
							{1 + actionIntervalsCurrentPage * actionIntervalsPerPage}
						{:else}
							0
						{/if}
						- {Math.min(
							actionIntervalsPerPage * (actionIntervalsCurrentPage + 1),
							$topicsTotalSize
						)} of
						{$topicsTotalSize}
					</span>

					
					<button aria-label="first page"  on:click={() => {
							deselectAllActionIntervalsCheckboxes();
							if (actionIntervalsCurrentPage > 0) {
								actionIntervalsCurrentPage = 0;
								reloadAllActionIntervals();
							}
						}}><img
						src={pagefirstSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={actionIntervalsCurrentPage === 0}
						
					/></button>
					
					<button aria-label="previous page"  on:click={() => {
							deselectAllActionIntervalsCheckboxes();
							if (actionIntervalsCurrentPage > 0) {
								actionIntervalsCurrentPage--;
								reloadAllActionIntervals(actionIntervalsCurrentPage);
							}
						}}><img
						src={pagebackwardsSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={actionIntervalsCurrentPage === 0}
						
					/></button>
					
					<button aria-label="next page"  on:click={() => {
							deselectAllActionIntervalsCheckboxes();
							if (actionIntervalsCurrentPage + 1 < $topicsTotalPages) {
								actionIntervalsCurrentPage++;
								reloadAllActionIntervals(actionIntervalsCurrentPage);
							}
						}}><img
						src={pageforwardSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={actionIntervalsCurrentPage + 1 === $topicsTotalPages ||
							$actionIntervals?.length === undefined}
						
					/></button>
					
					<button aria-label="last page"  on:click={() => {
							deselectAllActionIntervalsCheckboxes();
							if (actionIntervalsCurrentPage < $topicsTotalPages) {
								actionIntervalsCurrentPage = $topicsTotalPages - 1;
								reloadAllActionIntervals(actionIntervalsCurrentPage);
							}
						}}><img
						src={pagelastSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={actionIntervalsCurrentPage + 1 === $topicsTotalPages ||
							$actionIntervals?.length === undefined}
						
					/></button>
				</div>
				<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['action-intervals']} />
				<p style="margin-top: 8rem">{messages['footer']['message']}</p>
			{/if}
		{/await}
	{/if}
{/key}

<style>
.icon-button {
	background: none;
	border: none;
	padding: 0;
	margin: 0;
	cursor: pointer;
}

.text-button {
	background: none;
	border: none;
	padding: 0;
	margin: 0;
	cursor: pointer;
	font: inherit;
	color: inherit;
	text-align: left;
	display: inline;
	text-decoration: underline;
}

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
	.header-column {
		font-weight: 600;
	}

</style>

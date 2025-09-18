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
	import grantDurations from '../../stores/grantDurations';
	import retrievedTimestamps from '../../stores/retrievedTimestamps';
	import { updateRetrievalTimestamp } from '../../utils.js';
	import DurationModal from './DurationModal.svelte';
	import { convertFromMilliseconds } from '../../utils';

	// Group Context
	$: if ($groupContext?.id) reloadAllGrantDurations();

	$: if ($groupContext === 'clear') {
		groupContext.set();
		singleGroupCheck.set();
		reloadAllGrantDurations();
	}

	// Redirects the User to the Login screen if not authenticated
	$: if (browser) {
		setTimeout(() => {
			if (!$isAuthenticated) goto(resolve('/'));
		}, waitTime);
	}

	// Locks the background scroll when modal is open
	$: if (browser && (addDurationVisible || deleteDurationVisible || errorMessageVisible)) {
		document.body.classList.add('modal-open');
	} else if (browser && !(addDurationVisible || deleteDurationVisible || errorMessageVisible)) {
		document.body.classList.remove('modal-open');
	}

	// checkboxes selection
	$: if ($grantDurations?.length === grantDurationRowsSelected?.length) {
		grantDurationRowsSelectedTrue = false;
		grantDurationAllRowsSelectedTrue = true;
	} else if (grantDurationRowsSelected?.length > 0) {
		grantDurationRowsSelectedTrue = true;
	} else {
		grantDurationAllRowsSelectedTrue = false;
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
	let grantDurationRowsSelected = [];
	let grantDurationRowsSelectedTrue = false;
	let grantDurationAllRowsSelectedTrue = false;
	let grantDurationsPerPage = 10;

	// Search
	let searchString;
	let timer;

	// Authentication
	let isTopicAdmin = false;

	// Error Handling
	let errorMsg, errorObject;

	//Pagination
	let durationsCurrentPage = 0;

	// Modals
	let errorMessageVisible = false;
	let durationsListVisible = true;
	let editDurationVisible = false;
	let addDurationVisible = false;
	let deleteDurationVisible = false;
	let selectedGrantDuration = {};

	// Search
	$: if (searchString?.trim().length < searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadAllGrantDurations();
		}, waitTime);
	}

	const reloadAllGrantDurations = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/grant_durations?page=${page}&size=${grantDurationsPerPage}&filter=${searchString}`
				);
			} else if ($groupContext) {
				res = await httpAdapter.get(
					`/grant_durations?page=${page}&size=${grantDurationsPerPage}&group=${$groupContext.id}`
				);
			} else {
				res = await httpAdapter.get(`/grant_durations?page=${page}&size=${grantDurationsPerPage}`);
			}
			if (res.data) {
				topicsTotalPages.set(res.data.totalPages);
				topicsTotalSize.set(res.data.totalSize);
			}
			grantDurations.set(res.data.content);
			durationsCurrentPage = page;
			updateRetrievalTimestamp(retrievedTimestamps, 'durations');
		} catch (err) {
			userValidityCheck.set(true);

			errorMessage(errorMessages['durations']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		headerTitle.set(messages['durations']['title']);

		await reloadAllGrantDurations();

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

	const editDuration = (grantDuration) => {
		selectedGrantDuration = grantDuration;
		addDurationVisible = false;
		editDurationVisible = true;
	};

	const deleteSelectedDurations = async () => {
		try {
			for (const duration of grantDurationRowsSelected) {
				await httpAdapter.delete(`/grant_durations/${duration.id}`);
			}
			updateRetrievalTimestamp(retrievedTimestamps, 'durations');
		} catch (err) {
			errorMessage(errorMessages['durations']['deleting.error.title'], err.message);
		}
	};

	const deselectAllDurationsCheckboxes = () => {
		grantDurationAllRowsSelectedTrue = false;
		grantDurationRowsSelectedTrue = false;
		grantDurationRowsSelected = [];
		let checkboxes = document.querySelectorAll('.durations-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.durations-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const addGrantDuration = async (newGrantDuration) => {
		const res = await httpAdapter.post(`/grant_durations`, newGrantDuration).catch((err) => {
			addDurationVisible = false;
			errorMessage(errorMessages['durations']['adding.error.title'], err.message);
		});

		addDurationVisible = false;

		if (res === undefined) {
			errorMessage(
				errorMessages['durations']['adding.error.title'],
				errorMessages['durations']['exists']
			);
		} else {
			reloadAllGrantDurations();
		}
	};

	const editGrantDuration = async (grantDuration) => {
		const res = await httpAdapter
			.put(`/grant_durations/${selectedGrantDuration.id}`, grantDuration)
			.catch((err) => {
				addDurationVisible = false;
				errorMessage(errorMessages['durations']['adding.error.title'], err.message);
			});

		addDurationVisible = false;

		if (res === undefined) {
			errorMessage(
				errorMessages['durations']['adding.error.title'],
				errorMessages['durations']['exists']
			);
		} else {
			reloadAllGrantDurations();
			selectedGrantDuration = {};
		}
	};

	const getDuration = (grantDuration) => {
		const duration = convertFromMilliseconds(
			grantDuration.durationInMilliseconds,
			grantDuration.durationMetadata
		);
		const durationType =
			duration > 1 ? grantDuration.durationMetadata + 's' : grantDuration.durationMetadata;
		return `${duration} ${durationType}`;
	};
</script>

<svelte:head>
	<title>{messages['durations']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Durations" />
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

			{#if addDurationVisible}
				<DurationModal
					title={messages['durations']['add']}
					actionAddGrandDuration={true}
					on:cancel={() => (addDurationVisible = false)}
					on:addGrantDuration={(e) => {
						addGrantDuration(e.detail);
					}}
				/>
			{/if}

			{#if editDurationVisible}
				<DurationModal
					title={messages['durations']['add']}
					actionEditGrandDuration={true}
					on:cancel={() => (editDurationVisible = false)}
					{selectedGrantDuration}
					on:editGrantDuration={(e) => {
						editGrantDuration(e.detail);
					}}
				/>
			{/if}

			{#if deleteDurationVisible}
				<Modal
					actionDeleteTopics={true}
					title="{messages['durations']['delete.title']} {grantDurationRowsSelected.length > 1
						? messages['durations']['delete.multiple']
						: messages['durations']['delete.single']}"
					on:cancel={() => {
						if (grantDurationRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
							grantDurationRowsSelected = [];
						deleteDurationVisible = false;
					}}
					on:deleteTopics={async () => {
						await deleteSelectedDurations();
						reloadAllGrantDurations();
						deselectAllDurationsCheckboxes();
						deleteDurationVisible = false;
					}}
				/>
			{/if}

			{#if $topicsTotalSize !== undefined && !isNaN($topicsTotalSize)}
				<div class="content">
					<h1 data-cy="durations">{messages['durations']['title']}</h1>

					<form class="searchbox">
						<input
							data-cy="search-durations-table"
							class="searchbox"
							type="search"
							placeholder={messages['durations']['search.placeholder']}
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
							>{messages['durations']['search.clear.button']}</button
						>
					{/if}

					<button aria-label="options"  on:click={() => {
							if (grantDurationRowsSelected.length > 0) deleteDurationVisible = true;
						}} on:keydown={(event) => {
							if (event.which === returnKey) {
								if (grantDurationRowsSelected.length > 0) deleteDurationVisible = true;
							}
						}} on:mouseenter={() => {
							deleteMouseEnter = true;
							if ($isAdmin || isTopicAdmin) {
								if (grantDurationRowsSelected.length === 0) {
									deleteToolip = messages['durations']['delete.tooltip'];
									const tooltip = document.querySelector('#delete-durations');
									setTimeout(() => {
										if (deleteMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, 1000);
								}
							} else {
								deleteToolip = messages['durations']['delete.tooltip.duration.admin.required'];
								const tooltip = document.querySelector('#delete-durations');
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
							if (grantDurationRowsSelected.length === 0) {
								const tooltip = document.querySelector('#delete-durations');
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
							grantDurationRowsSelected.length === 0}
						style="margin-left: 0.5rem; margin-right: 1rem"
						
						
						
						
					/></button>
					<span
						id="delete-durations"
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
								addDurationVisible = true;
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
									addDurationVisible = true;
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
								addTooltip = messages['durations']['add.tooltip.duration.admin.required'];
								const tooltip = document.querySelector('#add-durations');
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
								addTooltip = messages['durations']['add.tooltip.select.group'];
								const tooltip = document.querySelector('#add-durations');
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
							const tooltip = document.querySelector('#add-durations');
							setTimeout(() => {
								if (!addMouseEnter) {
									tooltip.classList.add('tooltip-hidden');
									tooltip.classList.remove('tooltip');
								}
							}, waitTime);
						}}><img
						data-cy="add-duration"
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
						id="add-durations"
						class="tooltip-hidden"
						style="margin-left: 8rem; margin-top: -1.8rem"
						>{addTooltip}
					</span>

					{#if $grantDurations?.length > 0 && durationsListVisible}
						<table data-cy="durations-table" class="main" style="margin-top: 0.5rem">
							<thead>
								<tr style="border-top: 1px solid black; border-bottom: 2px solid">
									{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
										<td style="line-height: 1rem;">
											<input
												tabindex="-1"
												type="checkbox"
												class="durations-checkbox"
												style="margin-right: 0.5rem"
												bind:indeterminate={grantDurationRowsSelectedTrue}
												on:click={(e) => {
													if (e.target.checked) {
														grantDurationRowsSelected = $grantDurations;
														grantDurationRowsSelectedTrue = false;
														grantDurationAllRowsSelectedTrue = true;
													} else {
														grantDurationAllRowsSelectedTrue = false;
														grantDurationRowsSelectedTrue = false;
														grantDurationRowsSelected = [];
													}
												}}
												checked={grantDurationAllRowsSelectedTrue}
											/>
										</td>
									{/if}
									<td class="header-column"style="min-width: 7rem">{messages['durations']['table.column.one']}</td>
									<td class="header-column">{messages['durations']['table.column.two']}</td>
									<td class="header-column">{messages['durations']['table.column.three']}</td>
								</tr>
							</thead>
							<tbody>
								{#each $grantDurations as grantDuration (grantDuration.id)}
									<tr>
										{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
											<td style="line-height: 1rem; width: 2rem; ">
												<input
													tabindex="-1"
													type="checkbox"
													class="durations-checkbox"
													checked={grantDurationAllRowsSelectedTrue}
													on:change={(e) => {
														if (e.target.checked === true) {
															grantDurationRowsSelected.push(grantDuration);
															// reactive statement
															grantDurationRowsSelected = grantDurationRowsSelected;
															grantDurationRowsSelectedTrue = true;
														} else {
															grantDurationRowsSelected = grantDurationRowsSelected.filter(
																(selection) => selection !== grantDuration
															);
															if (grantDurationRowsSelected.length === 0) {
																grantDurationRowsSelectedTrue = false;
															}
														}
													}}
												/>
											</td>
										{/if}

										
										<td
											style="cursor: pointer; width: max-content"
											data-cy="duration-name"
											
											><button class="text-button"  on:click={() => {
												if ($isAdmin || isTopicAdmin) {
													editDuration(grantDuration);
												}
											}}>{grantDuration.name}
										</button></td>

										<td style="padding-left: 0.5rem">{grantDuration.groupName}</td>

										<td data-cy="grant-duration" style="padding-left: 0.5rem">{getDuration(grantDuration)}</td>

										{#if $isAdmin || isTopicAdmin}
											<td style="cursor: pointer; width:1rem">
												
												<button aria-label="edit user"  on:click={() => {
														if ($isAdmin || isTopicAdmin) {
															editDuration(grantDuration);
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
												
												<button aria-label="delete duration"  on:click={() => {
														if (!grantDurationRowsSelected.some((tpc) => tpc === grantDuration))
															grantDurationRowsSelected.push(grantDuration);
														deleteDurationVisible = true;
													}}><img class="icon-button"
													data-cy="delete-duration-icon"
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
					{:else if !editDurationVisible}
						<p>
							{messages['durations']['empty.durations']}
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
											addDurationVisible = true;
										else if (
											!$groupContext &&
											($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
										)
											showSelectGroupContext.set(true);
									}}><span
									class="link icon-button"
									
								>
									{messages['durations']['empty.durations.action.two']}
								</span></button>
								{messages['durations']['empty.durations.action.result']}
							{:else if !$groupContext && ($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)}
								{messages['durations']['empty.durations.action']}
							{/if}
						</p>
					{/if}
				</div>

				<div class="pagination">
					<span>{messages['pagination']['rows.per.page']}</span>
					<select
						tabindex="-1"
						on:change={(e) => {
							grantDurationsPerPage = e.target.value;
							reloadAllGrantDurations();
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
							{1 + durationsCurrentPage * grantDurationsPerPage}
						{:else}
							0
						{/if}
						- {Math.min(grantDurationsPerPage * (durationsCurrentPage + 1), $topicsTotalSize)} of
						{$topicsTotalSize}
					</span>

					
					<button aria-label="first page"  on:click={() => {
							deselectAllDurationsCheckboxes();
							if (durationsCurrentPage > 0) {
								durationsCurrentPage = 0;
								reloadAllGrantDurations();
							}
						}}><img
						src={pagefirstSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={durationsCurrentPage === 0}
						
					/></button>
					
					<button aria-label="previous page"  on:click={() => {
							deselectAllDurationsCheckboxes();
							if (durationsCurrentPage > 0) {
								durationsCurrentPage--;
								reloadAllGrantDurations(durationsCurrentPage);
							}
						}}><img
						src={pagebackwardsSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={durationsCurrentPage === 0}
						
					/></button>
					
					<button aria-label="next page"  on:click={() => {
							deselectAllDurationsCheckboxes();
							if (durationsCurrentPage + 1 < $topicsTotalPages) {
								durationsCurrentPage++;
								reloadAllGrantDurations(durationsCurrentPage);
							}
						}}><img
						src={pageforwardSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={durationsCurrentPage + 1 === $topicsTotalPages ||
							$grantDurations?.length === undefined}
						
					/></button>
					
					<button aria-label="last page"  on:click={() => {
							deselectAllDurationsCheckboxes();
							if (durationsCurrentPage < $topicsTotalPages) {
								durationsCurrentPage = $topicsTotalPages - 1;
								reloadAllGrantDurations(durationsCurrentPage);
							}
						}}><img
						src={pagelastSVG}
						alt=""
						class="pagination-image icon-button"
						class:disabled-img={durationsCurrentPage + 1 === $topicsTotalPages ||
							$grantDurations?.length === undefined}
						
					/></button>
				</div>
				<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['durations']} />
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

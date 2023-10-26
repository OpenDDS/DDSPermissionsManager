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
	import grants from '../../stores/grants';
	import retrievedTimestamps from '../../stores/retrievedTimestamps';
	import { updateRetrievalTimestamp } from '../../utils.js';
	import GrantModal from './GrantModal.svelte';
	import { convertFromMilliseconds } from '../../utils';
	import GrantDetails from './GrantDetails.svelte';
	import detailView from '../../stores/detailView';

	// Group Context
	$: if ($groupContext?.id) reloadAllGrants();

	$: if ($groupContext === 'clear') {
		groupContext.set();
		singleGroupCheck.set();
		reloadAllGrants();
	}

	// Redirects the User to the Login screen if not authenticated
	$: if (browser) {
		setTimeout(() => {
			if (!$isAuthenticated) goto(`/`, true);
		}, waitTime);
	}

	// Locks the background scroll when modal is open
	$: if (browser && (addGrantVisible || deleteGrantVisible || errorMessageVisible)) {
		document.body.classList.add('modal-open');
	} else if (browser && !(addGrantVisible || deleteGrantVisible || errorMessageVisible)) {
		document.body.classList.remove('modal-open');
	}

	// checkboxes selection
	$: if ($grants?.length === grantRowsSelected?.length) {
		grantRowsSelectedTrue = false;
		grantAllRowsSelectedTrue = true;
	} else if (grantRowsSelected?.length > 0) {
		grantRowsSelectedTrue = true;
	} else {
		grantAllRowsSelectedTrue = false;
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
	let grantRowsSelected = [];
	let grantRowsSelectedTrue = false;
	let grantAllRowsSelectedTrue = false;
	let gransPerPage = 10;

	// Search
	let searchString;
	let timer;

	// Authentication
	let isTopicAdmin = false;

	// Error Handling
	let errorMsg, errorObject;

	//Pagination
	let grantsCurrentPage = 0;

	// Modals
	let errorMessageVisible = false;
	let grantsListVisible = true;
	let editGrantVisible = false;
	let addGrantVisible = false;
	let grantDetailVisible = false;
	let deleteGrantVisible = false;
	let selectedGrant = {};

	// Search
	$: if (searchString?.trim().length < searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadAllGrants();
		}, waitTime);
	}

	// Return to List view
	$: if ($detailView === 'backToList') {
		headerTitle.set(messages['grants']['title']);
		reloadAllGrants();
		returnToGrantsList();
	}

	const returnToGrantsList = () => {
		grantDetailVisible = false;
		grantsListVisible = true;
	};

	const reloadAllGrants = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/application_grants?page=${page}&size=${gransPerPage}&filter=${searchString}`
				);
			} else if ($groupContext) {
				res = await httpAdapter.get(
					`/application_grants?page=${page}&size=${gransPerPage}&group=${$groupContext.id}`
				);
			} else {
				res = await httpAdapter.get(`/application_grants?page=${page}&size=${gransPerPage}`);
			}
			if (res.data) {
				topicsTotalPages.set(res.data.totalPages);
				topicsTotalSize.set(res.data.totalSize);
			}
			grants.set(res.data.content);
			grantsCurrentPage = page;
			updateRetrievalTimestamp(retrievedTimestamps, 'grants');
		} catch (err) {
			userValidityCheck.set(true);

			errorMessage(errorMessages['grants']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		detailView.set('first run');

		headerTitle.set(messages['grants']['title']);
		await reloadAllGrants();

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

	const deleteSelectedGrants = async () => {
		try {
			for (const duration of grantRowsSelected) {
				await httpAdapter.delete(`/application_grants/${duration.id}`);
			}
			updateRetrievalTimestamp(retrievedTimestamps, 'durations');
		} catch (err) {
			errorMessage(errorMessages['grants']['deleting.error.title'], err.message);
		}
	};

	const deselectAllGrantsCheckboxes = () => {
		grantAllRowsSelectedTrue = false;
		grantRowsSelectedTrue = false;
		grantRowsSelected = [];
		let checkboxes = document.querySelectorAll('.grants-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.grants-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const addGrant = async (newGrant) => {
		const config = {
			headers: {
				accept: 'application/json',
				APPLICATION_GRANT_TOKEN: newGrant.applicationGrantToken
			}
		};
		delete newGrant['applicationGrantToken'];

		const res = await httpAdapter.post(`/application_grants`, newGrant, config).catch((err) => {
			addGrantVisible = false;
			errorMessage(errorMessages['grants']['adding.error.title'], err.message);
		});

		addGrantVisible = false;

		if (res === undefined) {
			errorMessage(
				errorMessages['grants']['adding.error.title'],
				errorMessages['grants']['exists']
			);
		} else {
			reloadAllGrants();
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

	const loadGrant = () => {
		addGrantVisible = false;
		grantsListVisible = false;
		grantDetailVisible = true;
	};
</script>

<svelte:head>
	<title>{messages['grants']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Durations" />
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

			{#if addGrantVisible}
				<GrantModal
					title={messages['grants']['add']}
					actionAddGrant={true}
					on:cancel={() => (addGrantVisible = false)}
					on:addGrant={(e) => {
						addGrant(e.detail);
					}}
				/>
			{/if}

			{#if grantDetailVisible && !grantsListVisible}
				<GrantDetails {isTopicAdmin} {selectedGrant} />
			{/if}

			{#if deleteGrantVisible}
				<Modal
					actionDeleteTopics={true}
					title="{messages['grants']['delete.title']} {grantRowsSelected.length > 1
						? messages['grants']['delete.multiple']
						: messages['grants']['delete.single']}"
					on:cancel={() => {
						if (grantRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
							grantRowsSelected = [];
						deleteGrantVisible = false;
					}}
					on:deleteTopics={async () => {
						await deleteSelectedGrants();
						reloadAllGrants();
						deselectAllGrantsCheckboxes();
						deleteGrantVisible = false;
					}}
				/>
			{/if}
			{#if !grantDetailVisible}
				{#if $topicsTotalSize !== undefined && $topicsTotalSize != NaN}
					<div class="content">
						<h1 data-cy="durations">{messages['grants']['title']}</h1>

						<form class="searchbox">
							<input
								data-cy="search-durations-table"
								class="searchbox"
								type="search"
								placeholder={messages['grants']['search.placeholder']}
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
								>{messages['grants']['search.clear.button']}</button
							>
						{/if}

						<img
							src={deleteSVG}
							alt="options"
							class="dot"
							class:button-disabled={(!$isAdmin && !isTopicAdmin) || grantRowsSelected.length === 0}
							style="margin-left: 0.5rem; margin-right: 1rem"
							on:click={() => {
								if (grantRowsSelected.length > 0) deleteGrantVisible = true;
							}}
							on:keydown={(event) => {
								if (event.which === returnKey) {
									if (grantRowsSelected.length > 0) deleteGrantVisible = true;
								}
							}}
							on:mouseenter={() => {
								deleteMouseEnter = true;
								if ($isAdmin || isTopicAdmin) {
									if (grantRowsSelected.length === 0) {
										deleteToolip = messages['grants']['delete.tooltip'];
										const tooltip = document.querySelector('#delete-durations');
										setTimeout(() => {
											if (deleteMouseEnter) {
												tooltip.classList.remove('tooltip-hidden');
												tooltip.classList.add('tooltip');
											}
										}, 1000);
									}
								} else {
									deleteToolip = messages['grants']['delete.tooltip.duration.admin.required'];
									const tooltip = document.querySelector('#delete-durations');
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
								if (grantRowsSelected.length === 0) {
									const tooltip = document.querySelector('#delete-durations');
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
							id="delete-durations"
							class="tooltip-hidden"
							style="margin-left: 12.2rem; margin-top: -1.8rem"
							>{deleteToolip}
						</span>

						<img
							data-cy="add-duration"
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
									addGrantVisible = true;
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
										addGrantVisible = true;
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
									addTooltip = messages['grants']['add.tooltip.duration.admin.required'];
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
									addTooltip = messages['grants']['add.tooltip.select.group'];
									const tooltip = document.querySelector('#add-durations');
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
								const tooltip = document.querySelector('#add-durations');
								setTimeout(() => {
									if (!addMouseEnter) {
										tooltip.classList.add('tooltip-hidden');
										tooltip.classList.remove('tooltip');
									}
								}, waitTime);
							}}
						/>
						<span
							id="add-durations"
							class="tooltip-hidden"
							style="margin-left: 8rem; margin-top: -1.8rem"
							>{addTooltip}
						</span>

						{#if $grants?.length > 0 && grantsListVisible}
							<table data-cy="durations-table" class="main" style="margin-top: 0.5rem">
								<thead>
									<tr style="border-top: 1px solid black; border-bottom: 2px solid">
										{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
											<td style="line-height: 1rem;">
												<input
													tabindex="-1"
													type="checkbox"
													class="grants-checkbox"
													style="margin-right: 0.5rem"
													bind:indeterminate={grantRowsSelectedTrue}
													on:click={(e) => {
														if (e.target.checked) {
															grantRowsSelected = $grants;
															grantRowsSelectedTrue = false;
															grantAllRowsSelectedTrue = true;
														} else {
															grantAllRowsSelectedTrue = false;
															grantRowsSelectedTrue = false;
															grantRowsSelected = [];
														}
													}}
													checked={grantAllRowsSelectedTrue}
												/>
											</td>
										{/if}
										<td class="header-column" style="min-width: 7rem"
											>{messages['grants']['table.column.one']}</td
										>
										<td class="header-column">{messages['grants']['table.column.two']}</td>
										<td class="header-column">{messages['grants']['table.column.three']}</td>
										<td class="header-column">{messages['grants']['table.column.four']}</td>
										<td class="header-column">{messages['grants']['table.column.five']}</td>
									</tr>
								</thead>
								<tbody>
									{#each $grants as grant}
										<tr>
											{#if $permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true) || $isAdmin}
												<td style="line-height: 1rem; width: 2rem; ">
													<input
														tabindex="-1"
														type="checkbox"
														class="grants-checkbox"
														checked={grantAllRowsSelectedTrue}
														on:change={(e) => {
															if (e.target.checked === true) {
																grantRowsSelected.push(grant);
																// reactive statement
																grantRowsSelected = grantRowsSelected;
																grantRowsSelectedTrue = true;
															} else {
																grantRowsSelected = grantRowsSelected.filter(
																	(selection) => selection !== grant
																);
																if (grantRowsSelected.length === 0) {
																	grantRowsSelectedTrue = false;
																}
															}
														}}
													/>
												</td>
											{/if}

											<!-- svelte-ignore a11y-click-events-have-key-events -->
											<td
												style="cursor: pointer; width: max-content"
												data-cy="duration-name"
												on:click={() => {
													selectedGrant = grant;
													loadGrant();
												}}
												on:keydown={(event) => {
													if (event.which === returnKey) {
														selectedGrant = grant;
														loadGrant();
													}
												}}
												>{grant.name}
											</td>

											<td>{grant.groupName}</td>

											<td data-cy="grant-duration">{getDuration(grant)}</td>

											<td>{grant.applicationName}</td>
											<td>{grant.applicationGroupName}</td>

											{#if $isAdmin || isTopicAdmin}
												<td style="cursor: pointer; width:1rem">
													<!-- svelte-ignore a11y-click-events-have-key-events -->
													<img
														data-cy="detail-application-icon"
														src={detailSVG}
														height="18rem"
														width="18rem"
														alt="edit user"
														style="vertical-align: -0.2rem"
														on:click={() => {
															selectedGrant = grant;
															loadGrant();
														}}
														on:keydown={(event) => {
															if (event.which === returnKey) {
																selectedGrant = grant;
																loadGrant();
															}
														}}
													/>
												</td>

												<td
													style="cursor: pointer; text-align: right; padding-right: 0.25rem; width: 1rem"
												>
													<!-- svelte-ignore a11y-click-events-have-key-events -->
													<img
														data-cy="delete-duration-icon"
														src={deleteSVG}
														width="27px"
														height="27px"
														style="vertical-align: -0.45rem"
														alt="delete duration"
														disabled={!$isAdmin || !isTopicAdmin}
														on:click={() => {
															if (!grantRowsSelected.some((tpc) => tpc === grant))
																grantRowsSelected.push(grant);
															deleteGrantVisible = true;
														}}
													/>
												</td>
											{/if}
										</tr>
									{/each}
								</tbody>
							</table>
						{:else if !editGrantVisible}
							<p>
								{messages['grants']['empty.grants']}
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
												addGrantVisible = true;
											else if (
												!$groupContext &&
												($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)
											)
												showSelectGroupContext.set(true);
										}}
									>
										{messages['grants']['empty.grants.action.two']}
									</span>
									{messages['grants']['empty.grants.action.result']}
								{:else if !$groupContext && ($permissionsByGroup?.some((gm) => gm.isTopicAdmin === true) || $isAdmin)}
									{messages['grants']['empty.grants.action']}
								{/if}
							</p>
						{/if}
					</div>

					<div class="pagination">
						<span>{messages['pagination']['rows.per.page']}</span>
						<select
							tabindex="-1"
							on:change={(e) => {
								gransPerPage = e.target.value;
								reloadAllGrants();
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
								{1 + grantsCurrentPage * gransPerPage}
							{:else}
								0
							{/if}
							- {Math.min(gransPerPage * (grantsCurrentPage + 1), $topicsTotalSize)} of
							{$topicsTotalSize}
						</span>

						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagefirstSVG}
							alt="first page"
							class="pagination-image"
							class:disabled-img={grantsCurrentPage === 0}
							on:click={() => {
								deselectAllGrantsCheckboxes();
								if (grantsCurrentPage > 0) {
									grantsCurrentPage = 0;
									reloadAllGrants();
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagebackwardsSVG}
							alt="previous page"
							class="pagination-image"
							class:disabled-img={grantsCurrentPage === 0}
							on:click={() => {
								deselectAllGrantsCheckboxes();
								if (grantsCurrentPage > 0) {
									grantsCurrentPage--;
									reloadAllGrants(grantsCurrentPage);
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pageforwardSVG}
							alt="next page"
							class="pagination-image"
							class:disabled-img={grantsCurrentPage + 1 === $topicsTotalPages ||
								$grants?.length === undefined}
							on:click={() => {
								deselectAllGrantsCheckboxes();
								if (grantsCurrentPage + 1 < $topicsTotalPages) {
									grantsCurrentPage++;
									reloadAllGrants(grantsCurrentPage);
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagelastSVG}
							alt="last page"
							class="pagination-image"
							class:disabled-img={grantsCurrentPage + 1 === $topicsTotalPages ||
								$grants?.length === undefined}
							on:click={() => {
								deselectAllGrantsCheckboxes();
								if (grantsCurrentPage < $topicsTotalPages) {
									grantsCurrentPage = $topicsTotalPages - 1;
									reloadAllGrants(grantsCurrentPage);
								}
							}}
						/>
					</div>
					<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['grants']} />
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

	.header-column {
		font-weight: 600;
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

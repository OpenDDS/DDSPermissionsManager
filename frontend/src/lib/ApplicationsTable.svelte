<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { onMount, afterUpdate } from 'svelte';
	import { isAdmin } from '../stores/authentication';
	import { httpAdapter } from '../appconfig';
	import permissionsByGroup from '../stores/permissionsByGroup';
	import Modal from './Modal.svelte';
	import RetrievedTimestamp from './RetrievedTimestamp.svelte';
	import applications from '../stores/applications';
	import detailView from '../stores/detailView';
	import pageforwardSVG from '../icons/pageforward.svg';
	import pagebackwardsSVG from '../icons/pagebackwards.svg';
	import pagefirstSVG from '../icons/pagefirst.svg';
	import pagelastSVG from '../icons/pagelast.svg';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import userValidityCheck from '../stores/userValidityCheck';
	import groupContext from '../stores/groupContext';
	import applicationsTotalPages from '../stores/applicationsTotalPages';
	import applicationsTotalSize from '../stores/applicationsTotalSize';
	import retrievedTimestamps from '../stores/retrievedTimestamps.js';
	import { updateRetrievalTimestamp } from '../utils.js';

	// Group Context
	$: if ($groupContext?.id) reloadAllApps();

	// Promises
	let promise;

	// Authentication
	let isApplicationAdmin = false;

	// Error Handling
	let errorMsg, errorObject;

	// Modals
	let errorMessageVisible = false;

	// Tables
	let applicationsPerPage = 10;

	// Pagination
	let applicationsCurrentPage = 0;

	const reloadAllApps = async (page = 0) => {
		try {
			let res = await httpAdapter.get(
				`/applications?page=${page}&size=${applicationsPerPage}&group=${$groupContext.id}`
			);

			if (res.data) {
				applicationsTotalPages.set(res.data.totalPages);
				applicationsTotalSize.set(res.data.totalSize);
			}
			applications.set(res.data.content);
			applicationsCurrentPage = page;

			updateRetrievalTimestamp(retrievedTimestamps, 'applications');
		} catch (err) {
			userValidityCheck.set(true);
			applications.set();
			errorMessage(errorMessages['application']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		detailView.set('first run');

		if (document.querySelector('.content') == null) promise = await reloadAllApps();

		if ($permissionsByGroup) {
			isApplicationAdmin = $permissionsByGroup.some(
				(groupPermission) => groupPermission.isApplicationAdmin === true
			);
		}
	});

	afterUpdate(async () => {
		promise = await reloadAllApps();
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
</script>

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

{#await promise then _}
	<div class="content">
		{#if $applicationsTotalSize !== undefined && $applicationsTotalSize != NaN}
				{#if $applications?.length > 0}
					<table
						data-cy="applications-table"
						class="main-table"
						style="margin-top:0.5rem; min-width: 50rem; width:max-content"
						class:application-table-admin={isApplicationAdmin || $isAdmin}
					>
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								<td class="header-column" style="line-height: 2.2rem; min-width: 7rem"
									>{messages['application']['table.column.one']}</td
								>
								<td class="header-column">{messages['application']['table.column.two']}</td>
							</tr>
						</thead>

						{#if $applications.length > 0}
							<tbody>
								{#each $applications as app, i}
									<tr>
										<td>{app.name}</td>
										<td style="padding-left: 0.5rem">{app.id}</td>
									</tr>
								{/each}
							</tbody>
						{/if}
					</table>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<div class="pagination">
						<span>{messages['pagination']['rows.per.page']}</span>
						<select
							tabindex="-1"
							on:change={(e) => {
								applicationsPerPage = e.target.value;
								reloadAllApps();
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
							{#if $applicationsTotalSize > 0}
								{1 + applicationsCurrentPage * applicationsPerPage}
							{:else}
								0
							{/if}
							- {Math.min(
								applicationsPerPage * (applicationsCurrentPage + 1),
								$applicationsTotalSize
							)}
							of
							{$applicationsTotalSize}
						</span>
	
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagefirstSVG}
							alt="first page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage === 0}
							on:click={() => {
								if (applicationsCurrentPage > 0) {
									applicationsCurrentPage = 0;
									reloadAllApps();
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagebackwardsSVG}
							alt="previous page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage === 0}
							on:click={() => {
								if (applicationsCurrentPage > 0) {
									applicationsCurrentPage--;
									reloadAllApps(applicationsCurrentPage);
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pageforwardSVG}
							alt="next page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage + 1 === $applicationsTotalPages ||
								$applications?.length === undefined}
							on:click={() => {
								if (applicationsCurrentPage + 1 < $applicationsTotalPages) {
									applicationsCurrentPage++;
									reloadAllApps(applicationsCurrentPage);
								}
							}}
						/>
						<img
							src={pagelastSVG}
							alt="last page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage + 1 === $applicationsTotalPages ||
								$applications?.length === undefined}
							on:click={() => {
								if (applicationsCurrentPage < $applicationsTotalPages) {
									applicationsCurrentPage = $applicationsTotalPages - 1;
									reloadAllApps(applicationsCurrentPage);
								}
							}}
						/>
					</div>	
				{/if}
		{/if}

		<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['applications']} />
	</div>
{/await}

<style>
	.content {
		width: 100%;
		min-width: 45rem;
	}

	td {
		height: 2.2rem;
	}

	.header-column {
		font-weight: 600;
	}
</style>

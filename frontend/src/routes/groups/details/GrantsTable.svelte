<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../../appconfig';
	import permissionsByGroup from '../../../stores/permissionsByGroup';
	import Modal from '../../../lib/Modal.svelte';
	import RetrievedTimestamp from '../../../lib/RetrievedTimestamp.svelte';
	import userValidityCheck from '../../../stores/userValidityCheck';
	import pageforwardSVG from '../../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../../icons/pagefirst.svg';
	import pagelastSVG from '../../../icons/pagelast.svg';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import groupContext from '../../../stores/groupContext';
	import topicsTotalSize from '../../../stores/topicsTotalSize';
	import topicsTotalPages from '../../../stores/topicsTotalPages';
	import grants from '../../../stores/grants';
	import retrievedTimestamps from '../../../stores/retrievedTimestamps';
	import { updateRetrievalTimestamp } from '../../../utils.js';
	import { convertFromMilliseconds } from '../../../utils';
	import detailView from '../../../stores/detailView';

	// Group Context
	$: if ($groupContext?.id) reloadAllGrants();

	// Promises
	let promise;

	// Tables
	let gransPerPage = 10;

	// Authentication
	let isTopicAdmin = false;

	// Error Handling
	let errorMsg, errorObject;

	//Pagination
	let grantsCurrentPage = 0;

	// Modals
	let errorMessageVisible = false;

	const reloadAllGrants = async (page = 0) => {
		try {
			let res = await httpAdapter.get(
				`/application_grants?page=${page}&size=${gransPerPage}&group=${$groupContext.id}`
			);

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

		{#if $topicsTotalSize !== undefined && $topicsTotalSize != NaN}
			<div class="content">
				{#if $grants?.length > 0}
					<table data-cy="durations-table" class="main" style="margin-top:0.5rem; min-width: 50rem; width:max-content;">
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								<td class="header-column" style="min-width: 7rem"
									>{messages['grants']['table.column.one']}</td
								>
								<td class="header-column">{messages['grants']['table.column.three']}</td>
								<td class="header-column">{messages['grants']['table.column.four']}</td>
							</tr>
						</thead>
						<tbody>
							{#each $grants as grant}
								<tr>
									<td data-cy="duration-name">{grant.name}</td>
									<td data-cy="grant-duration">{getDuration(grant)}</td>
									<td>{grant.applicationName}</td>
								</tr>
							{/each}
						</tbody>
					</table>
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
						if (grantsCurrentPage < $topicsTotalPages) {
							grantsCurrentPage = $topicsTotalPages - 1;
							reloadAllGrants(grantsCurrentPage);
						}
					}}
				/>
			</div>
			<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['grants']} />
		{/if}

{/await}

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

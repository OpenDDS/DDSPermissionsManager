<script>
	import { onMount } from 'svelte';
	import messages from '$lib/messages.json';
	import errorMessages from '$lib/errorMessages.json';
	import { httpAdapter } from '../appconfig';
	import retrievedTimestamps from '../stores/retrievedTimestamps.js';
	import { updateRetrievalTimestamp } from '../utils.js';
	import groupContext from '../stores/groupContext';
	import permissionsByGroup from '../stores/permissionsByGroup';
	import userValidityCheck from '../stores/userValidityCheck';
	import detailView from '../stores/detailView';
	import topicsTotalSize from '../stores/topicsTotalSize';
	import topicsTotalPages from '../stores/topicsTotalPages';
	import topicsA from '../stores/topicsA';
	import RetrievedTimestamp from './RetrievedTimestamp.svelte';
	import Modal from './Modal.svelte';
	import pageforwardSVG from '../icons/pageforward.svg';
	import pagebackwardsSVG from '../icons/pagebackwards.svg';
	import pagefirstSVG from '../icons/pagefirst.svg';
	import pagelastSVG from '../icons/pagelast.svg';

	// Group Context
	$: if ($groupContext?.id) reloadAllTopics();

	// Promises
	let promise;

	// Tables
	let topicsPerPage = 10;

	// Authentication
	let isTopicAdmin = false;

	// Error Handling
	let errorMsg, errorObject;

	//Pagination
	let topicsCurrentPage = 0;

	// Modals
	let errorMessageVisible = false;

	const reloadAllTopics = async (page = 0) => {
		try {
			let res = await httpAdapter.get(
				`/topics?page=${page}&size=${topicsPerPage}&group=${$groupContext.id}`
			);

			if (res.data) {
				topicsTotalPages.set(res.data.totalPages);
				topicsTotalSize.set(res.data.totalSize);
			}
			topicsA.set(res.data.content);
			topicsCurrentPage = page;

			updateRetrievalTimestamp(retrievedTimestamps, 'topics');
		} catch (err) {
			userValidityCheck.set(true);

			errorMessage(errorMessages['topic']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		detailView.set('first run');

		if (document.querySelector('.content') == null) promise = await reloadAllTopics();

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
</script>

{#await promise then _}
	<div class="content">
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

		{#if $topicsA?.length > 0}
			<table data-cy="topics-table" class="main" style="margin-top: 0.5rem;min-width: 50rem; width:max-content">
				<thead>
					<tr style="border-top: 1px solid black; border-bottom: 2px solid">
						<td class="header-column" style="min-width: 7rem">{messages['topic']['table.column.one']}</td>
						<td class="header-column">{messages['topic']['table.column.three']}</td>
					</tr>
				</thead>
				<tbody>
				{#each $topicsA as topic}
					<tr>
						<td>{topic.name}</td>
						<td style="padding-left: 0.5rem">{topic.id}</td>
					</tr>
				{/each}
				</tbody>
			</table>
		{/if}

		<div class="pagination">
			<span>{messages['pagination']['rows.per.page']}</span>
			<select
				tabindex="-1"
				on:change={(e) => {
					topicsPerPage = e.target.value;
					reloadAllTopics();
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
					{1 + topicsCurrentPage * topicsPerPage}
				{:else}
					0
				{/if}
				- {Math.min(topicsPerPage * (topicsCurrentPage + 1), $topicsTotalSize)} of
				{$topicsTotalSize}
			</span>

			<!-- svelte-ignore a11y-click-events-have-key-events -->
			<img
				src={pagefirstSVG}
				alt="first page"
				class="pagination-image"
				class:disabled-img={topicsCurrentPage === 0}
				on:click={() => {
					if (topicsCurrentPage > 0) {
						topicsCurrentPage = 0;
						reloadAllTopics();
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
					if (topicsCurrentPage > 0) {
						topicsCurrentPage--;
						reloadAllTopics(topicsCurrentPage);
					}
				}}
			/>
			<!-- svelte-ignore a11y-click-events-have-key-events -->
			<img
				src={pageforwardSVG}
				alt="next page"
				class="pagination-image"
				class:disabled-img={topicsCurrentPage + 1 === $topicsTotalPages ||
					$topicsA?.length === undefined}
				on:click={() => {
					if (topicsCurrentPage + 1 < $topicsTotalPages) {
						topicsCurrentPage++;
						reloadAllTopics(topicsCurrentPage);
					}
				}}
			/>
			<!-- svelte-ignore a11y-click-events-have-key-events -->
			<img
				src={pagelastSVG}
				alt="last page"
				class="pagination-image"
				class:disabled-img={topicsCurrentPage + 1 === $topicsTotalPages ||
					$topicsA?.length === undefined}
				on:click={() => {
					if (topicsCurrentPage < $topicsTotalPages) {
						topicsCurrentPage = $topicsTotalPages - 1;
						reloadAllTopics(topicsCurrentPage);
					}
				}}
			/>
		</div>
		<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['topics']} />
	</div>
{/await}

<style>
	table.main {
		min-width: 43.5rem;
		line-height: 2.2rem;
	}

	.content {
		width: 100%;
		min-width: fit-content;
		margin-right: 1rem;
	}

	.header-column {
		font-weight: 600
	}
</style>

<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import Select from 'svelte-select';
	import moment from 'moment';
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import editSVG from '../../icons/edit.svg';
	import { httpAdapter } from '../../appconfig';
	import topicSetsDetails from '../../stores/topicSetsDetails';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import deleteSVG from '../../icons/delete.svg';
	import AdminDetails from '../../lib/AdminDetails.svelte';
	import SearchIcon from './SearchIcon.svelte';
	import groupContext from '../../stores/groupContext';
	import Modal from '../../lib/Modal.svelte';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';

	export let selectedTopicSetId, isTopicAdmin;

	let selectedTopicsSetName,
		selectedTopicsSetGroupName,
		selectedTopicsSetGroupId,
		selectedTopicApplications = [],
		selectedTopicSetUpdateDate,
		topicCurrentGroupPublic;
	// Selection
	let grantsRowsSelected = [],
		grantsRowsSelectedTrue = false,
		grantsAllRowsSelectedTrue = false;

	// checkboxes selection
	$: if (selectedTopicApplications?.length === grantsRowsSelected?.length) {
		grantsRowsSelectedTrue = false;
		grantsAllRowsSelectedTrue = true;
	} else if (grantsRowsSelected?.length > 0) {
		grantsRowsSelectedTrue = true;
	} else {
		grantsAllRowsSelectedTrue = false;
	}

	// Success Message
	let notifyApplicationAccessTypeSuccess = false;
	$: if (notifyApplicationAccessTypeSuccess) {
		setTimeout(() => (notifyApplicationAccessTypeSuccess = false), waitTime);
	}

	// Promises
	let promise;

	// Modals
	let errorMessageVisible = false,
		editTopicSetNameVisible = false;

	// Constants
	const returnKey = 13,
		waitTime = 1000;

	const minCharactersToTriggerSearch = 3;

	// Error Handling
	let errorMsg, errorObject;

	// Editing
	let topicSearchString = '';
	let topicsRowsSelectedTrue = false;
	let topicsAllRowsSelectedTrue = false;
	let topicsRowsSelected = [];
	let deleteTopicVisible = false;

	// Messages
	let deleteTooltip;
	let deleteMouseEnter = false;

	const fetchAndUpdateTopicSet = async () => {
		promise = await httpAdapter.get(`/topic-sets/${selectedTopicSetId}`);
		topicSetsDetails.set(promise.data);

		await loadApplicationPermissions(selectedTopicSetId);
		selectedTopicSetId = $topicSetsDetails.id;
		selectedTopicsSetName = $topicSetsDetails.name;
		selectedTopicsSetGroupName = $topicSetsDetails.groupName;
		selectedTopicsSetGroupId = $topicSetsDetails.groupId;
		selectedTopicSetUpdateDate = $topicSetsDetails.dateUpdated;

		topicCurrentGroupPublic = await getGroupVisibilityPublic(selectedTopicsSetGroupName);
		headerTitle.set(selectedTopicsSetName);
		detailView.set(true);
	};

	onMount(async () => {
		try {
			await fetchAndUpdateTopicSet();
		} catch (err) {
			errorMessage(errorMessages['topic-sets']['loading.detail.error.title'], err.message);
		}
	});

	const errorMessage = (errMsg, errObj) => {
		errorMsg = errMsg;
		errorObject = errObj;
		errorMessageVisible = true;
	};

	const loadApplicationPermissions = async (topicId) => {
		const resApps = await httpAdapter.get(`/application_permissions/topic/${topicId}`);
		selectedTopicApplications = resApps.data.content;
	};

	const saveNewTopic = async (topicToAdd) => {
		try {
			const res = await httpAdapter.post(`/topic-sets/${selectedTopicSetId}/${topicToAdd.id}`);
			topicSetsDetails.set(res.data);
		} catch (err) {
			errorMessage(err.message);
		}
	};

	const saveNewTopicSetName = async (newTopicSetName) => {
		try {
			const res = await httpAdapter.put(`/topic-sets/${selectedTopicSetId}`, {
				name: newTopicSetName
			});
			topicSetsDetails.set(res.data);
		} catch (err) {
			errorMessage(errorMessages['topic-sets']['adding.error.title'], err.message);
		}
	};

	const getGroupVisibilityPublic = async (groupName) => {
		try {
			const res = await httpAdapter.get(`/groups?filter=${groupName}`);
			if (res.data.content?.length > 0 && res.data?.content[0]?.public) return true;
			else return false;
		} catch (err) {
			errorMessage(errorMessages['group']['error.loading.visibility'], err.message);
		}
	};

	const loadOptions = async (filterText) => {
		if (!filterText.length || filterText.length < minCharactersToTriggerSearch)
			return Promise.resolve([]);
		try {
			const topicsResponse = await httpAdapter.get(
				`/topics?filter=${topicSearchString}&group=${selectedTopicsSetGroupId}`
			);

			if (topicsResponse.data.content && topicsResponse.data.content.length) {
				const topicIdsOfTopicSet = $topicSetsDetails.topics?.map((topic) => {
					return Number(Object.keys(topic)[0]);
				});
				const searchedTopics = topicsResponse.data.content.filter((topic) => {
					return !topicIdsOfTopicSet?.includes(topic.id);
				});

				const mappedTopics = searchedTopics.map((topic) => {
					return {
						label: topic.name,
						value: topic.name,
						id: topic.id,
						groupId: topic.group
					};
				});

				return mappedTopics;
			} else {
				return [];
			}
		} catch (err) {
			errorMessage(errorMessages['topic-sets']['loading.error.title'], err.message);
		}
	};

	let selectedTopic = '';

	const onTopicSelect = () => {
		const topicToAdd = {
			name: selectedTopic.value,
			id: selectedTopic.id,
			groupId: selectedTopic.groupId
		};

		saveNewTopic(topicToAdd);
		selectedTopic = '';
	};

	// checkboxes selection
	$: if ($topicSetsDetails?.topics?.length === topicsRowsSelected?.length) {
		topicsRowsSelectedTrue = false;
		topicsAllRowsSelectedTrue = true;
	} else if (topicsRowsSelected?.length > 0) {
		topicsRowsSelectedTrue = true;
	} else {
		topicsAllRowsSelectedTrue = false;
	}

	const deselectAllTopicsCheckboxes = () => {
		topicsAllRowsSelectedTrue = false;
		topicsRowsSelectedTrue = false;
		topicsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.topics-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.topics-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const deleteSelectedTopics = async () => {
		try {
			for (const topic of topicsRowsSelected) {
				const topicId = Object.keys(topic)[0];
				await httpAdapter.delete(`/topic-sets/${selectedTopicSetId}/${topicId}`);
			}
		} catch (err) {
			errorMessage(errorMessages['topic']['deleting.error.title'], err.message);
		}
	};

	$: timeAgo = moment($topicSetsDetails.dateUpdated).fromNow();
	$: browserFormat = new Date($topicSetsDetails.dateUpdated).toLocaleString();

	//
</script>

{#if $isAuthenticated}
	{#if errorMessageVisible}
		<Modal
			title={errorMsg}
			errorMsg={true}
			errorDescription={errorObject}
			closeModalText={errorMessages['modal']['button.close']}
			on:cancel={() => (errorMessageVisible = false)}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					errorMessageVisible = false;
				}
			}}
		/>
	{/if}
	<div class="content">
		<div style="display: inline-flex; align-items: baseline">
			<table class="topics-details">
				<tr>
					<td>{messages['topic-sets.detail']['row.one']}</td>
					<td>{$topicSetsDetails.name}</td>
				</tr>

				<tr>
					<td>{messages['topic-sets.detail']['row.two']}</td>
					<td>{$topicSetsDetails.groupName}</td>
				</tr>

				<tr>
					<td>Admins:</td>
					<td>
						{#if selectedTopicsSetGroupId}
							<AdminDetails
								groupId={selectedTopicsSetGroupId}
								adminCategory="topic"
							/>
						{/if}
					</td>
				</tr>
			</table>

			{#if $isAdmin || isTopicAdmin}
				<!-- svelte-ignore a11y-click-events-have-key-events -->
				<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
				<img
					src={editSVG}
					tabindex="0"
					alt="edit topic"
					height="20rem"
					style="margin-left: 2rem; cursor:pointer"
					on:click={() => (editTopicSetNameVisible = true)}
				/>
			{/if}
		</div>

		{#if $topicSetsDetails.dateUpdated}
			<p style="font-weight: 200; margin-bottom: 2rem;">Last updated {timeAgo} ({browserFormat})</p>
		{/if}
		<div style="font-size:1.3rem; margin-bottom: 1rem">Topics</div>
		<div class="search-delete-container">
			<div class="search-wrapper">
				<Select
					disabled={!$isAdmin && !isTopicAdmin}
					type="search"
					{loadOptions}
					bind:filterText={topicSearchString}
					on:change={onTopicSelect}
					placeholder="Search and add a topic"
					bind:value={selectedTopic}
				>
					<div slot="prepend" style="padding: 3px 10px 0 0">
						<SearchIcon />
					</div>
				</Select>
			</div>
			{#if $isAdmin || isTopicAdmin}
				<img
					src={deleteSVG}
					alt="options"
					class="dot"
					class:button-disabled={(!$isAdmin && !isTopicAdmin) || topicsRowsSelected.length === 0}
					style="margin-left: 0.5rem; margin-right: 1rem"
					on:click={() => {
						if (topicsRowsSelected.length > 0) deleteTopicVisible = true;
					}}
					on:keydown={(event) => {
						if (event.which === returnKey) {
							if (topicsRowsSelected.length > 0) deleteTopicVisible = true;
						}
					}}
					on:mouseenter={() => {
						deleteMouseEnter = true;
						if ($isAdmin || isTopicAdmin) {
							if (topicsRowsSelected.length === 0) {
								deleteTooltip = messages['topic-sets.detail']['delete.tooltip'];
								const tooltip = document.querySelector('#delete-topics');
								setTimeout(() => {
									if (deleteMouseEnter) {
										tooltip.classList.remove('tooltip-hidden');
										tooltip.classList.add('tooltip');
									}
								}, 1000);
							}
						} else {
							deleteTooltip = messages['topic-sets.detail']['delete.tooltip.topic.admin.required'];
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
						if (topicsRowsSelected.length === 0) {
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
			{/if}
			<span
				id="delete-topics"
				class="tooltip-hidden"
				style="margin-left: 12.2rem; margin-top: -1.8rem"
				>{deleteTooltip}
			</span>
		</div>
		{#if $topicSetsDetails.topics?.length}
			<table data-cy="topics-table" class="main" style="margin-top: 0.5rem">
				<thead>
					<tr style="border-top: 1px solid black; border-bottom: 2px solid">
						{#if isTopicAdmin || $isAdmin}
							<td style="line-height: 1rem;">
								<input
									tabindex="-1"
									type="checkbox"
									class="topics-checkbox"
									style="margin-right: 0.5rem"
									bind:indeterminate={topicsRowsSelectedTrue}
									on:click={(e) => {
										if (e.target.checked) {
											topicsRowsSelected = $topicSetsDetails?.topics;
											topicsRowsSelectedTrue = false;
											topicsAllRowsSelectedTrue = true;
										} else {
											topicsAllRowsSelectedTrue = false;
											topicsRowsSelectedTrue = false;
											topicsRowsSelected = [];
										}
									}}
									checked={topicsAllRowsSelectedTrue}
								/>
							</td>
						{/if}
						<td class="header-column" style="min-width: 7rem"
							>{messages['topic-sets.detail']['table.column.one']}</td
						>
					</tr>
				</thead>
				<tbody>
					{#each $topicSetsDetails?.topics as topic}
						<tr>
							{#if isTopicAdmin || $isAdmin}
								<td style="line-height: 1rem; width: 2rem; ">
									<input
										tabindex="-1"
										type="checkbox"
										class="topics-checkbox"
										checked={topicsAllRowsSelectedTrue}
										on:change={(e) => {
											if (e.target.checked === true) {
												topicsRowsSelected.push(topic);
												// reactive statement
												topicsRowsSelected = topicsRowsSelected;
												topicsRowsSelectedTrue = true;
											} else {
												topicsRowsSelected = topicsRowsSelected.filter(
													(selection) => selection !== topic
												);
												if (topicsRowsSelected.length === 0) {
													topicsRowsSelectedTrue = false;
												}
											}
										}}
									/>
								</td>
							{/if}

							<td data-cy="topic" style="width: max-content">{Object.values(topic)[0]}</td>

							<td style="cursor: pointer; text-align: right; padding-right: 0.25rem; width: 1rem">
								{#if $isAdmin || isTopicAdmin}
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
											if (!topicsRowsSelected.some((tpc) => tpc === topic))
												topicsRowsSelected.push(topic);
											deleteTopicVisible = true;
										}}
									/>
								{/if}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{:else}
			<div class="no-topics">
				<p>{messages['topic-sets.detail']['no.topics.found']}</p>
			</div>
		{/if}
	</div>
	<p style="margin-top: 8rem">{messages['footer']['message']}</p>
{/if}

{#if deleteTopicVisible}
	<Modal
		actionDeleteTopicsFromTopicSet={true}
		title="{messages['topic-sets.detail']['delete.title']} {topicsRowsSelected.length > 1
			? messages['topic-sets.detail']['delete.multiple']
			: messages['topic-sets.detail']['delete.single']}"
		on:cancel={() => {
			if (topicsRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
				topicsRowsSelected = [];
			deleteTopicVisible = false;
		}}
		on:deleteTopics={async () => {
			await deleteSelectedTopics();
			await fetchAndUpdateTopicSet();
			deselectAllTopicsCheckboxes();
			deleteTopicVisible = false;
		}}
	/>
{/if}

{#if editTopicSetNameVisible}
	<Modal
		title={messages['topic-sets']['edit']}
		topicSetName={true}
		actionEditTopicSet={true}
		originalTopicSetName={$topicSetsDetails.name}
		newTopicSetName={$topicSetsDetails.name}
		topicCurrentGroupPublic={$groupContext?.public ?? false}
		on:cancel={() => (editTopicSetNameVisible = false)}
		on:addTopicSet={(e) => {
			saveNewTopicSetName(e.detail.newTopicSetName);
			editTopicSetNameVisible = false;
		}}
	/>
{/if}

<style>
	.topics-details {
		font-size: 12pt;
		width: 15rem;
		margin: 1.6rem 0;
	}

	.topics-details td {
		height: 2.2rem;
	}
	.search-delete-container {
		display: flex;
		align-items: center;
		margin: 1rem 0;
		justify-content: space-between;
	}
	.search-wrapper {
		width: 80%;
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

	.no-topics {
		margin-top: 4rem;
		margin-left: 1rem;
	}

	.header-column {
		font-weight: 600;
	}
</style>

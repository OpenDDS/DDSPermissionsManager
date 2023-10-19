<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import { isAuthenticated } from '../../stores/authentication';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import headerTitle from '../../stores/headerTitle';
	import Modal from '$lib/Modal.svelte';
	import GroupDetails from '../groups/GroupDetails.svelte';
	import TopicDetails from '../topics/TopicDetails.svelte';
	import ApplicationDetails from '../applications/ApplicationDetails.svelte';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import groupsSVG from '../../icons/groups.svg';
	import topicsSVG from '../../icons/topics.svg';
	import applicationsSVG from '../../icons/apps.svg';
	import pageforwardSVG from '../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../icons/pagefirst.svg';
	import pagelastSVG from '../../icons/pagelast.svg';
	import searchResults from '../../stores/searchResults';
	import searchResultsTotalPages from '../../stores/searchResultsTotalPages';
	import searchResultsTotalSize from '../../stores/searchResultsTotalSize';
	import universalSearchList from '../../stores/universalSearchList';

	export let data, errors;

	$: if ($universalSearchList) {
		groupDetails = false;
		topicDetails = false;
		applicationDetails = false;
	}

	// Pagination
	let searchResultsPerPage = 10;
	let searchResultsCurrentPage = 0;

	// Constants
	const returnKey = 13;
	const minCharactersToTriggerSearch = 3;
	const waitTime = 1000;

	// Screens
	let groupDetails = false,
		topicDetails = false,
		applicationDetails = false;
	let selectedGroup, selectedTopic, isTopicAdmin;

	// Application Parameters
	let selectedAppGroupId,
		selectedAppGroupName,
		selectedAppName,
		selectedAppId,
		selectedAppPublic,
		selectedAppDescription,
		appCurrentGroupPublic,
		isApplicationAdmin;

	// Timer
	let timer;

	// Promise
	let promise;

	// Error Handling
	let errorMsg, errorObject;
	let errorMessageVisible;

	// Search Feature
	$: if (universalSearchString?.trim().length >= minCharactersToTriggerSearch) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			getSearchResults();
		}, waitTime);
	}

	$: if (universalSearchString?.trim().length < minCharactersToTriggerSearch) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			getSearchResults();
		}, waitTime);
	}

	// Selection Options
	const UniversalSearchOption = {
		EVERYTHING: 'everything',
		GROUPS: 'groups',
		TOPICS: 'topics',
		APPLICATIONS: 'applications'
	};

	let universalSearchString,
		selectedUniversalSearchButton = [UniversalSearchOption.EVERYTHING];

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

	const getGroupVisibilityPublic = async (groupName) => {
		try {
			const res = await httpAdapter.get(`/groups?filter=${groupName}`);

			if (res.data.content?.length > 0 && res.data?.content[0]?.public) return true;
			else return false;
		} catch (err) {
			errorMessage(errorMessages['group']['error.loading.visibility'], err.message);
		}
	};

	const handleItemSelection = async (result) => {
		// Add the new path to the browser's history
		history.pushState({ path: '/search' }, 'Search', '/search');

		switch (result.type) {
			case 'GROUP':
				selectedGroup = result.entity;
				universalSearchList.set(false);
				groupDetails = true;
				break;
			case 'TOPIC':
				selectedTopic = result.entity;
				universalSearchList.set(false);

				if ($permissionsByGroup) {
					isTopicAdmin = $permissionsByGroup?.includes(
						(groupPermission) =>
							groupPermission.isTopicAdmin === true &&
							groupPermission.groupId === selectedTopic.group
					);
				}
				topicDetails = true;
				break;
			case 'APPLICATION':
				selectedAppId = result.entity.id;
				selectedAppGroupId = result.entity.group;
				selectedAppGroupName = result.entity.groupName;
				selectedAppName = result.entity.name;
				selectedAppPublic = result.entity.public;
				selectedAppDescription = result.entity.description;

				if ($permissionsByGroup) {
					isApplicationAdmin = $permissionsByGroup?.includes(
						(groupPermission) =>
							groupPermission.isApplicationAdmin === true &&
							groupPermission.groupId === selectedTopic.group
					);
				}

				appCurrentGroupPublic = await getGroupVisibilityPublic(selectedAppGroupName);

				universalSearchList.set(false);
				applicationDetails = true;
				break;
		}
	};

	const handleClick = (identifier) => {
		if (identifier !== 'EVERYTHING') {
			selectedUniversalSearchButton = selectedUniversalSearchButton.filter(
				(selection) => selection !== 'everything'
			);
		}

		switch (identifier) {
			case 'EVERYTHING':
				selectedUniversalSearchButton = [UniversalSearchOption.EVERYTHING];
				break;
			case 'GROUPS':
				const alreadySelectedGroup = selectedUniversalSearchButton.find(
					(selection) => selection === UniversalSearchOption.GROUPS
				);
				if (alreadySelectedGroup) {
					selectedUniversalSearchButton = selectedUniversalSearchButton.filter(
						(selection) => selection !== UniversalSearchOption.GROUPS
					);
				} else {
					selectedUniversalSearchButton.push(UniversalSearchOption.GROUPS);
				}
				break;
			case 'TOPICS':
				const alreadySelectedTopic = selectedUniversalSearchButton.find(
					(selection) => selection === UniversalSearchOption.TOPICS
				);
				if (alreadySelectedTopic) {
					selectedUniversalSearchButton = selectedUniversalSearchButton.filter(
						(selection) => selection !== UniversalSearchOption.TOPICS
					);
				} else {
					selectedUniversalSearchButton.push(UniversalSearchOption.TOPICS);
				}
				break;
			case 'APPLICATIONS':
				const alreadySelectedApplications = selectedUniversalSearchButton.find(
					(selection) => selection === UniversalSearchOption.APPLICATIONS
				);
				if (alreadySelectedApplications) {
					selectedUniversalSearchButton = selectedUniversalSearchButton.filter(
						(selection) => selection !== UniversalSearchOption.APPLICATIONS
					);
				} else {
					selectedUniversalSearchButton.push(UniversalSearchOption.APPLICATIONS);
				}
				break;
		}
		if (selectedUniversalSearchButton.length === 0)
			selectedUniversalSearchButton.push(UniversalSearchOption.EVERYTHING);

		getSearchResults();
	};

	const resolveParameters = () => {
		let parameters = '';

		if (selectedUniversalSearchButton.includes('groups')) parameters = '&groups=true';
		if (selectedUniversalSearchButton.includes('topics')) parameters = parameters + '&topics=true';
		if (selectedUniversalSearchButton.includes('applications'))
			parameters = parameters + '&applications=true';

		return parameters;
	};

	const getSearchResults = async (page = 0) => {
		try {
			let parameters;

			parameters = resolveParameters();

			if (universalSearchString?.length >= minCharactersToTriggerSearch) {
				if (selectedUniversalSearchButton.includes('everything'))
					promise = await httpAdapter.get(`/search?query=${universalSearchString}`);
				else {
					parameters = resolveParameters();

					promise = await httpAdapter.get(`/search?query=${universalSearchString}${parameters}`);
				}
			} else {
				promise = await httpAdapter.get(`/search?${parameters}`);
			}

			searchResults.set(promise.data?.content);
			searchResultsTotalPages.set(promise.data?.totalPages);
			searchResultsTotalSize.set(promise.data?.totalSize);
			searchResultsCurrentPage = page;
		} catch (err) {
			errorMessage(errorMessages['universal_search']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		headerTitle.set(messages['universal.search']['header.title']);
		await getSearchResults();
	});
</script>

<svelte:head>
	<title>{messages['universal.search']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Search" />
</svelte:head>

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

		{#if groupDetails}
			<GroupDetails group={selectedGroup} on:update-search={async () => await getSearchResults()} />
		{/if}

		{#if topicDetails}
			<TopicDetails selectedTopicId={selectedTopic.id} {isTopicAdmin} />
		{/if}

		{#if applicationDetails}
			<ApplicationDetails
				{selectedAppId}
				{selectedAppGroupId}
				{selectedAppGroupName}
				{selectedAppName}
				{selectedAppPublic}
				{isApplicationAdmin}
				{appCurrentGroupPublic}
				{selectedAppDescription}
			/>
		{/if}

		{#if $universalSearchList}
			<div class="content">
				<h1>{messages['universal.search']['title']}</h1>

				<form class="searchbox">
					<input
						data-cy="search-applications-table"
						class="searchbox"
						type="search"
						style="width: 25rem"
						placeholder={messages['universal.search']['search.placeholder']}
						bind:value={universalSearchString}
						on:blur={() => {
							universalSearchString = universalSearchString?.trim();
						}}
						on:keydown={(event) => {
							if (event.which === returnKey) {
								document.activeElement.blur();
								universalSearchString = universalSearchString?.trim();
							}
						}}
					/>
				</form>

				<div class="controls">
					<button
						class="button-universal-search"
						class:button-universal-search-selected={selectedUniversalSearchButton.find(
							(selection) => selection === UniversalSearchOption.EVERYTHING
						)}
						style="border-radius: 25px 0 0 25px; padding: 0 2rem 0 1.2rem"
						on:click={() => handleClick('EVERYTHING')}
					>
						<span
							class:invisible={selectedUniversalSearchButton.find(
								(selection) => selection !== UniversalSearchOption.EVERYTHING
							)}
							style="margin-right: 0.5rem"
						>
							✓
						</span>
						{messages['universal.search']['filter.everything']}
					</button>

					<button
						class="button-universal-search"
						class:button-universal-search-selected={selectedUniversalSearchButton.find(
							(selection) => selection === UniversalSearchOption.GROUPS
						)}
						style="padding: 0 2.5rem 0 1rem"
						on:click={() => handleClick('GROUPS')}
					>
						<span
							class:invisible={selectedUniversalSearchButton.filter(
								(selection) => selection === UniversalSearchOption.GROUPS
							).length === 0}
							style="margin-right: 0.5rem"
						>
							✓
						</span>
						{messages['universal.search']['filter.groups']}
					</button>

					<button
						class="button-universal-search"
						class:button-universal-search-selected={selectedUniversalSearchButton.find(
							(selection) => selection === UniversalSearchOption.TOPICS
						)}
						style="padding: 0 2.5rem 0 1rem"
						on:click={() => handleClick('TOPICS')}
					>
						<span
							class:invisible={selectedUniversalSearchButton.filter(
								(selection) => selection === UniversalSearchOption.TOPICS
							).length === 0}
							style="margin-right: 0.5rem"
						>
							✓
						</span>
						{messages['universal.search']['filter.topics']}
					</button>

					<button
						class="button-universal-search"
						class:button-universal-search-selected={selectedUniversalSearchButton.find(
							(selection) => selection === UniversalSearchOption.APPLICATIONS
						)}
						style="border-radius: 0 25px 25px 0; padding: 0 2rem 0 1.2rem"
						on:click={() => handleClick('APPLICATIONS')}
					>
						<span
							class:invisible={selectedUniversalSearchButton.filter(
								(selection) => selection === UniversalSearchOption.APPLICATIONS
							).length === 0}
							style="margin-right: 0.5rem"
						>
							✓
						</span>
						{messages['universal.search']['filter.applications']}
					</button>
				</div>

				<table class="universal-search-table">
					<thead>
						<tr
							><td class="header-column" style="min-width: 5rem"
								>{messages['universal.search']['table.search.results.column.one']}</td
							>
							<td class="header-column" style="min-width: 5rem"
								>{messages['universal.search']['table.search.results.column.two']}</td
							>
							<td class="header-column" style="min-width: 5rem"
								>{messages['universal.search']['table.search.results.column.three']}</td
							>
						</tr>
					</thead>

					<tbody>
						{#if $searchResults}
							{#each $searchResults as result}
								<tr on:click={handleItemSelection(result)} style="cursor: pointer">
									<td style="white-space: nowrap">
										{#if result.type === 'GROUP'}
											<img
												src={groupsSVG}
												alt="group"
												width="21rem"
												height="21rem"
												style="vertical-align: middle"
											/>
										{:else if result.type === 'TOPIC'}
											<img
												src={topicsSVG}
												alt="topic"
												width="21rem"
												height="21rem"
												style="vertical-align: middle"
											/>
										{:else if result.type === 'APPLICATION'}
											<img
												src={applicationsSVG}
												alt="application"
												width="21rem"
												height="21rem"
												style="vertical-align: middle"
											/>
										{/if}
										<span style="vertical-align: middle; margin-left: 0.5rem"
											>{result.entity.name}</span
										>
									</td>

									<td style="white-space: nowrap">
										{#if result.type === 'GROUP'}
											<span style="vertical-align: middle">{result.entity.name}</span>
										{:else}
											<span style="vertical-align: middle">{result.entity.groupName}</span>
										{/if}
									</td>

									<td>
										<span style="vertical-align: middle">
											{#if result.entity.description}
												{result.entity.description}
											{:else}
												-
											{/if}
										</span>
									</td>
								</tr>
							{/each}
						{:else}
							<div style="margin-top: 0.5rem; margin-left: 0.3rem">
								{messages['universal.search']['empty.results']}
							</div>
						{/if}
					</tbody>
				</table>
			</div>

			<div class="pagination">
				<span>{messages['pagination']['rows.per.page']}</span>
				<select
					tabindex="-1"
					on:change={(e) => {
						searchResultsPerPage = e.target.value;
						getSearchResults();
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
					{#if $searchResultsTotalSize > 0}
						{1 + searchResultsCurrentPage * searchResultsPerPage}
					{:else}
						0
					{/if}
					- {Math.min(
						searchResultsPerPage * (searchResultsCurrentPage + 1),
						$searchResultsTotalSize
					)}
					of
					{$searchResultsTotalSize}
				</span>

				<!-- svelte-ignore a11y-click-events-have-key-events -->
				<img
					src={pagefirstSVG}
					alt="first page"
					class="pagination-image"
					class:disabled-img={searchResultsCurrentPage === 0}
					on:click={() => {
						if (searchResultsCurrentPage > 0) {
							searchResultsCurrentPage = 0;
							getSearchResults();
						}
					}}
				/>
				<!-- svelte-ignore a11y-click-events-have-key-events -->
				<img
					src={pagebackwardsSVG}
					alt="previous page"
					class="pagination-image"
					class:disabled-img={searchResultsCurrentPage === 0}
					on:click={() => {
						if (searchResultsCurrentPage > 0) {
							searchResultsCurrentPage--;
							getSearchResults(searchResultsCurrentPage);
						}
					}}
				/>
				<!-- svelte-ignore a11y-click-events-have-key-events -->
				<img
					src={pageforwardSVG}
					alt="next page"
					class="pagination-image"
					class:disabled-img={searchResultsCurrentPage + 1 === $searchResultsTotalPages}
					on:click={() => {
						if (searchResultsCurrentPage + 1 < $searchResultsTotalPages) {
							searchResultsCurrentPage++;
							getSearchResults(searchResultsCurrentPage);
						}
					}}
				/>
				<!-- svelte-ignore a11y-click-events-have-key-events -->
				<img
					src={pagelastSVG}
					alt="last page"
					class="pagination-image"
					class:disabled-img={searchResultsCurrentPage + 1 === $searchResultsTotalPages}
					on:click={() => {
						if (searchResultsCurrentPage < $searchResultsTotalPages) {
							searchResultsCurrentPage = $searchResultsTotalPages - 1;
							getSearchResults(searchResultsCurrentPage);
						}
					}}
				/>
			</div>
			<p style="margin-top: 8rem">{messages['footer']['message']}</p>
		{/if}
	{/await}
{/if}

<style>
	.content {
		width: 100%;
		min-width: 43.5rem;
		margin-right: 1rem;
	}

	.controls {
		margin-top: 2rem;
		width: fit-content;
	}

	.universal-search-table {
		margin-top: 2rem;
		min-width: 43.5rem;
	}

	tr {
		height: 3rem;
	}

	.header-column {
		font-weight: 600;
	}
</style>

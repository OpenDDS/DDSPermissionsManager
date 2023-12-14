<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import {
		grantDetailsStore,
		grantPublishActionsStore,
		grantSubscribeActionsStore
	} from './../../stores/grantDetails.js';
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import editSVG from '../../icons/edit.svg';
	import { httpAdapter } from '../../appconfig';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import deleteSVG from '../../icons/delete.svg';
	import detailSVG from '../../icons/detail.svg';
	import Modal from '../../lib/Modal.svelte';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import { convertFromMilliseconds } from '../../utils';
	import addSVG from '../../icons/add.svg';
	import GrantModal from './GrantModal.svelte';
	import ActionsModal from './ActionsModal.svelte';
	import AdminDetails from '../../lib/AdminDetails.svelte';

	export let selectedGrant, isTopicAdmin;

	let selectedTopicApplications = [],
		selectedAction = {};
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

	// Modals
	let errorMessageVisible = false;

	// Constants
	const returnKey = 13,
		waitTime = 1000;

	// Error Handling
	let errorMsg, errorObject;

	// Editing
	let publishActionsRowsSelectedTrue = false;
	let publishActionsAllRowsSelectedTrue = false;
	let publishActionsRowsSelected = [];

	let subscribeActionsRowsSelectedTrue = false;
	let subscribeActionsAllRowsSelectedTrue = false;
	let subscribeActionsRowsSelected = [];

	let deletePublishVisible = false;
	let deleteSubscribeVisible = false;

	let addActionVisible = false;
	let editActionVisible = false;
	let editGrantVisible = false;
	let isPublishAction = false;

	const fetchAndUpdateGrant = async () => {
		try {
			const response = await httpAdapter.get(`/application_grants/${selectedGrant.id}`);
			grantDetailsStore.set(response.data);
			selectedGrant = $grantDetailsStore;
			await loadApplicationPermissions(selectedGrant.groupId);

			headerTitle.set(selectedGrant.name);
			detailView.set(true);
		} catch (error) {
			errorMessage(errorMessages['grant-details']['loading.detail.error.title'], err.message);
		}
	};

	const fetchAndUpdatePublishAction = async () => {
		try {
			const response = await httpAdapter.get(`/actions?grantId=${selectedGrant.id}&pubsub=PUBLISH`);
			grantPublishActionsStore.set(response.data.content || []);
		} catch (error) {
			errorMessage(errorMessages['grant-details']['loading.detail.error.title'], err.message);
		}
	};

	const fetchAndUpdateSubscribeAction = async () => {
		try {
			const response = await httpAdapter.get(
				`/actions?grantId=${selectedGrant.id}&pubsub=SUBSCRIBE`
			);
			grantSubscribeActionsStore.set(response.data.content || []);
		} catch (error) {
			errorMessage(errorMessages['grant-details']['loading.detail.error.title'], err.message);
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

	onMount(async () => {
		await fetchAndUpdateGrant();
		await fetchAndUpdatePublishAction();
		await fetchAndUpdateSubscribeAction();
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

	const saveNewAction = async (newAction) => {
		try {
			const res = await httpAdapter.post(`/actions`, newAction);
			await fetchAndUpdatePublishAction();
			await fetchAndUpdateSubscribeAction();
		} catch (err) {
			errorMessage(errorMessages['grant-details']['updating.error.title'], err.message);
		}
	};

	const updateAction = async (updatedAction) => {
		try {
			const res = await httpAdapter.put(`/actions/${updatedAction.id}`, updatedAction);
			await fetchAndUpdatePublishAction();
			await fetchAndUpdateSubscribeAction();
		} catch (err) {
			errorMessage(errorMessages['grant-details']['adding.error.title'], err.message);
		}
	};

	const updateGrant = async (editedGrant) => {
		const res = await httpAdapter
			.put(`/application_grants/${selectedGrant.id}`, editedGrant)
			.catch((err) => {
				editGrantVisible = false;
				errorMessage(errorMessages['grant-details']['updating.error.title'], err.message);
			});

		editGrantVisible = false;

		if (res === undefined) {
			errorMessage(errorMessages['grant-details']['updating.error.title'], err.message);
		} else {
			fetchAndUpdateGrant();
		}
	};

	const deselectAllPublishCheckboxes = () => {
		publishActionsAllRowsSelectedTrue = false;
		publishActionsRowsSelectedTrue = false;
		publishActionsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.publish-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const deselectAllSubscribeCheckboxes = () => {
		subscribeActionsAllRowsSelectedTrue = false;
		subscribeActionsRowsSelectedTrue = false;
		subscribeActionsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.subscribe-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = (checkboxClass) => {
		let checkboxes = document.querySelectorAll(checkboxClass);
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const deleteSelectedPublishActions = async () => {
		try {
			for (const action of publishActionsRowsSelected) {
				const actionId = action.id;
				await httpAdapter.delete(`/actions/${actionId}`);
			}
			await fetchAndUpdatePublishAction();
		} catch (err) {
			errorMessage(errorMessages['grant-details']['deleting.actions.error.title'], err.message);
		}
	};

	const deleteSelectedSubscribeActions = async () => {
		try {
			for (const action of subscribeActionsRowsSelected) {
				const actionId = action.id;
				await httpAdapter.delete(`/actions/${actionId}`);
			}
			await fetchAndUpdateSubscribeAction();
		} catch (err) {
			errorMessage(errorMessages['grant-details']['deleting.actions.error.title'], err.message);
		}
	};

	// Todo: uncomment when we have the dateUpdated property in the API response
	// $: timeAgo = moment($grantDetailsStore.dateUpdated).fromNow();
	// $: browserFormat = new Date($grantDetailsStore.dateUpdated).toLocaleString();
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
	{#if editGrantVisible}
		<GrantModal
			title={messages['grants']['edit']}
			actionEditGrant={true}
			on:cancel={() => (editGrantVisible = false)}
			{selectedGrant}
			on:editGrant={(e) => {
				updateGrant(e.detail);
			}}
		/>
	{/if}

	{#if addActionVisible}
		<ActionsModal
			title={isPublishAction ? 'Add Publish Actions' : 'Add Subscribe Actions'}
			actionAddAction={true}
			on:cancel={() => (addActionVisible = false)}
			{selectedGrant}
			on:addAction={(e) => {
				saveNewAction(e.detail);
			}}
			{isPublishAction}
		/>
	{/if}

	{#if editActionVisible}
		<ActionsModal
			title={isPublishAction ? 'Edit Publish Actions' : 'Edit Subscribe Actions'}
			actionEditAction={true}
			on:cancel={() => (editActionVisible = false)}
			{selectedGrant}
			{selectedAction}
			on:editAction={(e) => {
				updateAction(e.detail);
			}}
			{isPublishAction}
		/>
	{/if}

	<div class="content">
		<div style="display: inline-flex; align-items: baseline">
			<table class="grant-details">
				<tr>
					<td>{messages['grants.detail']['row.one']}</td>
					<td>{selectedGrant.name}</td>
				</tr>

				<tr>
					<td>{messages['grants.detail']['row.two']}</td>
					<td>{selectedGrant.groupName}</td>
				</tr>

				<tr>
					<td>Application Name:</td>
					<td>{selectedGrant.applicationName}</td>
				</tr>

				<tr>
					<td>Application Group:</td>
					<td>{selectedGrant.applicationGroupName}</td>
				</tr>

				<tr>
					<td>Duration:</td>
					<td>{getDuration(selectedGrant)}</td>
				</tr>

				<tr>
					<td>Admins:</td>
					<td>
						{#if selectedGrant.groupId}
							<AdminDetails
								groupId={selectedGrant.groupId}
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
					on:click={() => (editGrantVisible = true)}
				/>
			{/if}
		</div>

		<!-- {#if $grantDetailsStore.dateUpdated}
			<p style="font-weight: 200; margin-bottom: 2rem;">Last updated {timeAgo} ({browserFormat})</p>
		{/if} -->

		<div class="pub-sub-wrapper">
			<div>
				<div style="display: flex; justify-content: space-between; min-width: 43rem">
					<h1>Publish Actions</h1>
					{#if $isAdmin || isTopicAdmin}
						<div style="padding-top: 1.5rem">
							<img
								src={deleteSVG}
								alt="options"
								class="dot"
								class:button-disabled={(!$isAdmin && !isTopicAdmin) ||
									publishActionsRowsSelected.length === 0}
								style="margin-left: 0.5rem;"
								on:click={() => {
									if (publishActionsRowsSelected.length > 0) deletePublishVisible = true;
								}}
								on:keydown={(event) => {
									if (event.which === returnKey) {
										if (publishActionsRowsSelected.length > 0) deletePublishVisible = true;
									}
								}}
							/>
							<img
								data-cy="add-duration"
								src={addSVG}
								alt="options"
								class="dot"
								on:click={() => {
									isPublishAction = true;
									addActionVisible = true;
								}}
								on:keydown={(event) => {
									if (event.which === returnKey) {
										isPublishAction = true;
										addActionVisible = true;
									}
								}}
							/>
						</div>
					{/if}
				</div>
				{#if $grantPublishActionsStore.length}
					<table data-cy="topics-table" class="main">
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								{#if isTopicAdmin || $isAdmin}
									<td style="line-height: 1rem;">
										<input
											tabindex="-1"
											type="checkbox"
											class="publish-checkbox"
											style="margin-right: 0.5rem"
											bind:indeterminate={publishActionsRowsSelectedTrue}
											on:click={(e) => {
												if (e.target.checked) {
													publishActionsRowsSelected = $grantPublishActionsStore;
													publishActionsRowsSelectedTrue = false;
													publishActionsAllRowsSelectedTrue = true;
												} else {
													publishActionsAllRowsSelectedTrue = false;
													publishActionsRowsSelectedTrue = false;
													publishActionsRowsSelected = [];
												}
											}}
											checked={publishActionsAllRowsSelectedTrue}
										/>
									</td>
								{/if}
								<td class="header-column" style="min-width: 7rem">Actions</td>
							</tr>
						</thead>
						<tbody>
							{#each $grantPublishActionsStore as publish}
								<tr>
									{#if isTopicAdmin || $isAdmin}
										<td style="line-height: 1rem; width: 2rem; ">
											<input
												tabindex="-1"
												type="checkbox"
												class="publish-checkbox"
												checked={publishActionsAllRowsSelectedTrue}
												on:change={(e) => {
													if (e.target.checked === true) {
														publishActionsRowsSelected.push(publish);
														// reactive statement
														publishActionsRowsSelected = publishActionsRowsSelected;
														publishActionsRowsSelectedTrue = true;
													} else {
														publishActionsRowsSelected = publishActionsRowsSelected.filter(
															(selection) => selection !== publish
														);
														if (publishActionsRowsSelected.length === 0) {
															publishActionsRowsSelectedTrue = false;
														}
													}
												}}
											/>
										</td>
									{/if}

									<td data-cy="topic" style="width: max-content">
										<span style="display: inline-block; min-width: 15rem">
											{#if publish.topicSets?.length}
												<span style="font-weight: 700; margin-right: 0.5rem"
													>Topic Set:
												</span>{publish.topicSets[0].name}{publish.topicSets.length > 1
													? '...'
													: ''}
												<!-- content here -->
											{:else if publish.topics?.length}
												<!-- else content here -->
												<span style="font-weight: 700;  margin-right: 0.5rem">Topic: </span>{publish
													.topics[0].name}{publish.topics?.length > 1 ? '...' : ''}
											{:else}
												<span style="font-weight: 700; margin-right: 0.5rem">Topic Set: </span>No
												Topics
											{/if}
										</span>
										<span>
											<span style="font-weight: 700; margin-left: 2rem;  margin-right: 0.5rem"
												>Partition:
											</span>

											{#if publish.partitions?.length}
												{publish.partitions[0]}{publish.partitions.length > 1 ? '...' : ''}
											{:else}
												No Partitions
											{/if}
										</span>
									</td>

									{#if $isAdmin || isTopicAdmin}
										<td style="cursor: pointer; width:1rem">
											<!-- svelte-ignore a11y-click-events-have-key-events -->
											<img
												data-cy="detail-application-icon"
												src={detailSVG}
												height="18rem"
												width="18rem"
												alt="edit user"
												style="vertical-align: -0.15rem"
												on:click={() => {
													selectedAction = publish;
													isPublishAction = true;
													editActionVisible = true;
												}}
												on:keydown={(event) => {
													if (event.which === returnKey) {
														selectedAction = publish;
														isPublishAction = true;
														editActionVisible = true;
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
													if (!publishActionsRowsSelected.some((tpc) => tpc === publish))
														publishActionsRowsSelected.push(publish);
													deletePublishVisible = true;
												}}
											/>
										</td>
									{/if}
								</tr>
							{/each}
						</tbody>
					</table>
				{:else}
					<div class="no-actions">
						<p>{messages['grants.detail']['no.topics.found']}</p>
					</div>
				{/if}
			</div>
			<div>
				<div style="display: flex; justify-content: space-between; min-width: 43rem">
					<h1>Subscribe Actions</h1>
					{#if $isAdmin || isTopicAdmin}
						<div style="padding-top: 1.5rem">
							<img
								src={deleteSVG}
								alt="options"
								class="dot"
								class:button-disabled={(!$isAdmin && !isTopicAdmin) ||
									subscribeActionsRowsSelected.length === 0}
								style="margin-left: 0.5rem;"
								on:click={() => {
									if (subscribeActionsRowsSelected.length > 0) deleteSubscribeVisible = true;
								}}
								on:keydown={(event) => {
									if (event.which === returnKey) {
										if (subscribeActionsRowsSelected.length > 0) deleteSubscribeVisible = true;
									}
								}}
							/>
							<img
								data-cy="add-duration"
								src={addSVG}
								alt="options"
								class="dot"
								on:click={() => {
									isPublishAction = false;
									addActionVisible = true;
								}}
								on:keydown={(event) => {
									if (event.which === returnKey) {
										isPublishAction = false;
										addActionVisible = true;
									}
								}}
							/>
						</div>
					{/if}
				</div>
				{#if $grantSubscribeActionsStore.length}
					<table data-cy="topics-table" class="main">
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								{#if isTopicAdmin || $isAdmin}
									<td style="line-height: 1rem;">
										<input
											tabindex="-1"
											type="checkbox"
											class="subscribe-checkbox"
											style="margin-right: 0.5rem"
											bind:indeterminate={subscribeActionsRowsSelectedTrue}
											on:click={(e) => {
												if (e.target.checked) {
													subscribeActionsRowsSelected = $grantSubscribeActionsStore;
													subscribeActionsRowsSelectedTrue = false;
													subscribeActionsAllRowsSelectedTrue = true;
												} else {
													subscribeActionsAllRowsSelectedTrue = false;
													subscribeActionsRowsSelectedTrue = false;
													subscribeActionsRowsSelected = [];
												}
											}}
											checked={subscribeActionsAllRowsSelectedTrue}
										/>
									</td>
								{/if}
								<td class="header-column" style="min-width: 7rem">Actions</td>
							</tr>
						</thead>
						<tbody>
							{#each $grantSubscribeActionsStore as subscribe}
								<tr>
									{#if isTopicAdmin || $isAdmin}
										<td style="line-height: 1rem; width: 2rem; ">
											<input
												tabindex="-1"
												type="checkbox"
												class="subscribe-checkbox"
												checked={subscribeActionsAllRowsSelectedTrue}
												on:change={(e) => {
													if (e.target.checked === true) {
														subscribeActionsRowsSelected.push(subscribe);
														// reactive statement
														subscribeActionsRowsSelected = subscribeActionsRowsSelected;
														subscribeActionsRowsSelectedTrue = true;
													} else {
														subscribeActionsRowsSelected = subscribeActionsRowsSelected.filter(
															(selection) => selection !== subscribe
														);
														if (subscribeActionsRowsSelected.length === 0) {
															subscribeActionsRowsSelectedTrue = false;
														}
													}
												}}
											/>
										</td>
									{/if}

									<td data-cy="topic" style="width: max-content">
										<span style="display: inline-block; min-width: 15rem">
											{#if subscribe.topicSets?.length}
												<span style="font-weight: 700; margin-right: 0.5rem"
													>Topic Set:
												</span>{subscribe.topicSets[0].name}{subscribe.topicSets.length > 1
													? '...'
													: ''}
												<!-- content here -->
											{:else if subscribe.topics?.length}
												<!-- else content here -->
												<span style="font-weight: 700;  margin-right: 0.5rem"
													>Topic:
												</span>{subscribe.topics[0].name}{subscribe.topics?.length > 1 ? '...' : ''}
											{:else}
												<span style="font-weight: 700; margin-right: 0.5rem">Topic Set: </span>No
												Topics
											{/if}
										</span>
										<span>
											<span style="font-weight: 700; margin-left: 2rem;  margin-right: 0.5rem"
												>Partition:
											</span>

											{#if subscribe.partitions?.length}
												{subscribe.partitions[0]}{subscribe.partitions.length > 1 ? '...' : ''}
											{:else}
												No Partitions
											{/if}
										</span>
									</td>

									{#if $isAdmin || isTopicAdmin}
										<td style="cursor: pointer; width:1rem">
											<!-- svelte-ignore a11y-click-events-have-key-events -->
											<img
												data-cy="detail-application-icon"
												src={detailSVG}
												height="18rem"
												width="18rem"
												alt="edit user"
												style="vertical-align: -0.15rem"
												on:click={() => {
													selectedAction = subscribe;
													isPublishAction = false;
													editActionVisible = true;
												}}
												on:keydown={(event) => {
													if (event.which === returnKey) {
														selectedAction = subscribe;
														isPublishAction = false;
														editActionVisible = true;
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
													if (!subscribeActionsRowsSelected.some((tpc) => tpc === subscribe))
														subscribeActionsRowsSelected.push(subscribe);
													deleteSubscribeVisible = true;
												}}
											/>
										</td>
									{/if}
								</tr>
							{/each}
						</tbody>
					</table>
				{:else}
					<div class="no-actions">
						<p>{messages['grants.detail']['no.topics.found']}</p>
					</div>
				{/if}
			</div>
		</div>
	</div>
	<p style="margin-top: 8rem">{messages['footer']['message']}</p>
{/if}

{#if deletePublishVisible}
	<Modal
		actionDeleteTopicsFromTopicSet={true}
		title="{messages['grants.detail']['delete.title']} {publishActionsRowsSelected.length > 1
			? messages['grants.detail']['delete.multiple']
			: messages['grants.detail']['delete.single']}"
		on:cancel={() => {
			if (
				publishActionsRowsSelected?.length === 1 &&
				numberOfSelectedCheckboxes('.publish-checkbox') === 0
			)
				publishActionsRowsSelected = [];
			deletePublishVisible = false;
		}}
		on:deleteTopics={async () => {
			await deleteSelectedPublishActions();
			await fetchAndUpdateGrant();
			deselectAllPublishCheckboxes();
			deletePublishVisible = false;
		}}
	/>
{/if}

{#if deleteSubscribeVisible}
	<Modal
		actionDeleteTopicsFromTopicSet={true}
		title="{messages['grants.detail']['delete.title']} {subscribeActionsRowsSelected.length > 1
			? messages['grants.detail']['delete.multiple']
			: messages['grants.detail']['delete.single']}"
		on:cancel={() => {
			if (
				subscribeActionsRowsSelected?.length === 1 &&
				numberOfSelectedCheckboxes('.subscribe-checkbox') === 0
			)
				subscribeActionsRowsSelected = [];
			deleteSubscribeVisible = false;
		}}
		on:deleteTopics={async () => {
			await deleteSelectedSubscribeActions();
			await fetchAndUpdateGrant();
			deselectAllSubscribeCheckboxes();
			deleteSubscribeVisible = false;
		}}
	/>
{/if}

<style>
	.pub-sub-wrapper {
		display: flex;
		align-items: center;
		flex-direction: column;
		margin-bottom: 1rem;
		gap: 2rem;
	}
	.grant-details {
		font-size: 12pt;
		width: 40rem;
		margin: 1.6rem 0;
	}

	.grant-details td {
		height: 2.2rem;
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

	.no-actions {
		margin-top: 4rem;
		margin-left: 1rem;
	}

	.header-column {
		font-weight: 600;
	}
</style>

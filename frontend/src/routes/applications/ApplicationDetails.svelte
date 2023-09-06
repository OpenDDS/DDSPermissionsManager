<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { createEventDispatcher, onDestroy, onMount } from 'svelte';
	import moment from 'moment';
	import { page } from '$app/stores';
	import { isAdmin } from '../../stores/authentication';
	import { httpAdapter } from '../../appconfig';
	import Modal from '../../lib/Modal.svelte';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import headerTitle from '../../stores/headerTitle';
	import messages from '$lib/messages.json';
	import errorMessages from '$lib/errorMessages.json';
	import editSVG from '../../icons/edit.svg';
	import deleteSVG from '../../icons/delete.svg';

	export let isApplicationAdmin,
		selectedAppId,
		selectedAppGroupId,
		selectedAppName,
		selectedAppDescription = '',
		selectedAppPublic,
		appCurrentGroupPublic,
		selectedAppGroupName,
		selectedAppDateUpdated = '',
		selectedTopicApplications = [];

	const dispatch = createEventDispatcher();

	// Error Handling
	let errorMsg,
		errorObject,
		errorMessageVisible = false;

	// Constants
	const returnKey = 13;

	// Messages
	let deleteToolip,
		deleteMouseEnter = false;

	// Selection
	let grantsRowsSelected = [],
		grantsRowsSelectedTrue = false,
		grantsAllRowsSelectedTrue = false;

	// Modals
	let reloadMessageVisible = false;
	// checkboxes selection
	$: if (selectedTopicApplications?.length === grantsRowsSelected?.length) {
		grantsRowsSelectedTrue = false;
		grantsAllRowsSelectedTrue = true;
	} else if (grantsRowsSelected?.length > 0) {
		grantsRowsSelectedTrue = true;
	} else {
		grantsAllRowsSelectedTrue = false;
	}

	let selectedAppDescriptionSelector,
		checkboxSelector,
		isPublic = selectedAppPublic;

	let editApplicationVisible = false,
		deleteSelectedGrantsVisible = false;

	// Websocket
	let applicationSocketIsPaused = false;

	const decodeError = (errorObject) => {
		errorObject = errorObject.code.replaceAll('-', '_');
		const cat = errorObject.substring(0, errorObject.indexOf('.'));
		const code = errorObject.substring(errorObject.indexOf('.') + 1, errorObject.length);
		return { category: cat, code: code };
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

	const getAppPermissions = async () => {
		const appPermissionData = await httpAdapter.get(
			`/application_permissions/application/${selectedAppId}`
		);
		selectedTopicApplications = appPermissionData.data.content;
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

	const saveNewApp = async (newAppName, newAppDescription, newAppPublic) => {
		pauseSocketListener();
		const res = await httpAdapter
			.post(`/applications/save/`, {
				id: selectedAppId,
				name: newAppName,
				group: selectedAppGroupId,
				description: newAppDescription,
				public: newAppPublic
			})
			.catch((err) => {
				if (err.response.data && err.response.status === 400) {
					const decodedError = decodeError(Object.create(...err.response.data));
					errorMessage(
						errorMessages['application']['saving.error.title'],
						errorMessages[decodedError.category][decodedError.code]
					);
				}
			});
		dispatch('reloadAllApps');
		dispatch('loadApplicationDetail');
		resumeSocketListener();
	};

	const deleteTopicApplicationAssociation = async () => {
		for (const grant of grantsRowsSelected) {
			await httpAdapter.delete(`/application_permissions/${grant.id}`);
		}
	};

	const deselectAllGrantsCheckboxes = () => {
		grantsAllRowsSelectedTrue = false;
		grantsRowsSelectedTrue = false;
		grantsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.grants-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const loadApplicationDetail = async (appId, groupId) => {
		const appDetail = await httpAdapter.get(`/applications/show/${appId}`);

		selectedAppId = appId;
		selectedAppGroupId = groupId;
		selectedAppName = appDetail.data.name;
		selectedAppGroupName = appDetail.data.groupName;
		selectedAppDescription = appDetail.data.description;
		selectedAppPublic = appDetail.data.public;
		selectedAppDateUpdated = appDetail.data.dateUpdated;
		isPublic = selectedAppPublic;
	};

	const websocketURL = import.meta.env.VITE_WEBSOCKET_URL;
	const socket = new WebSocket(`${websocketURL}/applications/${selectedAppId}`);

	const pauseSocketListener = () => {
		applicationSocketIsPaused = true;
	};

	const resumeSocketListener = () => {
		applicationSocketIsPaused = false;
	};

	const subscribeApplicationMessage = (applicationSocket) => {
		applicationSocket.addEventListener('message', (event) => {
			if (!applicationSocketIsPaused) {
				if (
					event.data.includes('application_updated') ||
					event.data.includes('application_deleted')
				) {
					reloadMessageVisible = true;
				}
			}
		});
	};

	onMount(async () => {
		subscribeApplicationMessage(socket);
		headerTitle.set(selectedAppName);
		await getAppPermissions();
		if (appCurrentGroupPublic === undefined) {
			appCurrentGroupPublic = await getGroupVisibilityPublic(selectedAppGroupName);
		}
	});

	$: timeAgo = moment(selectedAppDateUpdated).fromNow();
	$: browserFormat = new Date(selectedAppDateUpdated).toLocaleString();

	onDestroy(() => {
		socket.close();
	});
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

{#if editApplicationVisible}
	<Modal
		title={messages['application']['edit']}
		actionEditApplication={true}
		appCurrentName={selectedAppName}
		appCurrentDescription={selectedAppDescription}
		appCurrentPublic={isPublic}
		{appCurrentGroupPublic}
		on:cancel={() => (editApplicationVisible = false)}
		on:saveNewApp={(e) => {
			saveNewApp(e.detail.newAppName, e.detail.newAppDescription, e.detail.newAppPublic);
			headerTitle.set(e.detail.newAppName);
			editApplicationVisible = false;
			selectedAppName = e.detail.newAppName;
			selectedAppDescription = e.detail.newAppDescription;
			isPublic = e.detail.newAppPublic;
		}}
	/>
{/if}

{#if deleteSelectedGrantsVisible}
	<Modal
		actionDeleteGrants={true}
		title="{messages['application.detail']['delete.grants.title']} {grantsRowsSelected.length > 1
			? messages['application.detail']['delete.grants.multiple']
			: messages['application.detail']['delete.grants.single']}"
		on:cancel={() => {
			deleteSelectedGrantsVisible = false;
		}}
		on:deleteGrants={async () => {
			await deleteTopicApplicationAssociation();
			await getAppPermissions();

			deselectAllGrantsCheckboxes();
			deleteSelectedGrantsVisible = false;
		}}
	/>
{/if}

<div class="content">
	<table>
		<tr>
			<td style="font-weight: 300; width: 11.5rem">
				{messages['application.detail']['row.one']}
			</td>

			<td style="font-weight: 500">{selectedAppName} </td>
			{#if $isAdmin || $permissionsByGroup.find((permission) => permission.groupId === selectedAppGroupId && permission.isApplicationAdmin)}
				<!-- svelte-ignore a11y-click-events-have-key-events -->
				<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
				<img
					data-cy="edit-application-icon"
					src={editSVG}
					tabindex="0"
					alt="edit application"
					width="18rem"
					style="margin-left: 1rem; cursor: pointer"
					on:click={async () => {
						editApplicationVisible = true;
					}}
				/>
			{/if}
		</tr>
		<tr>
			<td style="font-weight: 300; margin-right: 1rem; width: 6.2rem;">
				{messages['application.detail']['row.two']}
			</td>

			<td style="font-weight: 400; margin-left: 2rem" bind:this={selectedAppDescriptionSelector}
				>{selectedAppDescription ? selectedAppDescription : '-'}</td
			>
		</tr>
		<tr>
			<td style="font-weight: 300">
				{messages['application.detail']['row.three']}
			</td>
			<td>
				<input
					type="checkbox"
					tabindex="-1"
					style="width: 15px; height: 15px"
					bind:checked={isPublic}
					on:change={() => (isPublic = selectedAppPublic)}
					bind:this={checkboxSelector}
				/>
			</td>
		</tr>
	</table>

	{#if selectedAppDateUpdated} <p style="font-weight: 200;">Last updated {timeAgo} ({browserFormat})</p> {/if}

	{#if !$page.url.pathname.includes('search')}
		<div style="margin-top: 3.5rem">
			<div
				style="display: flex; justify-content: space-between; align-items:center; margin-top: 2rem"
			>
				<div style="font-size:1.3rem; margin-bottom: 1rem">
					{messages['application.detail']['table.applications.label']}
				</div>

				<div>
					<img
						src={deleteSVG}
						alt="options"
						class="dot"
						class:button-disabled={(!$isAdmin && !isApplicationAdmin) ||
							grantsRowsSelected?.length === 0}
						style="margin-left: 0.5rem; margin-right: 1rem"
						on:click={() => {
							if (grantsRowsSelected.length > 0) deleteSelectedGrantsVisible = true;
						}}
						on:keydown={(event) => {
							if (event.which === returnKey) {
								if (grantsRowsSelected.length > 0) deleteSelectedGrantsVisible = true;
							}
						}}
						on:mouseenter={() => {
							deleteMouseEnter = true;
							if ($isAdmin || isApplicationAdmin) {
								if (grantsRowsSelected.length === 0) {
									deleteToolip = messages['topic.detail']['delete.tooltip'];
									const tooltip = document.querySelector('#delete-topics');
									setTimeout(() => {
										if (deleteMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, 1000);
								}
							} else {
								deleteToolip = messages['topic']['delete.tooltip.topic.admin.required'];
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
							if (grantsRowsSelected.length === 0) {
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
					<span id="delete-topics" class="tooltip-hidden" style="margin-top: -1.8rem"
						>{deleteToolip}
					</span>
				</div>
			</div>
		</div>

		<table style="min-width: 59rem; max-width: 59rem">
			<thead>
				<tr style="border-top: 1px solid black; border-bottom: 2px solid">
					<td>
						<input
							tabindex="-1"
							type="checkbox"
							class="grants-checkbox"
							style="margin-right: 0.5rem"
							bind:indeterminate={grantsRowsSelectedTrue}
							on:click={(e) => {
								if (e.target.checked) {
									grantsRowsSelected = selectedTopicApplications;
									grantsRowsSelectedTrue = false;
									grantsAllRowsSelectedTrue = true;
								} else {
									grantsAllRowsSelectedTrue = false;
									grantsRowsSelectedTrue = false;
									grantsRowsSelected = [];
								}
							}}
							checked={grantsAllRowsSelectedTrue}
						/>
					</td>
					<td>{messages['application.detail']['table.applications.column.one']}</td>
					<td>{messages['application.detail']['table.applications.column.two']}</td>
					<td>{messages['application.detail']['table.applications.column.three']}</td>
					{#if !$page.url.pathname.includes('search')}
						<td>{messages['application.detail']['table.applications.column.four']}</td>
						<td>{messages['application.detail']['table.applications.column.five']}</td>
					{/if}

					{#if isApplicationAdmin || $isAdmin}
						<td />
					{/if}
				</tr>
			</thead>
			{#if selectedTopicApplications}
				{#each selectedTopicApplications as appPermission}
					<tbody>
						<tr>
							<td style="line-height: 1rem;">
								<input
									tabindex="-1"
									type="checkbox"
									class="grants-checkbox"
									style="margin-right: 0.5rem"
									checked={grantsAllRowsSelectedTrue}
									on:change={(e) => {
										if (e.target.checked === true) {
											grantsRowsSelected.push(appPermission);
											// reactive statement
											grantsRowsSelected = grantsRowsSelected;
											grantsRowsSelectedTrue = true;
										} else {
											grantsRowsSelected = grantsRowsSelected.filter(
												(selection) => selection !== appPermission
											);
											if (grantsRowsSelected.length === 0) {
												grantsRowsSelectedTrue = false;
											}
										}
									}}
								/>
							</td>
							<td style="min-width: 10rem">
								{appPermission.topicGroup}
							</td>
							<td style="min-width: 18rem">
								{appPermission.topicName} ({appPermission.topicCanonicalName})
							</td>
							<td style="min-width: 6.5rem">
								{#if appPermission.read && appPermission.write}
									{messages['application.detail']['table.applications.access.readwrite']}
									{messages['application.detail']['table.applications.access.write']}
								{:else if appPermission.read}
									{messages['application.detail']['table.applications.access.read']}
								{:else if appPermission.write}
									{messages['application.detail']['table.applications.access.write']}
								{/if}
							</td>

							{#if !$page.url.pathname.includes('search')}
								<td style="min-width: 10rem; max-width: 10rem">
									{#if appPermission.readPartitions?.length > 0}
										{#each appPermission.readPartitions as partition}
											<div
												style="display:inline; align-items: center; background-color: #bad5ff; border-radius: 25px; font-size: 0.8rem; width: fit-content; padding: 0 0.3rem 0 0.3rem; margin: 0 0.1rem 0 0.1rem"
											>
												{partition}
											</div>
										{/each}
									{/if}
								</td>
								<td style="min-width: 10rem; max-width: 10rem">
									{#if appPermission.writePartitions?.length > 0}
										{#each appPermission.writePartitions as partition}
											<div
												style="display:inline; align-items: center; background-color: #bad5ff; border-radius: 25px; font-size: 0.8rem; width: fit-content; padding: 0 0.3rem 0 0.3rem; margin: 0 0.1rem 0 0.1rem"
											>
												{partition}
											</div>
										{/each}
									{/if}
								</td>
							{:else}
								<td />
								<td />
							{/if}

							{#if isApplicationAdmin || $isAdmin}
								<td>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
									<img
										src={deleteSVG}
										tabindex="0"
										alt="delete topic"
										height="23px"
										width="23px"
										style="vertical-align: -0.4rem; float: right; cursor: pointer"
										on:click={() => {
											if (!grantsRowsSelected.some((grant) => grant === appPermission))
												grantsRowsSelected.push(appPermission);
											deleteSelectedGrantsVisible = true;
										}}
									/>
								</td>
							{:else}
								<td />
							{/if}
						</tr>
					</tbody>
				{/each}
			{:else}
				<p style="margin:0.3rem 0 0.6rem 0">
					{messages['application.detail']['empty.topics.associated']}
				</p>
			{/if}
			<tr style="font-size: 0.7rem; text-align: right">
				<td style="border: none" />
				<td style="border: none" />
				<td style="border: none" />
				<td style="border: none" />
				<td style="border: none" />
				<td style="border: none" />
				<td style="border: none; min-width: 3.5rem; text-align:right">
					{#if selectedTopicApplications}
						{selectedTopicApplications.length} of {selectedTopicApplications.length}
					{:else}
						0 of 0
					{/if}
				</td>
			</tr>
		</table>
	{/if}
</div>

{#if reloadMessageVisible}
	<Modal
		title={messages['application.detail']['application.changed.title']}
		actionApplicationChange={true}
		on:cancel={() => (reloadMessageVisible = false)}
		on:reloadContent={() => {
			reloadMessageVisible = false;
			loadApplicationDetail(selectedAppId, selectedAppGroupId);
		}}
	/>
{/if}

<style>
	.content {
		width: 100%;
		min-width: 45rem;
	}

	td {
		height: 2.2rem;
	}
</style>

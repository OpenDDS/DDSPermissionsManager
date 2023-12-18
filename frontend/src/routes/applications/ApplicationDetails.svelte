<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { createEventDispatcher, onDestroy, onMount } from 'svelte';
	import moment from 'moment';
	import { page } from '$app/stores';
	import { isAdmin } from '../../stores/authentication';
	import { createWebSocket, httpAdapter } from '../../appconfig';
	import Modal from '../../lib/Modal.svelte';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import headerTitle from '../../stores/headerTitle';
	import messages from '$lib/messages.json';
	import errorMessages from '$lib/errorMessages.json';
	import editSVG from '../../icons/edit.svg';
	import deleteSVG from '../../icons/delete.svg';
	import featureFlagConfigStore from '../../stores/featureFlagConfig';
	import { convertFromMilliseconds } from '../../utils';
	import ActionsDetails from '../../lib/ActionsDetails.svelte';
	import AdminDetails from '../../lib/AdminDetails.svelte';

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

	let applicationGrants = [];
	let applicationAdmins = [];

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
	let actionsModalVisible = false;
	let selectedGrant = {};
	let selectedAction = {};

	// checkboxes selection
	$: if (
		applicationGrants?.length === grantsRowsSelected?.length &&
		grantsRowsSelected?.length > 0
	) {
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

	const getApplicationGrants = async () => {
		const res = await httpAdapter.get(`/application_grants/application/${selectedAppId}`);

		if (res.data.content) {
			return Promise.all(
				res.data.content.map(async (grant) => {
					const publishActions = grant?.actions?.filter((action) => action.publishAction) || [];
					const subscribeActions = grant?.actions?.filter((action) => !action.publishAction) || [];

					return {
						...grant,
						publishActions,
						subscribeActions
					};
				})
			);
		}

		return [];
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


	const deselectAllGrantsCheckboxes = () => {
		grantsAllRowsSelectedTrue = false;
		grantsRowsSelectedTrue = false;
		grantsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.grants-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const deleteSelectedGrants = async () => {
		try {
			for (const duration of grantsRowsSelected) {
				await httpAdapter.delete(`/application_grants/${duration.id}`);
			}
		} catch (err) {
			errorMessage(errorMessages['grants']['deleting.error.title'], err.message);
		}
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
		applicationAdmins = appDetail.data.admins;
		isPublic = selectedAppPublic;
	};

	const socket = createWebSocket($page.url, `applications/${selectedAppId}`);
	const APIisBroadcastingEvents = $featureFlagConfigStore?.DPM_WEBSOCKETS_BROADCAST_CHANGES;

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
		if (APIisBroadcastingEvents) subscribeApplicationMessage(socket);
		await loadApplicationDetail(selectedAppId, selectedAppGroupId);
		applicationGrants = await getApplicationGrants();
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

	const getDuration = (grantDuration) => {
		const duration = convertFromMilliseconds(
			grantDuration.durationInMilliseconds,
			grantDuration.durationMetadata
		);
		const durationType =
			duration > 1 ? grantDuration.durationMetadata + 's' : grantDuration.durationMetadata;
		return `${duration} ${durationType}`;
	};

	const getActionsTotal = (grant, type) => {
		const totalActions = grant[type]?.length;
		return totalActions > 0 ? totalActions : 0;
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
			await deleteSelectedGrants();
			deselectAllGrantsCheckboxes();
			deleteSelectedGrantsVisible = false;
			applicationGrants = await getApplicationGrants();
		}}
	/>
{/if}

{#if actionsModalVisible}
	<ActionsDetails
		title="Actions"
		actionEditAction={false}
		on:cancel={() => (actionsModalVisible = false)}
		isPublishAction={selectedAction.publishAction}
		{selectedGrant}
		{selectedAction}
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

			<td style="font-weight: 400; margin-left: 2rem">{selectedAppGroupName}</td>
		</tr>
		<tr>
			<td style="font-weight: 300; margin-right: 1rem; width: 6.2rem;">
				{messages['application.detail']['row.three']}
			</td>

			<td style="font-weight: 400; margin-left: 2rem" bind:this={selectedAppDescriptionSelector}
				>{selectedAppDescription ? selectedAppDescription : '-'}</td
			>
		</tr>
		<tr>
			<td style="font-weight: 300">
				{messages['application.detail']['row.four']}
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
		<tr>
			<td>Admins:</td>
			<td>
				<AdminDetails
					admins={applicationAdmins}
				/>
			</td>
		</tr>
	</table>

	{#if selectedAppDateUpdated}
		<p style="font-weight: 200;">Last updated {timeAgo} ({browserFormat})</p>
	{/if}

	{#if !$page.url.pathname.includes('search')}
		<div style="margin-top: 3.5rem">
			<div
				style="display: flex; justify-content: space-between; align-items:center; margin-top: 2rem"
			>
				<div style="font-size:1.3rem; margin-bottom: 1rem">
					{messages['application.detail']['table.applications.label-temp']}
				</div>

				{#if isApplicationAdmin || $isAdmin}
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
				{/if}
			</div>
		</div>

		<table style="min-width: 59rem; max-width: 59rem">
			<thead>
				<tr style="border-top: 1px solid black; border-bottom: 2px solid">
					<td>
						<input
							tabindex="-1"
							disabled={!isApplicationAdmin && !$isAdmin}
							type="checkbox"
							class="grants-checkbox"
							style="margin-right: 0.5rem"
							bind:indeterminate={grantsRowsSelectedTrue}
							on:click={(e) => {
								if (e.target.checked) {
									grantsRowsSelected = applicationGrants;
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
					<td>{messages['application.detail']['table.applications.column.one-temp']}</td>
					<td>{messages['application.detail']['table.applications.column.two-temp']}</td>
					<td>{messages['application.detail']['table.applications.column.three-temp']}</td>
					<td>{messages['application.detail']['table.applications.column.four-temp']}</td>
					{#if isApplicationAdmin || $isAdmin}
						<td />
					{/if}
				</tr>
			</thead>
			{#if applicationGrants.length}
				{#each applicationGrants as grant}
					{@const publishActions = getActionsTotal(grant, 'publishActions')}
					{@const subscribeActions = getActionsTotal(grant, 'subscribeActions')}
					<tbody>
						<tr>
							<td style="line-height: 1rem;">
								<input
									tabindex="-1"
									disabled={!isApplicationAdmin && !$isAdmin}
									type="checkbox"
									class="grants-checkbox"
									style="margin-right: 0.5rem"
									checked={grantsAllRowsSelectedTrue}
									on:change={(e) => {
										if (e.target.checked === true) {
											grantsRowsSelected.push(grant);
											// reactive statement
											grantsRowsSelected = grantsRowsSelected;
											grantsRowsSelectedTrue = true;
										} else {
											grantsRowsSelected = grantsRowsSelected.filter(
												(selection) => selection !== grant
											);
											if (grantsRowsSelected.length === 0) {
												grantsRowsSelectedTrue = false;
											}
										}
									}}
								/>
							</td>
							<td style="min-width: 14rem">
								{grant.name}
							</td>
							<td style="min-width: 14rem">
								{grant.groupName}
							</td>
							<td style="min-width: 14rem">
								{getDuration(grant)}
							</td>
							<td style="min-width: 12rem">
								<!-- svelte-ignore a11y-click-events-have-key-events -->
								<span
									class:clickable-action={publishActions}
									on:click={() => {
										if (publishActions) {
											selectedGrant = grant;
											selectedAction = grant.publishActions[0];
											actionsModalVisible = true;
										}
									}}
								>
									{publishActions}
								</span>
								/
								<!-- svelte-ignore a11y-click-events-have-key-events -->
								<span
									class:clickable-action={subscribeActions.length}
									on:click={() => {
										if (subscribeActions) {
											selectedGrant = grant;
											selectedAction = grant.subscribeActions;
											actionsModalVisible = true;
										}
									}}>{subscribeActions}</span
								>
							</td>
							<td />

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
											if (!grantsRowsSelected.some((grant) => grant === grant))
												grantsRowsSelected.push(grant);
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
					{#if applicationGrants}
						{applicationGrants.length} of {applicationGrants.length}
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

	.clickable-action {
		cursor: pointer;
		text-decoration: underline;
	}
</style>

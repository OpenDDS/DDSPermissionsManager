<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { onMount, createEventDispatcher, onDestroy } from 'svelte';
	import moment from 'moment';
	import { page } from '$app/stores';
	import { createWebSocket, httpAdapter } from '../../appconfig';
	import topicDetails from '../../stores/groupDetails';
	import AdminDetails from '../../lib/AdminDetails.svelte';
	import Modal from '../../lib/Modal.svelte';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import deleteSVG from '../../icons/delete.svg';
	import addSVG from '../../icons/add.svg';
	import editSVG from '../../icons/edit.svg';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import errorMessageAssociation from '../../stores/errorMessageAssociation';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import groupContext from '../../stores/groupContext';
	import nonEmptyInputField from '../../stores/nonEmptyInputField';
	import featureFlagConfigStore from '../../stores/featureFlagConfig';

	export let selectedTopicId, isTopicAdmin;

	const dispatch = createEventDispatcher();

	let selectedTopicName,
		selectedTopicKind,
		selectedTopicGroupName,
		selectedTopicGroupId,
		selectedTopicCanonicalName,
		selectedTopicDescription,
		selectedTopicPublic,
		selectedTopicApplications = [],
		selectedApplicationList,
		selectedGrant,
		readChecked,
		writeChecked,
		topicCurrentGroupPublic,
		partitionListRead = [],
		partitionListWrite = [];

	// Messages
	let deleteToolip,
		deleteMouseEnter = false;

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
		associateApplicationVisible = false,
		editTopicVisible = false,
		deleteSelectedGrantsVisible = false,
		editGrantVisible = false,
		showUnsavedPartitionsModal = false,
		reloadMessageVisible = false;

	// Constants
	const returnKey = 13,
		waitTime = 2000;

	// Public flag
	let isPublic;

	// Error Handling
	let errorMsg, errorObject;

	// Bind Token
	let bindToken;

	//Grant
	let editGrant = false;

	// Websocket
	let topicSocketIsPaused = false;

	const fetchAndUpdateTopic = async () => {
		promise = await httpAdapter.get(`/topics/show/${selectedTopicId}`);
		topicDetails.set(promise.data);

		await loadApplicationPermissions(selectedTopicId);
		selectedTopicId = $topicDetails.id;
		selectedTopicName = $topicDetails.name;
		selectedTopicCanonicalName = $topicDetails.canonicalName;
		selectedTopicDescription = $topicDetails.description;
		selectedTopicPublic = $topicDetails.public;
		selectedTopicGroupName = $topicDetails.groupName;
		selectedTopicGroupId = $topicDetails.group;
		selectedTopicKind = $topicDetails.kind;
		isPublic = $topicDetails.public;

		topicCurrentGroupPublic = await getGroupVisibilityPublic(selectedTopicGroupName);
		headerTitle.set(selectedTopicName);
		detailView.set(true);
	};

	const socket = createWebSocket($page.url, `topics/${selectedTopicId}`);
	const APIisBroadcastingEvents = $featureFlagConfigStore?.DPM_WEBSOCKETS_BROADCAST_CHANGES;

	const subscribeTopicMessage = (topicSocket) => {
		topicSocket.addEventListener('message', (event) => {
			if (!topicSocketIsPaused) {
				if (event.data.includes('topic_updated') || event.data.includes('topic_deleted')) {
					reloadMessageVisible = true;
				}
			}
		});
	};

	const pauseSocketListener = () => {
		topicSocketIsPaused = true;
	};

	const resumeSocketListener = () => {
		topicSocketIsPaused = false;
	};

	onMount(async () => {
		try {
			if (APIisBroadcastingEvents) subscribeTopicMessage(socket);
			await fetchAndUpdateTopic();
		} catch (err) {
			errorMessage(errorMessages['topic']['loading.detail.error.title'], err.message);
		}
	});

	onDestroy(() => {
		socket.close();
	});

	const errorMessage = (errMsg, errObj) => {
		errorMsg = errMsg;
		errorObject = errObj;
		errorMessageVisible = true;
	};

	const extractValues = (arr) => {
		let readValue = null;
		let writeValue = null;

		arr.forEach((item) => {
			if (item.includes('Read: ')) {
				readValue = item.split('Read: ')[1];
			} else if (item.includes('Write: ')) {
				writeValue = item.split('Write: ')[1];
			}
		});

		return [readValue, writeValue];
	};

	const loadApplicationPermissions = async (topicId) => {
		const resApps = await httpAdapter.get(`/application_permissions/topic/${topicId}`);
		selectedTopicApplications = resApps.data.content;
	};

	const addTopicApplicationAssociation = async (topicId, reload = false) => {
		if ($nonEmptyInputField) {
			const [readValue, writeValue] = extractValues($nonEmptyInputField);
			nonEmptyInputField.set(false);

			if (readValue) partitionListRead.push(readValue);
			if (writeValue) partitionListWrite.push(writeValue);
		}

		const config = {
			headers: {
				accept: 'application/json',
				APPLICATION_GRANT_TOKEN: bindToken
			}
		};

		try {
			if (selectedApplicationList && !reload) {
				await httpAdapter.post(
					`/application_permissions/${topicId}`,
					{
						read: readChecked,
						write: writeChecked,
						readPartitions: partitionListRead,
						writePartitions: partitionListWrite
					},
					config
				);
			}
			if (reload) {
				const res = await httpAdapter
					.post(
						`/application_permissions/${topicId}`,
						{
							read: readChecked,
							write: writeChecked,
							readPartitions: partitionListRead,
							writePartitions: partitionListWrite
						},
						config
					)
					.then(async () => await loadApplicationPermissions(topicId));
			}
			editGrant = false;
			errorMessageAssociation.set([]);
			associateApplicationVisible = false;
		} catch (err) {
			if (err.response.data && err.response.status === 400) {
				associateApplicationVisible = false;
				const decodedError = decodeError(Object.create(...err.response.data));
				errorMessage(
					editGrant
						? errorMessages[decodedError.category]['saving.error.title']
						: errorMessages[decodedError.category]['adding.error.title'],
					errorMessages[decodedError.category][decodedError.code]
				);
				editGrant = false;
			} else {
				errorMessageAssociation.set(errorMessages['bind_token']['forbidden']);
			}
		}
	};

	const deleteTopicApplicationAssociation = async () => {
		for (const grant of grantsRowsSelected) {
			await httpAdapter.delete(`/application_permissions/${grant.id}`);
		}
	};

	const updateTopicApplicationAssociation = async () => {
		if ($nonEmptyInputField) {
			const [readValue, writeValue] = extractValues($nonEmptyInputField);
			nonEmptyInputField.set(false);

			if (readValue) partitionListRead.push(readValue);
			if (writeValue) partitionListWrite.push(writeValue);
		}

		try {
			const res = await httpAdapter.put(`/application_permissions/${selectedGrant.id}`, {
				read: readChecked,
				write: writeChecked,
				readPartitions: partitionListRead,
				writePartitions: partitionListWrite
			});
			if (res.status === 200) {
				notifyApplicationAccessTypeSuccess = true;
				editGrantVisible = false;
				editGrant = false;
			}
		} catch {
			editGrantVisible = false;
			errorMessage(errorMessages['topic']['updating.access.type.error.title'], err.message);
			editGrant = false;
		}
		await loadApplicationPermissions(selectedTopicId);
	};

	const decodeError = (errorObject) => {
		errorObject = errorObject.code.replaceAll('-', '_');
		const cat = errorObject.substring(0, errorObject.indexOf('.'));
		const code = errorObject.substring(errorObject.indexOf('.') + 1, errorObject.length);
		return { category: cat, code: code };
	};

	const saveNewTopic = async (newTopicName, newTopicDescription, newTopicPublic) => {
		try {
			pauseSocketListener();
			await httpAdapter.post(`/topics/save/`, {
				name: newTopicName,
				id: selectedTopicId,
				kind: selectedTopicKind,
				group: selectedTopicGroupId,
				groupName: selectedTopicGroupName,
				description: newTopicDescription,
				public: newTopicPublic
			});

			dispatch('reloadTopics');
			await fetchAndUpdateTopic();
			resumeSocketListener();
			selectedTopicDescription = newTopicDescription;
			isPublic = newTopicPublic;
			editTopicVisible = false;
		} catch (err) {
			errorMessage(errorMessages['topic']['adding.error.title'], err.message);
			editTopicVisible = false;
			errorMessageVisible = true;
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

	const deselectAllGrantsCheckboxes = () => {
		grantsAllRowsSelectedTrue = false;
		grantsRowsSelectedTrue = false;
		grantsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.grants-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.grants-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	$: timeAgo = moment($topicDetails?.dateUpdated).fromNow();
	$: browserFormat = new Date($topicDetails?.dateUpdated).toLocaleString();
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

	{#if associateApplicationVisible}
		<Modal
			title={messages['topic.detail']['application.associate']}
			actionAssociateApplication={true}
			{selectedTopicId}
			on:cancel={() => {
				errorMessageAssociation.set([]);
				associateApplicationVisible = false;
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					errorMessageAssociation.set([]);
					associateApplicationVisible = false;
				}
			}}
			on:addTopicApplicationAssociation={(e) => {
				partitionListRead = e.detail.partitionListRead;
				partitionListWrite = e.detail.partitionListWrite;
				readChecked = e.detail.read;
				writeChecked = e.detail.write;
				bindToken = e.detail.bindToken;

				if ($nonEmptyInputField) {
					showUnsavedPartitionsModal = true;
					associateApplicationVisible = false;
				} else addTopicApplicationAssociation(selectedTopicId, true);
			}}
		/>
	{/if}

	{#if showUnsavedPartitionsModal}
		<Modal
			title={messages['topic.detail']['unsaved.partitions.title']}
			actionUnsavedPartitions={true}
			closeModalText={messages['modal']['discard.changes']}
			on:cancel={() => {
				nonEmptyInputField.set(false);
				showUnsavedPartitionsModal = false;
				if (!editGrant) addTopicApplicationAssociation(selectedTopicId, true);
				else updateTopicApplicationAssociation();
			}}
			on:addUnsavedPartitions={() => {
				if (!editGrant) addTopicApplicationAssociation(selectedTopicId, true);
				else updateTopicApplicationAssociation();
				showUnsavedPartitionsModal = false;
			}}
		/>
	{/if}

	{#if editTopicVisible}
		<Modal
			title={messages['topic.detail']['edit']}
			actionEditTopic={true}
			topicCurrentName={selectedTopicName}
			topicCurrentDescription={selectedTopicDescription}
			topicCurrentPublic={selectedTopicPublic}
			{topicCurrentGroupPublic}
			on:saveNewTopic={(e) => {
				saveNewTopic(e.detail.newTopicName, e.detail.newTopicDescription, e.detail.newTopicPublic);
				selectedTopicName = e.detail.newTopicName;
				selectedTopicDescription = e.detail.newTopicDescription;
				selectedTopicPublic = e.detail.newTopicPublic;
				editTopicVisible = false;
			}}
			on:cancel={() => (editTopicVisible = false)}
		/>
	{/if}

	{#if editGrantVisible}
		<Modal
			title={messages['topic.detail']['edit.grant']}
			actionAssociateApplicationTwo={true}
			readChecked={selectedGrant.read}
			writeChecked={selectedGrant.write}
			partitionListRead={selectedGrant.readPartitions}
			partitionListWrite={selectedGrant.writePartitions}
			on:addTopicApplicationAssociation={(e) => {
				editGrant = true;
				partitionListRead = e.detail.partitionListRead;
				partitionListWrite = e.detail.partitionListWrite;
				readChecked = e.detail.read;
				writeChecked = e.detail.write;

				if ($nonEmptyInputField) {
					showUnsavedPartitionsModal = true;
					editGrantVisible = false;
				} else updateTopicApplicationAssociation();
			}}
			on:cancel={() => (editGrantVisible = false)}
		/>
	{/if}

	{#if deleteSelectedGrantsVisible}
		<Modal
			actionDeleteGrants={true}
			title="{messages['topic.detail']['delete.grants.title']} {grantsRowsSelected.length > 1
				? messages['topic.detail']['delete.grants.multiple']
				: messages['topic.detail']['delete.grants.single']}"
			on:cancel={() => {
				if (grantsRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
					grantsRowsSelected = [];
				deleteSelectedGrantsVisible = false;
			}}
			on:deleteGrants={async () => {
				await deleteTopicApplicationAssociation();

				await loadApplicationPermissions(selectedTopicId);
				deselectAllGrantsCheckboxes();
				deleteSelectedGrantsVisible = false;
			}}
		/>
	{/if}

	{#await promise then _}
		<div style="display: inline-flex; align-items: baseline">
			<table class="topics-details">
				<tr>
					<td>{messages['topic.detail']['row.one']}</td>
					<td>{selectedTopicName} ({selectedTopicCanonicalName})</td>
				</tr>
				<td>{messages['topic.detail']['row.two']}</td>
				<td style="width: fit-content">
					{#if selectedTopicDescription}
						{selectedTopicDescription}
					{:else}
						-
					{/if}
				</td>
				<tr />

				<tr>
					<td>{messages['topic.detail']['row.three']}</td>
					<td>{selectedTopicGroupName}</td>
				</tr>

				<tr>
					<td style="min-width:12rem">{messages['topic.detail']['row.four']}</td>
					<td
						>{#if selectedTopicKind === 'B'}
							{messages['topic.detail']['any.application.can.read.yes']}
						{:else}
							{messages['topic.detail']['any.application.can.read.no']}
						{/if}
					</td>
				</tr>

				<tr>
					<td>{messages['topic.detail']['row.five']}</td>
					<td>
						<input
							type="checkbox"
							tabindex="-1"
							style="vertical-align: middle; margin-left: 0.1rem; width: 15px; height: 15px"
							bind:checked={isPublic}
							on:change={() => (isPublic = selectedTopicPublic)}
						/>
					</td>
				</tr>

				<tr>
					<td>Admins:</td>
					<td>
						{#if selectedTopicGroupId}
							<AdminDetails
								groupId={selectedTopicGroupId}
								adminCategory="topic"
							/>
						{/if}
					</td>
				</tr>
			</table>

			{#if $isAdmin || $permissionsByGroup.find((permission) => permission.groupId === selectedTopicGroupId && permission.isTopicAdmin)}
				<!-- svelte-ignore a11y-click-events-have-key-events -->
				<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
				<img
					src={editSVG}
					tabindex="0"
					alt="edit topic"
					height="20rem"
					style="margin-left: 2rem; cursor:pointer"
					on:click={() => (editTopicVisible = true)}
				/>
			{/if}
		</div>
		{#if $topicDetails.dateUpdated}
			<p style="font-weight: 200;">Last updated {timeAgo} ({browserFormat})</p>
		{/if}

		{#if !$page.url.pathname.includes('search')}
			<div>
				<div
					style="display: flex; justify-content: space-between; align-items:center; margin-top: 2rem"
				>
					<div style="font-size:1.3rem; margin-bottom: 1rem">
						{messages['topic.detail']['table.grants.label']}
					</div>
					<div style="margin-bottom: 0.5rem; margin-right: -1rem">
						<img
							src={deleteSVG}
							alt="options"
							class="dot"
							class:button-disabled={(!$isAdmin && !isTopicAdmin) ||
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
								if ($isAdmin || isTopicAdmin) {
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

						<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
						<img
							data-cy="add-grant"
							src={addSVG}
							tabindex="0"
							alt="options"
							class="dot"
							class:button-disabled={!$isAdmin &&
								!$permissionsByGroup?.find(
									(gm) => gm.groupName === $groupContext?.name && gm.isTopicAdmin === true
								)}
							on:click={() => {
								if ($isAdmin || isTopicAdmin) {
									nonEmptyInputField.set(false);
									associateApplicationVisible = true;
								}
							}}
							on:keydown={(event) => {
								if (event.which === returnKey) {
									if ($isAdmin || isTopicAdmin) {
										nonEmptyInputField.set(false);
										associateApplicationVisible = true;
									}
								}
							}}
						/>
					</div>
				</div>

				<table style="max-width: 50rem; min-width: 53rem">
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
							<td>{messages['topic.detail']['table.grants.column.one']}</td>
							<td>{messages['topic.detail']['table.grants.column.two']}</td>
							<td>{messages['topic.detail']['table.grants.column.three']}</td>
							<td>{messages['topic.detail']['table.grants.column.four']}</td>
							<td>{messages['topic.detail']['table.grants.column.five']}</td>
							<td />
							<td />
						</tr>
					</thead>

					{#if selectedTopicApplications?.length > 0}
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
									<td style="min-width: fit-content"> {appPermission.applicationGroupName} </td>
									<td style="min-width: fit-content"> {appPermission.applicationName} </td>
									<td style="min-width: 5.5rem; max-width: 5.5rem">
										{#if appPermission.read && appPermission.write}
											{messages['topic.detail']['table.grants.access.readwrite']}
										{:else if appPermission.read}
											{messages['topic.detail']['table.grants.access.read']}
										{/if}
										{#if appPermission.write}
											{messages['topic.detail']['table.grants.access.write']}
										{:else if !appPermission.read && !appPermission.write}
											-
										{/if}
									</td>
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
									<td style="min-width: 10rem; max-width: 10rem; margin: 0 0.3rem 0 0.3rem">
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
									<td
										style="cursor: pointer; text-align: right"
										on:keydown={(event) => {
											if (event.which === returnKey) {
												selectedGrant = appPermission;
												editGrantVisible = true;
											}
										}}
									>
										<!-- svelte-ignore a11y-click-events-have-key-events -->
										<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
										<img
											data-cy="edit-users-icon"
											src={editSVG}
											tabindex="0"
											height="17rem"
											width="17rem"
											style="vertical-align: -0.225rem"
											alt="edit user"
											on:click={() => {
												selectedGrant = appPermission;
												editGrantVisible = true;
											}}
										/>
									</td>
									<td
										style="cursor: pointer; text-align: right; padding-right: 0.25rem; width: 1rem"
									>
										<!-- svelte-ignore a11y-click-events-have-key-events -->
										<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
										<img
											data-cy="delete-users-icon"
											src={deleteSVG}
											tabindex="0"
											height="27px"
											width="27px"
											style="vertical-align: -0.5rem"
											alt="delete user"
											on:click={() => {
												if (!grantsRowsSelected.some((grant) => grant === appPermission))
													grantsRowsSelected.push(appPermission);
												deleteSelectedGrantsVisible = true;
											}}
										/>
									</td>
								</tr>
							</tbody>
						{/each}
					{:else}
						<td style="border: none">{messages['topic.detail']['table.grants.empty']}</td>
					{/if}
				</table>
				{#if notifyApplicationAccessTypeSuccess}
					<span
						style="float: right; margin-top: 0.5rem; font-size: 0.65rem; color: white; background-color: black; padding: 0.2rem 0.4rem 0.2rem 0.4rem; border-radius: 15px"
						>{messages['topic.detail']['updated.success']}</span
					>
				{/if}
			</div>
		{/if}

		<p style="margin-top: 8rem">{messages['footer']['message']}</p>
	{/await}
{/if}

{#if reloadMessageVisible}
	<Modal
		title={messages['topic.detail']['topic.changed.title']}
		actionTopicChange={true}
		on:cancel={() => (reloadMessageVisible = false)}
		on:reloadContent={() => {
			reloadMessageVisible = false;
			fetchAndUpdateTopic();
		}}
	/>
{/if}

<style>
	.topics-details {
		font-size: 12pt;
		width: 41rem;
		margin-top: 1.6rem;
	}

	.topics-details td {
		height: 2.2rem;
	}
</style>

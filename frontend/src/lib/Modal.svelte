<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { fade, fly } from 'svelte/transition';
	import { createEventDispatcher } from 'svelte';
	import { onMount, onDestroy } from 'svelte';
	import { httpAdapter } from '../appconfig';
	import closeSVG from '../icons/close.svg';
	import groupnotpublicSVG from '../icons/groupnotpublic.svg';
	import Switch from './Switch.svelte';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import errorMessageAssociation from '../stores/errorMessageAssociation';
	import groupContext from '../stores/groupContext';
	import modalOpen from '../stores/modalOpen';
	import nonEmptyInputField from '../stores/nonEmptyInputField';
	import tooltips from '$lib/tooltips.json';
	import CheckBox from '$lib/CheckBox.svelte';

	export let title;
	export let email = false;
	export let topicName = false;
	export let topicSetName = false;
	export let applicationName = false;
	export let groupNewName = false;
	export let group = false;
	export let adminRoles = false;
	export let actionAddUser = false;
	export let actionAddSuperUser = false;
	export let actionAddTopic = false;
	export let actionAddTopicSet = false;
	export let actionAddApplication = false;
	export let actionAddGroup = false;
	export let actionAssociateApplication = false;
	export let actionEditUser = false;
	export let actionEditApplication = false;
	export let actionEditTopic = false;
	export let actionEditTopicSet = false;
	export let actionEditGroup = false;
	export let actionDeleteUsers = false;
	export let actionDeleteSuperUsers = false;
	export let actionDeleteTopics = false;
	export let actionDeleteTopicsFromTopicSet = false;
	export let actionDeleteGrants = false;
	export let actionDeleteGroups = false;
	export let actionDeleteApplications = false;
	export let actionTopicChange = false;
	export let actionApplicationChange = false;
	export let actionUnsavedPartitions = false;
	export let noneditable = false;
	export let emailValue = '';
	export let newTopicName = '';
	export let newTopicSetName = '';
	export let originalTopicSetName = '';
	export let groupId = '';
	export let anyApplicationCanRead = false;
	export let searchGroups = '';
	export let selectedGroupMembership = '';
	export let groupCurrentName = '';
	export let groupCurrentDescription = '';
	export let groupCurrentPublic = false;
	export let appCurrentName = '';
	export let appCurrentDescription = '';
	export let appCurrentPublic = false;
	export let appCurrentGroupPublic = false;
	export let topicCurrentName = '';
	export let topicCurrentDescription = '';
	export let topicCurrentPublic = false;
	export let topicCurrentGroupPublic = false;
	export let selectedApplicationList = [];
	export let errorDescription = '';
	export let reminderDescription = '';
	export let errorMsg = false;
	export let reminderMsg = false;
	export let closeModalText = messages['modal']['close.modal.label'];
	export let selectedTopicId = '';

	export let partitionListRead = [];
	export let partitionListWrite = [];

	export let readChecked = false;
	export let writeChecked = false;

	const dispatch = createEventDispatcher();

	// Constants
	const returnKey = 13;
	const groupsToCompare = 7;
	const minNameLength = 3;
	const maxCharactersLength = 4000;

	// Forms
	export let actionAssociateApplicationTwo = false;
	let selectedIsGroupAdmin = false;
	let selectedIsTopicAdmin = false;
	let selectedIsApplicationAdmin = false;
	let appName = '';
	let newGroupName = '';
	let newGroupDescription = '';
	let newGroupPublic;
	let newAppName = '';
	let newAppDescription = '';
	let accessTypeSelection, newAppPublic, topicCurrentPublicInitial, appCurrentPublicInitial;

	// Bind Token
	let bindToken;
	let tokenApplicationName, tokenApplicationGroup, tokenApplicationEmail;
	let invalidToken = false;

	// Error Handling
	let invalidTopic = false;
	let invalidGroup = false;
	let invalidApplicationName = false;
	let invalidTopicName = false;
	let invalidEmail = false;
	let errorMessageGroup = '';
	let errorMessageApplication = '';
	let errorMessageTopic = '';
	let errorMessageEmail = '';
	let errorMessageName = '';
	let validRegex = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$/;

	// SearchBox
	let selectedGroup;

	// Tooltip
	let groupNotPublicTooltip,
		groupNotPublicMouseEnter = false;

	if (actionEditGroup) {
		newGroupName = groupCurrentName;
		newGroupDescription = groupCurrentDescription;
		newGroupPublic = groupCurrentPublic;
	}

	if (actionEditApplication) {
		newAppName = appCurrentName;
		newAppDescription = appCurrentDescription;
		newAppPublic = appCurrentPublic;
	}

	// Bind Token Decode
	$: if (bindToken?.length > 0) {
		const tokenBody = bindToken.substring(bindToken.indexOf('.') + 1, bindToken.lastIndexOf('.'));
		decodeToken(tokenBody);
	} else {
		errorMessageAssociation.set([]);
	}

	onMount(() => {
		modalOpen.set(true);
		if ($groupContext) selectedGroup = $groupContext.id;
		topicCurrentPublicInitial = topicCurrentPublic;
		appCurrentPublicInitial = appCurrentPublic;

		// Sort the values left in the unsaved partitions input field
		if ($nonEmptyInputField) {
			nonEmptyInputField.set(
				$nonEmptyInputField.sort((a, b) => {
					if (a.startsWith('Read:') && b.startsWith('Write:')) {
						return -1;
					} else if (a.startsWith('Write:') && b.startsWith('Read:')) {
						return 1;
					}
					return 0;
				})
			);
		}
	});

	onDestroy(() => modalOpen.set(false));

	const validateEmail = (input) => {
		if (input.match(validRegex)) invalidEmail = false;
		else {
			invalidEmail = true;
			errorMessageEmail = errorMessages['email']['is_not_format'];
		}
	};

	const validateTopicName = async () => {
		const res = await httpAdapter.get(
			`/topics?page=0&size=${groupsToCompare}&filter=${newTopicName}`
		);

		if (
			newTopicName?.length > minNameLength &&
			res.data.content?.some(
				(topic) =>
					topic.name.toUpperCase() === newTopicName.toUpperCase() &&
					topic.groupName.toUpperCase() === $groupContext.name.toUpperCase()
			)
		) {
			return false;
		} else {
			return true;
		}
	};

	const validateApplicationName = async () => {
		const res = await httpAdapter.get(
			`/applications?page=0&size=${groupsToCompare}&filter=${appName}&group=${$groupContext.id}`
		);
		if (
			appName?.length > 0 &&
			res.data.content?.some(
				(application) => application.name.toUpperCase() === appName.toUpperCase()
			)
		) {
			return false;
		} else {
			return true;
		}
	};

	const validateGroupMembership = async () => {
		const res = await httpAdapter.get(
			`/group_membership?page=0&size=${groupsToCompare}&filter=${emailValue}`
		);
		if (
			emailValue?.length > minNameLength &&
			res.data.content?.some(
				(user) =>
					user.permissionsUserEmail.toUpperCase() === emailValue.toUpperCase() &&
					user.permissionsGroup === $groupContext.id
			)
		) {
			return false;
		} else {
			return true;
		}
	};

	const validateNameLength = (name, category) => {
		if (name?.length < minNameLength && category) {
			errorMessageName = errorMessages[category]['name.cannot_be_less_than_three_characters'];
			return false;
		}
		if (name?.length >= minNameLength) {
			return true;
		}
	};

	function closeModal() {
		dispatch('cancel');
	}

	const actionAddUserEvent = async () => {
		let newGroupMembership = {
			selectedIsGroupAdmin: selectedIsGroupAdmin,
			selectedIsTopicAdmin: selectedIsTopicAdmin,
			selectedIsApplicationAdmin: selectedIsApplicationAdmin,
			selectedGroup: selectedGroup,
			searchGroups: searchGroups,
			emailValue: emailValue
		};

		validateEmail(emailValue);

		const validGroupMembership = await validateGroupMembership();
		if (!validGroupMembership) {
			errorMessageEmail = errorMessages['group_membership']['exists'];
			return;
		}

		if (!invalidEmail && validGroupMembership) dispatch('addGroupMembership', newGroupMembership);
	};

	const actionAddSuperUserEvent = () => {
		validateEmail(emailValue);
		if (!invalidEmail) {
			dispatch('addSuperUser', emailValue);
			closeModal();
		}
	};

	const actionAddTopicEvent = async () => {
		let newTopic = {
			newTopicName: newTopicName,
			newTopicDescription: topicCurrentDescription,
			newTopicPublic: topicCurrentPublic,
			searchGroups: searchGroups,
			selectedGroup: selectedGroup,
			anyApplicationCanRead: anyApplicationCanRead,
			selectedApplicationList: selectedApplicationList
		};

		invalidTopic = !validateNameLength(newTopicName, 'topic');
		if (invalidTopic) {
			errorMessageName = errorMessages['topic']['name.cannot_be_less_than_three_characters'];
			return;
		}

		const validTopicName = await validateTopicName();
		if (!validTopicName) {
			errorMessageTopic = errorMessages['topic']['exists'];
			return;
		}

		if (!invalidTopic) {
			dispatch('addTopic', newTopic);
			closeModal();
		}
	};

	const actionAddTopicSetEvent = async () => {
		let newTopicSet = {
			newTopicSetName: newTopicSetName,
			selectedGroup: selectedGroup
		};

		invalidTopic = !validateNameLength(newTopicSetName, 'topic');
		if (invalidTopic) {
			errorMessageName = errorMessages['topic-sets']['name.cannot_be_less_than_three_characters'];
			return;
		}

		const validTopicName = await validateTopicName();
		if (!validTopicName) {
			errorMessageTopic = errorMessages['topic-sets']['exists'];
			return;
		}

		if (!invalidTopic) {
			dispatch('addTopicSet', newTopicSet);
			closeModal();
		}
	};

	const actionAddApplicationEvent = async () => {
		let newApplication = {
			appName: appName,
			appDescription: newAppDescription,
			appPublic: newAppPublic,
			searchGroups: searchGroups,
			selectedGroup: selectedGroup
		};

		invalidApplicationName = !validateNameLength(appName, 'application');

		const validApplication = await validateApplicationName();
		if (!validApplication) {
			errorMessageApplication = errorMessages['application']['exists'];
			return;
		}

		if (!invalidApplicationName && validApplication) {
			dispatch('addApplication', newApplication);
			closeModal();
		}
	};

	const actionAddGroupEvent = async () => {
		invalidGroup = !validateNameLength(newGroupName, 'group');

		let returnGroupName = {
			newGroupName: newGroupName,
			newGroupDescription: newGroupDescription,
			newGroupPublic: newGroupPublic
		};

		const res = await httpAdapter.get(
			`/groups?page=0&size=${groupsToCompare}&filter=${newGroupName}`
		);

		if (res.data.content) {
			if (
				!actionEditGroup &&
				res.data.content.some((group) => group.name.toUpperCase() === newGroupName.toUpperCase())
			) {
				errorMessageGroup = errorMessages['group']['exists'];
				return;
			} else {
				if (actionEditGroup) {
					dispatch('addGroup', {
						groupId: groupId,
						newGroupName: newGroupName,
						newGroupDescription: newGroupDescription,
						newGroupPublic: newGroupPublic
					});
				} else {
					dispatch('addGroup', returnGroupName);
				}

				closeModal();
			}
		} else {
			if (actionEditGroup) {
				dispatch('addGroup', {
					groupId: groupId,
					newGroupName: newGroupName,
					newGroupDescription: newGroupDescription,
					newGroupPublic: newGroupPublic
				});
			} else {
				dispatch('addGroup', returnGroupName);
			}
			closeModal();
		}
	};

	const actionEditUserEvent = () => {
		dispatch('updateGroupMembership', {
			groupAdmin: selectedGroupMembership.groupAdmin,
			topicAdmin: selectedGroupMembership.topicAdmin,
			applicationAdmin: selectedGroupMembership.applicationAdmin
		});
	};

	const decodeToken = async (token) => {
		let res = atob(token);

		try {
			res = JSON.parse(res);
			invalidToken = false;
		} catch (err) {
			errorMessageAssociation.set(errorMessages['bind_token']['invalid']);
			invalidToken = true;
		}

		tokenApplicationName = res.appName;
		tokenApplicationGroup = res.groupName;
		tokenApplicationEmail = res.email;
	};
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<div class="modal-backdrop" on:click={closeModal} transition:fade />
<div class="modal" transition:fly={{ y: 300 }}>
	<!-- svelte-ignore a11y-click-events-have-key-events -->
	<img src={closeSVG} alt="close" class="close-button" on:click={closeModal} />
	<h2 class:condensed={title?.length > 25}>{title}</h2>
	<hr />
	<div class="content">
		{#if errorMsg}
			<p>{errorDescription}</p>
		{/if}

		{#if reminderMsg}
			<p>{reminderDescription}</p>
		{/if}

		{#if email}
			{#if !actionEditUser}
				<div style="float: right; font-size: 0.75rem; margin-bottom: -0.1rem">*</div>
			{/if}
			<!-- svelte-ignore a11y-autofocus -->
			<input
				data-cy="email-input"
				autofocus
				disabled={noneditable}
				placeholder={messages['modal']['input.email.placeholder']}
				class:invalid={invalidEmail && emailValue?.length >= 1}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
				bind:value={emailValue}
				on:blur={() => {
					emailValue = emailValue.trim();
					if (emailValue?.length > 0) validateEmail(emailValue);
				}}
				on:click={() => {
					invalidEmail = false;
					errorMessageEmail = '';
				}}
				on:keydown={(event) => {
					invalidEmail = false;
					errorMessageEmail = '';

					if (event.which === returnKey) {
						if (actionAddUser) {
							emailValue = emailValue.trim();
							validateEmail(emailValue);
							if (!invalidEmail && emailValue.length >= minNameLength && searchGroups?.length > 3) {
								actionAddUserEvent();
							}
						}
						if (actionAddSuperUser && emailValue.length >= 10) actionAddSuperUserEvent();
					}
				}}
			/>
			<span
				class="error-message"
				style="	top: 10.4rem; right: 2.4rem"
				class:hidden={errorMessageEmail?.length === 0}
			>
				{errorMessageEmail}
			</span>
			{#if noneditable}
				<span
					style="display: inline-flex; font-size: 0.65rem; position: relative; top: -3rem; left: 0.5rem; background-color: rgb(246,246,246); padding: 0 0.2rem 0 0.2rem; color: rgb(120,120,120)"
				>
					{messages['modal']['input.email.label']}
				</span>
			{/if}
		{/if}

		{#if topicName}
			<div style="float: right; font-size: 0.75rem; margin-bottom: -0.1rem">*</div>
			<!-- svelte-ignore a11y-autofocus -->
			<input
				data-cy="topic-name"
				autofocus
				placeholder={messages['modal']['input.topic.placeholder']}
				class:invalid={invalidTopic}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
				bind:value={newTopicName}
				on:blur={() => {
					newTopicName = newTopicName.trim();
				}}
				on:keydown={(event) => {
					errorMessageName = '';

					if (event.which === returnKey) {
						newTopicName = newTopicName.trim();
						actionAddTopicEvent();
					}
				}}
				on:click={() => {
					errorMessageTopic = '';
					errorMessageName = '';
				}}
			/>
		{/if}

		{#if topicSetName}
			<div style="float: right; font-size: 0.75rem; margin-bottom: -0.1rem">*</div>
			<!-- svelte-ignore a11y-autofocus -->
			<input
				data-cy="topic-name"
				autofocus
				placeholder={messages['modal']['input.topic-set.placeholder']}
				class:invalid={invalidTopic}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
				bind:value={newTopicSetName}
				on:blur={() => {
					newTopicSetName = newTopicSetName.trim();
				}}
				on:keydown={(event) => {
					errorMessageName = '';

					if (event.which === returnKey) {
						newTopicSetName = newTopicSetName.trim();
						actionAddTopicSetEvent();
					}
				}}
				on:click={() => {
					errorMessageTopic = '';
					errorMessageName = '';
				}}
			/>
		{/if}

		{#if errorMessageTopic?.substring(0, errorMessageTopic?.indexOf(' ')) === messages['modal']['error.message.topic.substring'] && errorMessageTopic?.length > 0}
			<span
				class="error-message"
				style="	top: 10.5rem; right: 2.2rem"
				class:hidden={errorMessageTopic?.length === 0}
			>
				{errorMessageTopic}
			</span>
		{/if}

		{#if errorMessageName?.substring(0, errorMessageName?.indexOf(' ')) === messages['modal']['error.message.topic.substring'] && errorMessageName?.length > 0}
			<span
				class="error-message"
				style="	top: 10.5rem; right: 2.2rem"
				class:hidden={errorMessageName?.length === 0}
			>
				{errorMessageName}
			</span>
		{/if}

		{#if applicationName}
			<div style="float: right; font-size: 0.75rem; margin-bottom: -0.1rem">*</div>
			<!-- svelte-ignore a11y-autofocus -->
			<input
				data-cy="application-name"
				autofocus
				placeholder={messages['modal']['input.application.placeholder']}
				class:invalid={invalidApplicationName}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
				bind:value={appName}
				on:blur={() => {
					appName = appName.trim();
				}}
				on:keydown={(event) => {
					errorMessageName = '';
					errorMessageApplication = '';
					if (event.which === returnKey) {
						appName = appName.trim();
						actionAddApplicationEvent();
					}
				}}
				on:click={() => {
					errorMessageName = '';
					errorMessageApplication = '';
				}}
			/>
		{/if}

		{#if errorMessageName?.substring(0, errorMessageName?.indexOf(' ')) === messages['modal']['error.message.application.substring'] && errorMessageName?.length > 0}
			<span
				class="error-message"
				style="	top: 10.5rem; right: 1.4rem"
				class:hidden={errorMessageName?.length === 0}
			>
				{errorMessageName}
			</span>
		{/if}

		{#if errorMessageApplication?.substring(0, errorMessageApplication?.indexOf(' ')) === messages['modal']['error.message.application.substring'] && errorMessageApplication?.length > 0}
			<span
				class="error-message"
				style="	top: 10.5rem; right: 2.3rem"
				class:hidden={errorMessageApplication?.length === 0}
			>
				{errorMessageApplication}
			</span>
		{/if}

		{#if groupNewName}
			<div style="float: right; font-size: 0.75rem; margin-bottom: -0.1rem">*</div>
			<!-- svelte-ignore a11y-autofocus -->
			<input
				data-cy="group-new-name"
				autofocus
				placeholder={messages['modal']['input.group.placeholder']}
				class:invalid={invalidGroup || errorMessageGroup?.length > 0}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
				bind:value={newGroupName}
				on:blur={() => {
					newGroupName = newGroupName.trim();
				}}
				on:keydown={(event) => {
					errorMessageName = '';
					errorMessageGroup = '';

					if (event.which === returnKey) {
						newGroupName = newGroupName.trim();
						invalidGroup = !validateNameLength(newGroupName, 'group');
						if (actionAddGroup && !invalidGroup) {
							actionAddGroupEvent();
						}

						if (
							actionEditGroup &&
							!invalidGroup &&
							newGroupName !== groupCurrentName &&
							newGroupDescription !== groupCurrentDescription &&
							newGroupPublic !== groupCurrentPublic
						) {
							actionAddGroupEvent();
						}
						if (newGroupName === groupCurrentName) {
							dispatch('cancel');
						}
					}
				}}
				on:click={() => {
					errorMessageName = '';
					errorMessageGroup = '';
				}}
			/>

			<textarea
				data-cy="group-new-description"
				placeholder={messages['modal']['input.group.description.placeholder']}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin: 1.4rem 2rem 0 0; resize: none"
				rows="5"
				maxlength={maxCharactersLength}
				bind:value={newGroupDescription}
				on:blur={() => {
					newGroupDescription = newGroupDescription.trim();
				}}
				on:click={() => {
					errorMessageName = '';
					errorMessageGroup = '';
				}}
			/>

			<span style="font-size: 0.75rem; float: right; margin-top: 0.2rem"
				>{newGroupDescription?.length}/{maxCharactersLength}</span
			>

			<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content">
				<span style="font-weight: 300; vertical-align: 1.12rem"
					>{messages['modal']['public.label']}</span
				>
				<input
					type="checkbox"
					style="vertical-align: 1rem; margin-left: 2rem; width: 15px; height: 15px"
					bind:checked={newGroupPublic}
				/>
			</div>

			{#if errorMessageName?.substring(0, errorMessageName?.indexOf(' ')) === 'Group' && errorMessageName?.length > 0}
				<span
					class="error-message"
					style="	top: 10.5rem; right: 2.1rem"
					class:hidden={errorMessageName?.length === 0}
				>
					{errorMessageName}
				</span>
			{/if}

			{#if errorMessageGroup?.substring(0, errorMessageGroup?.indexOf(' ')) === messages['modal']['error.message.group.substring'] && errorMessageGroup?.length > 0}
				<span
					class="error-message"
					style="	top: 10.5rem; right: 2.3rem"
					class:hidden={errorMessageGroup?.length === 0}
				>
					{errorMessageGroup}
				</span>
			{/if}
		{/if}

		{#if actionEditApplication || actionAddApplication}
			{#if actionEditApplication}
				<div style="float: right; font-size: 0.75rem; margin-bottom: -0.1rem">*</div>
				<!-- svelte-ignore a11y-autofocus -->
				<input
					autofocus
					data-cy="application-name"
					placeholder={messages['modal']['input.application.edit.placeholder']}
					class:invalid={invalidApplicationName}
					style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
					bind:value={newAppName}
					on:blur={() => {
						newAppName = newAppName.trim();
					}}
					on:keydown={(event) => {
						errorMessageName = '';
						if (event.which === returnKey) {
							newAppName = newAppName.trim();
							invalidApplicationName = !validateNameLength(newAppName, 'application');
							if (!invalidApplicationName)
								dispatch('saveNewApp', {
									newAppName: newAppName,
									newAppDescription: newAppDescription,
									newAppPublic: newAppPublic
								});
						}
					}}
					on:click={() => (errorMessageName = '')}
				/>
			{/if}
			<textarea
				data-cy="application-new-description"
				placeholder={messages['modal']['input.application.description.placeholder']}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin: 1.4rem 2rem 0 0; resize: none"
				rows="5"
				maxlength={maxCharactersLength}
				bind:value={newAppDescription}
				on:blur={() => {
					newAppDescription = newAppDescription.trim();
				}}
				on:click={() => {
					errorMessageName = '';
					errorMessageGroup = '';
				}}
			/>
			<span style="font-size: 0.75rem; float: right; margin-top: 0.2rem"
				>{newAppDescription?.length}/{maxCharactersLength}</span
			>

			<div
				style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content"
				class:disabled={!appCurrentGroupPublic}
				disabled={!appCurrentGroupPublic}
			>
				<span style="font-weight: 300; vertical-align: 1.12rem">
					{messages['modal']['public.label']}
				</span>

				{#if !appCurrentGroupPublic}
					<img
						src={groupnotpublicSVG}
						alt="group not public"
						height="21rem"
						style="vertical-align:top; margin-left: 0.5rem"
						on:mouseenter={() => {
							groupNotPublicMouseEnter = true;
							groupNotPublicTooltip = tooltips['group.not.public'];
							const tooltip = document.querySelector('#group-not-public');
							setTimeout(() => {
								if (groupNotPublicMouseEnter) {
									tooltip.classList.remove('tooltip-hidden');
									tooltip.classList.add('tooltip');
								}
							}, 1000);
						}}
						on:mouseleave={() => {
							groupNotPublicMouseEnter = false;
							const tooltip = document.querySelector('#group-not-public');
							setTimeout(() => {
								if (!groupNotPublicMouseEnter) {
									tooltip.classList.add('tooltip-hidden');
									tooltip.classList.remove('tooltip');
								}
							}, 1000);
						}}
					/>

					<span
						id="group-not-public"
						class="tooltip-hidden"
						style="margin-top: 1.8rem; margin-left: -5rem"
						>{groupNotPublicTooltip}
					</span>
				{/if}

				<input
					type="checkbox"
					style="vertical-align: 1rem; margin-left: 2rem; width: 15px; height: 15px"
					bind:checked={newAppPublic}
					on:change={() => {
						if (!appCurrentGroupPublic) newAppPublic = appCurrentPublicInitial;
					}}
				/>
			</div>

			{#if !actionAddApplication}
				<span style="font-size:0.7rem; float: right">
					{messages['modal']['required.field.message.three']}
				</span>
			{/if}
		{/if}

		{#if actionEditTopic || actionAddTopic}
			<textarea
				data-cy="topic-new-description"
				placeholder={messages['modal']['input.topic.description.placeholder']}
				class:add-topic={actionAddTopic}
				class:edit-topic={actionEditTopic}
				rows="5"
				maxlength={maxCharactersLength}
				bind:value={topicCurrentDescription}
				on:blur={() => {
					topicCurrentDescription = topicCurrentDescription.trim();
				}}
				on:click={() => {
					errorMessageName = '';
					errorMessageGroup = '';
				}}
			/>

			<span style="font-size: 0.75rem; float: right; margin-top: 0.2rem"
				>{topicCurrentDescription?.length}/{maxCharactersLength}</span
			>

			<div
				style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content"
				class:disabled={!topicCurrentGroupPublic}
				disabled={!topicCurrentGroupPublic}
			>
				<span style="font-weight: 300; vertical-align: 1.12rem"
					>{messages['modal']['public.label']}</span
				>
				{#if !topicCurrentGroupPublic}
					<img
						src={groupnotpublicSVG}
						alt="group not public"
						height="21rem"
						style="vertical-align:top; margin-left: 0.5rem"
						on:mouseenter={() => {
							groupNotPublicMouseEnter = true;
							groupNotPublicTooltip = tooltips['group.not.public'];
							const tooltip = document.querySelector('#group-not-public');
							setTimeout(() => {
								if (groupNotPublicMouseEnter) {
									tooltip.classList.remove('tooltip-hidden');
									tooltip.classList.add('tooltip');
								}
							}, 1000);
						}}
						on:mouseleave={() => {
							groupNotPublicMouseEnter = false;
							const tooltip = document.querySelector('#group-not-public');
							setTimeout(() => {
								if (!groupNotPublicMouseEnter) {
									tooltip.classList.add('tooltip-hidden');
									tooltip.classList.remove('tooltip');
								}
							}, 1000);
						}}
					/>

					<span
						id="group-not-public"
						class="tooltip-hidden"
						style="margin-top: 1.8rem; margin-left: -5rem"
						>{groupNotPublicTooltip}
					</span>
				{/if}
				<input
					type="checkbox"
					style="vertical-align: 1rem; margin-left: 2rem; width: 15px; height: 15px"
					bind:checked={topicCurrentPublic}
					on:change={() => {
						if (!topicCurrentGroupPublic) topicCurrentPublic = topicCurrentPublicInitial;
					}}
				/>
			</div>
		{/if}

		{#if group}
			<input
				data-cy="group-name"
				id="group-context"
				readonly
				disabled
				style="background: rgb(246, 246, 246); width: 13.2rem; margin: 1.5rem 2rem 1rem 0"
				bind:value={$groupContext.name}
			/>

			<span
				style="display: inline-flex; font-size: 0.65rem; position: relative; top: -4rem; left: 0.5rem; background-color: rgb(246,246,246); padding: 0 0.2rem 0 0.2rem; color: rgb(120,120,120)"
				>{messages['modal']['group.label']}
			</span>
		{/if}

		{#if noneditable}
			<input
				value={searchGroups}
				disabled="true"
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
			/>

			<span
				style="display: inline-flex; font-size: 0.65rem; position: relative; top: -3rem; left: 0.5rem; background-color: rgb(246,246,246); padding: 0 0.2rem 0 0.2rem; color: rgb(120,120,120)"
				>{messages['modal']['group.label']}
			</span>
		{/if}

		{#if topicName}
			<div style="display:flex; align-items: center">
				<fieldset>
					<div style="display:inline">
						<label for="anyApplicationOption1" style="font-size:0.8rem">
							<input
								type="radio"
								style="height:0.8rem; width:0.8rem"
								id="anyApplicationOption1"
								name="anyApplicationCanRead"
								bind:group={anyApplicationCanRead}
								value={false}
								checked
							/>
							{messages['modal']['any.application.can.read.message.one']}</label
						>
					</div>

					<div>
						<label for="anyApplicationOption2" style="font-size:0.8rem">
							<input
								type="radio"
								style="height:0.8rem; width:0.8rem; margin-top: 1rem"
								id="anyApplicationOption2"
								name="anyApplicationCanRead"
								bind:group={anyApplicationCanRead}
								value={true}
							/>
							{messages['modal']['any.application.can.read.message.two']}
						</label>
					</div>
				</fieldset>
			</div>
		{/if}

		{#if adminRoles}
			<div class="admin-roles">
				{#if noneditable}
					<Switch bind:checked={selectedGroupMembership.groupAdmin} />
				{:else}
					<Switch bind:checked={selectedIsGroupAdmin} />
				{/if}
				<h3>{messages['modal']['switch.group.admin.label']}</h3>
			</div>
			<div class="admin-roles">
				{#if noneditable}
					<Switch bind:checked={selectedGroupMembership.topicAdmin} />
				{:else}
					<Switch bind:checked={selectedIsTopicAdmin} />
				{/if}
				<h3>{messages['modal']['switch.topic.admin.label']}</h3>
			</div>
			<div class="admin-roles">
				{#if noneditable}
					<Switch bind:checked={selectedGroupMembership.applicationAdmin} />
				{:else}
					<Switch bind:checked={selectedIsApplicationAdmin} />
				{/if}
				<h3>{messages['modal']['switch.application.admin.label']}</h3>
			</div>
		{/if}

		{#if actionAssociateApplication}
			<textarea
				data-cy="bind-token-input"
				style="margin-top: 0.5rem; margin-bottom: 1.5rem; width: 13.5rem"
				type="search"
				rows="13"
				placeholder={messages['modal']['input.grant.token.placeholder']}
				bind:value={bindToken}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						bindToken = bindToken?.trim();
						document.activeElement.blur();
						if (bindToken?.length > 0) {
							dispatch('addTopicApplicationAssociation', selectedTopicId);
						}
					}
				}}
				on:blur={() => {
					bindToken = bindToken?.trim();
				}}
			/>

			{#if tokenApplicationName !== undefined && tokenApplicationGroup !== undefined}
				<div style="font-size:1rem; margin-top: 1rem">
					<strong>{tokenApplicationName}</strong> ({tokenApplicationGroup})
				</div>
				<div style="font-size:0.8rem; margin-top: 0.5rem">
					from {tokenApplicationEmail}
				</div>
			{/if}
			{#if $errorMessageAssociation}
				<span
					class="error-message"
					style="	top: 21rem; right: 2.3rem"
					class:hidden={$errorMessageAssociation?.length === 0}
				>
					{$errorMessageAssociation}
				</span>
			{/if}
		{/if}
	</div>

	{#if actionAssociateApplicationTwo}
		<div style="margin-left: 2rem">
			<div style="margin-bottom: 1rem">{messages['modal']['select.access.type.label']}:</div>
			<CheckBox label="Read" bind:partitionList={partitionListRead} bind:checked={readChecked} />
		</div>

		<div style="margin: 1rem 0 1rem 2rem">
			<CheckBox label="Write" bind:partitionList={partitionListWrite} bind:checked={writeChecked} />
		</div>
	{/if}

	{#if actionUnsavedPartitions && $nonEmptyInputField}
		{#each $nonEmptyInputField as field}
			<div style="margin-left: 2rem; margin-bottom: 1rem">
				{field}
			</div>
		{/each}

		<button
			class="action-button"
			on:click={() => {
				dispatch('addUnsavedPartitions');
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('addUnsavedPartitions');
				}
			}}
			>{messages['modal']['button.save.changes']}
		</button>
	{/if}

	{#if actionAddUser}
		<span style="font-size:0.7rem; float: right; margin:0 2rem 0.5rem 0"
			>{messages['modal']['required.field.message.one']}</span
		>
		<hr />
		<button
			data-cy="button-add-user"
			class="action-button"
			class:action-button-invalid={invalidEmail || !selectedGroup}
			disabled={invalidEmail || !selectedGroup}
			on:click={() => actionAddUserEvent()}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionAddUserEvent();
				}
			}}
			>{messages['modal']['button.add.user.label']}
		</button>
	{/if}

	{#if actionAddSuperUser}
		<span style="font-size:0.7rem; float: right; margin:0 2rem 0.5rem 0"
			>{messages['modal']['required.field.message.one']}</span
		>
		<hr />
		<button
			data-cy="button-add-super-user"
			class="action-button"
			class:action-button-invalid={invalidEmail || emailValue.length < 10}
			on:click={() => {
				actionAddSuperUserEvent();
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionAddSuperUserEvent();
				}
			}}
			>{messages['modal']['button.add.user.label']}
		</button>
	{/if}

	{#if actionAddTopic}
		<span style="font-size:0.7rem; float: right; margin:0 2rem 0.5rem 0"
			>{messages['modal']['required.field.message.one']}</span
		>
		<hr />
		<button
			data-cy="button-add-topic"
			class="action-button"
			disabled={newTopicName.length < minNameLength || !selectedGroup}
			class:action-button-invalid={newTopicName.length < minNameLength || !selectedGroup}
			on:click={() => actionAddTopicEvent()}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionAddTopicEvent();
				}
			}}
		>
			{messages['modal']['button.add.topic.label']}
		</button>
	{/if}

	{#if actionAddTopicSet}
		<span style="font-size:0.7rem; float: right; margin:0 2rem 0.5rem 0"
			>{messages['modal']['required.field.message.three']}</span
		>
		<hr />
		<button
			data-cy="button-add-topic"
			class="action-button"
			disabled={newTopicSetName.length < minNameLength || !selectedGroup}
			class:action-button-invalid={newTopicSetName.length < minNameLength || !selectedGroup}
			on:click={() => actionAddTopicSetEvent()}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionAddTopicSetEvent();
				}
			}}
		>
			{messages['modal']['button.add.topic-set.label']}
		</button>
	{/if}

	{#if actionEditTopicSet}
		<span style="font-size:0.7rem; float: right; margin:0 2rem 0.5rem 0"
			>{messages['modal']['required.field.message.three']}</span
		>
		<hr />
		<button
			data-cy="button-add-topic"
			class="action-button"
			disabled={newTopicSetName.length < minNameLength || originalTopicSetName === newTopicSetName}
			class:action-button-invalid={newTopicSetName.length < minNameLength}
			on:click={() => actionAddTopicSetEvent()}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionAddTopicSetEvent();
				}
			}}
		>
			{messages['modal']['button.edit.topic-set.label']}
		</button>
	{/if}

	{#if actionAddApplication}
		<span style="font-size:0.7rem; float: right; margin:0 2rem 0.5rem 0"
			>{messages['modal']['required.field.message.three']}</span
		>
		<hr />
		<button
			data-cy="button-add-application"
			class="action-button"
			class:action-button-invalid={appName?.length < minNameLength || !selectedGroup}
			disabled={appName?.length < minNameLength || !selectedGroup}
			on:click={() => {
				actionAddApplicationEvent();
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionAddApplicationEvent();
				}
			}}
			>{messages['modal']['button.add.application.label']}
		</button>
	{/if}

	{#if actionAddGroup}
		<span style="font-size:0.7rem; float: right; margin:0 2rem 0.5rem 0"
			>{messages['modal']['required.field.message.three']}</span
		>
		<hr />
		<button
			data-cy="button-add-group"
			class="action-button"
			class:action-button-invalid={newGroupName?.length < minNameLength}
			disabled={newGroupName?.length < minNameLength}
			on:click={() => {
				if (!newGroupName.length <= minNameLength) actionAddGroupEvent();
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					if (!newGroupName.length <= minNameLength) actionAddGroupEvent();
				}
			}}
		>
			{messages['modal']['button.add.group.label']}
		</button>
	{/if}

	{#if actionEditUser}
		<hr style="z-index: 1" />
		<button
			data-cy="save-edit-user"
			class="action-button"
			on:click={() => {
				actionEditUserEvent();
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionEditUserEvent();
				}
			}}
			>{messages['modal']['button.save.changes']}
		</button>
	{/if}

	{#if actionEditApplication}
		<hr style="z-index: 1" />
		<button
			data-cy="save-application"
			class="action-button"
			class:action-button-invalid={newAppName?.length < minNameLength}
			disabled={newAppName?.length < minNameLength}
			on:click={() => {
				dispatch('saveNewApp', {
					newAppName: newAppName,
					newAppDescription: newAppDescription,
					newAppPublic: newAppPublic
				});
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('saveNewApp', {
						newAppName: newAppName,
						newAppDescription: newAppDescription,
						newAppPublic: newAppPublic
					});
				}
			}}
			>{messages['modal']['button.save.changes']}
		</button>
	{/if}

	{#if actionEditTopic}
		<hr style="z-index: 1" />
		<button
			data-cy="save-application"
			class="action-button"
			class:action-button-invalid={topicCurrentName?.length < minNameLength}
			disabled={topicCurrentName?.length < minNameLength}
			on:click={() => {
				topicCurrentName = topicCurrentName.trim();
				invalidTopicName = !validateNameLength(topicCurrentName, 'topic');
				if (!invalidTopicName)
					dispatch('saveNewTopic', {
						newTopicName: topicCurrentName,
						newTopicDescription: topicCurrentDescription,
						newTopicPublic: topicCurrentPublic
					});
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					topicCurrentName = topicCurrentName.trim();
					invalidTopicName = !validateNameLength(topicCurrentName, 'topic');
					if (!invalidTopicName)
						dispatch('saveNewTopic', {
							newTopicName: topicCurrentName,
							newTopicDescription: topicCurrentDescription,
							newTopicPublic: topicCurrentPublic
						});
				}
			}}
			>{messages['modal']['button.save.changes']}
		</button>
	{/if}

	{#if actionEditGroup}
		<div style="text-align: right; margin-right: 2rem; font-size:0.7rem">
			{messages['modal']['required.field.message.three']}
		</div>
		<hr style="z-index: 1" />
		<button
			data-cy="edit-group"
			class="action-button"
			class:action-button-invalid={newGroupName?.length < minNameLength}
			disabled={newGroupName?.length < minNameLength}
			on:click={() => {
				if (
					newGroupName !== groupCurrentName ||
					newGroupDescription !== groupCurrentDescription ||
					newGroupPublic !== groupCurrentPublic
				)
					actionAddGroupEvent();
				else dispatch('cancel');
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					if (
						newGroupName !== groupCurrentName ||
						newGroupDescription !== groupCurrentDescription ||
						newGroupPublic !== groupCurrentPublic
					)
						actionAddGroupEvent();
					else dispatch('cancel');
				}
			}}
			>{messages['modal']['button.save.changes']}
		</button>
	{/if}

	{#if actionDeleteUsers}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['delete.warning']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="delete-user"
			autofocus
			class="action-button"
			on:click={() => dispatch('deleteGroupMemberships')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('deleteGroupMemberships');
				}
			}}>{title}</button
		>
	{/if}

	{#if actionTopicChange}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['topic.update.message']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="refresh-page"
			autofocus
			class="action-button"
			on:click={() => dispatch('reloadContent')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('reloadContent');
				}
			}}>Reload topic</button
		>
	{/if}

	{#if actionApplicationChange}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['application.update.message']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="refresh-page"
			autofocus
			class="action-button"
			on:click={() => dispatch('reloadContent')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('reloadContent');
				}
			}}>Reload content</button
		>
	{/if}

	{#if actionDeleteSuperUsers}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['delete.warning']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="delete-super-user"
			autofocus
			class="action-button"
			on:click={() => dispatch('deleteSuperUsers')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('deleteSuperUsers');
				}
			}}>{title}</button
		>
	{/if}

	{#if actionDeleteTopics}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['delete.warning']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="delete-topic"
			autofocus
			class="action-button"
			on:click={() => dispatch('deleteTopics')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('deleteTopics');
				}
			}}>{title}</button
		>
	{/if}

	{#if actionDeleteTopicsFromTopicSet}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['delete.message']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="delete-topic"
			autofocus
			class="action-button"
			on:click={() => dispatch('deleteTopics')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('deleteTopics');
				}
			}}>{title}</button
		>
	{/if}

	{#if actionDeleteGrants}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['delete.warning']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="delete-grant"
			autofocus
			class="action-button"
			on:click={() => dispatch('deleteGrants')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('deleteGrants');
				}
			}}>{title}</button
		>
	{/if}

	{#if actionDeleteApplications}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['delete.warning']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			data-cy="delete-application"
			autofocus
			class="action-button"
			on:click={() => dispatch('deleteApplications')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('deleteApplications');
				}
			}}>{title}</button
		>
	{/if}

	{#if actionDeleteGroups}
		<p style="margin: 0 1.7rem 1rem 2rem; font-size:0.9rem; font-stretch: condensed; ">
			{messages['modal']['delete.warning']}
		</p>
		<!-- svelte-ignore a11y-autofocus -->
		<button
			autofocus
			data-cy="delete-group"
			class="action-button"
			on:click={() => dispatch('deleteGroups')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('deleteGroups');
				}
			}}>{title}</button
		>
	{/if}

	{#if actionAssociateApplication}
		<!-- svelte-ignore a11y-autofocus -->
		<button
			autofocus
			data-cy="add-application-topic-association"
			class="action-button"
			disabled={bindToken === undefined || accessTypeSelection?.length === 0 || invalidToken}
			class:button-disabled={bindToken === undefined ||
				accessTypeSelection?.length === 0 ||
				invalidToken}
			on:click={() => {
				actionAssociateApplication = false;
				actionAssociateApplicationTwo = true;
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					actionAssociateApplication = false;
					actionAssociateApplicationTwo = true;
				}
			}}
		>
			{messages['modal']['associate.application.button.label']}
		</button>
	{/if}

	{#if actionAssociateApplicationTwo}
		<!-- svelte-ignore a11y-autofocus -->
		<button
			autofocus
			data-cy="add-application-topic-association"
			class="action-button"
			disabled={(title !== messages['topic.detail']['edit.grant'] && bindToken === undefined) ||
				(!readChecked && !writeChecked) ||
				invalidToken}
			class:button-disabled={(title !== messages['topic.detail']['edit.grant'] &&
				bindToken === undefined) ||
				(!readChecked && !writeChecked) ||
				invalidToken}
			on:click={() => {
				dispatch('addTopicApplicationAssociation', {
					bindToken: bindToken,
					partitionListRead: partitionListRead,
					partitionListWrite: partitionListWrite,
					read: readChecked,
					write: writeChecked
				});
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('addTopicApplicationAssociation', {
						bindToken: bindToken,
						partitionListRead: partitionListRead,
						partitionListWrite: partitionListWrite,
						read: readChecked,
						write: writeChecked
					});
				}
			}}
		>
			{#if title === messages['topic.detail']['edit.grant']}
				{messages['modal']['associate.application.button.label.two.edit']}
			{:else}
				{messages['modal']['associate.application.button.label.two']}
			{/if}
		</button>
	{/if}

	{#if reminderMsg}
		<!-- svelte-ignore a11y-autofocus -->
		<button
			autofocus
			class="action-button"
			on:click={() => dispatch('extendSession')}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					dispatch('extendSession');
				}
			}}>{messages['modal']['reminder.message.button.label']}</button
		>
	{/if}

	<!-- svelte-ignore a11y-autofocus -->
	<button
		autofocus={errorMsg}
		class="action-button"
		on:click={() => {
			emailValue = '';
			closeModal();
		}}
		on:keydown={(event) => {
			if (event.which === returnKey) {
				emailValue = '';
				closeModal();
			}
		}}
		>{closeModalText}
	</button>
</div>

<style>
	.action-button {
		float: right;
		margin: 0.8rem 1.5rem 1rem 0;
		background-color: transparent;
		border-width: 0;
		font-size: 0.85rem;
		font-weight: 500;
		color: #6750a4;
		cursor: pointer;
	}

	.action-button:focus {
		outline: 0;
	}

	.action-button-invalid {
		color: grey;
		cursor: default;
	}

	.admin-roles {
		display: flex;
		align-items: center;
		width: 14rem;
	}

	.admin-roles:first-child {
		margin-top: 1.5rem;
	}

	.admin-roles h3 {
		margin-left: 1.5rem;
		text-indent: -0.2rem;
	}

	.modal-backdrop {
		position: fixed;
		top: 0;
		left: 0;
		width: 100%;
		height: 100vh;
		background: rgba(0, 0, 0, 0.25);
		z-index: 10;
	}

	.modal {
		position: fixed;
		top: 10vh;
		left: 50%;
		transform: translateX(-50%);
		width: 18.6rem;
		max-height: 90vh;
		background: rgb(246, 246, 246);
		border-radius: 15px;
		z-index: 100;
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.26);
		overflow-y: auto;
	}

	.content {
		padding-bottom: 1rem;
		margin-top: 1rem;
		margin-left: 2.3rem;
		width: 14rem;
	}

	.content input {
		width: 11rem;
		border-width: 1px;
	}

	.error-message {
		font-size: 0.7rem;
	}

	img.close-button {
		transform: scale(0.25, 0.35);
	}

	.close-button {
		background-color: transparent;
		position: absolute;
		padding-right: 1rem;
		color: black;
		border: none;
		height: 50px;
		width: 60px;
		border-radius: 0%;
		cursor: pointer;
	}

	h2 {
		margin-top: 3rem;
		margin-left: 2rem;
	}

	hr {
		width: 79%;
		border-color: rgba(0, 0, 0, 0.07);
	}

	.button-disabled {
		cursor: default;
	}

	.condensed {
		font-stretch: extra-condensed;
	}

	.add-topic {
		background: rgb(246, 246, 246);
		width: 13.2rem;
		margin: 2rem 2rem 0 0;
		resize: none;
	}

	.edit-topic {
		background: rgb(246, 246, 246);
		width: 13.6rem;
		margin: 0 2rem 0 0;
		resize: none;
	}
</style>

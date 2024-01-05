<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { fade, fly } from 'svelte/transition';
	import { createEventDispatcher } from 'svelte';
	import { onMount, onDestroy } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import closeSVG from '../../icons/close.svg';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import errorMessageAssociation from '../../stores/errorMessageAssociation';
	import groupContext from '../../stores/groupContext';
	import modalOpen from '../../stores/modalOpen';
	import { convertFromMilliseconds } from '../../utils';
	import grantDurations from '../../stores/grantDurations';

	export let title;

	export let selectedGrant = {};
	export let actionAddGrant = false;
	export let actionEditGrant = false;
	export let newGrantName = '';
	export let duration = 0;
	export let errorDescription = '';
	export let reminderDescription = '';
	export let errorMsg = false;
	export let reminderMsg = false;
	export let closeModalText = messages['modal']['close.modal.label'];

	const dispatch = createEventDispatcher();

	// Constants
	const returnKey = 13;
	const minNameLength = 3;

	// Bind Token
	let bindToken;
	let tokenApplicationName = '';
	let tokenApplicationGroup = '';
	let invalidToken = false;

	// Error Handling
	let invalidName = false;
	let errorMessageDuration = '';
	let errorMessageName = '';

	let selectedGroup;
	let selectedDuration;


	// Bind Token Decode
	$: if (bindToken?.length > 0) {
		const tokenBody = bindToken.substring(bindToken.indexOf('.') + 1, bindToken.lastIndexOf('.'));
		decodeToken(tokenBody);
	} else {
		errorMessageAssociation.set([]);
	}

	onMount(() => {
		modalOpen.set(true);
		getGrantDurations();
		if (actionAddGrant) {
			if ($groupContext) selectedGroup = $groupContext.id;
		}
		if (actionEditGrant) {
			selectedGroup = selectedGrant.groupId;
			newGrantName = selectedGrant.name;
			duration = convertFromMilliseconds(
				selectedGrant.durationInMilliseconds,
				selectedGrant.durationMetadata
			);

			tokenApplicationName = selectedGrant.applicationName;
			tokenApplicationGroup = selectedGrant.groupName;
		}
	});

	onDestroy(() => modalOpen.set(false));

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

	const getGrantDurations = async () => {
		try {
			let res;

			res = await httpAdapter.get(`/grant_durations?group=${$groupContext.id}`);

			grantDurations.set(res.data.content || []);

			if (actionAddGrant) {
				selectedDuration = $grantDurations[0];
			} else {
				const selectedDurationFromStore = $grantDurations.find(
					(duration) =>
						duration.durationInMilliseconds === selectedGrant.durationInMilliseconds &&
						duration.durationMetadata === selectedGrant.durationMetadata
				);
				selectedDuration = selectedDurationFromStore;
			}
		} catch (err) {
			console.log('err', err);
		}
	};

	const actionAddGrantEvent = async () => {
		let newGrant = {
			name: newGrantName,
			grantDurationId: selectedDuration.id,
			applicationGrantToken: bindToken,
			groupId: selectedGroup
		};

		invalidName = !validateNameLength(newGrantName, 'grants');
		if (invalidName) {
			errorMessageName = errorMessages['grants']['name.cannot_be_less_than_three_characters'];
			return;
		}

		dispatch('addGrant', newGrant);
		closeModal();
	};

	const actionEditGrantEvent = async () => {
		let updatedGrantDuration = {
			name: newGrantName,
			grantDurationId: selectedDuration.id
		};

		invalidName = !validateNameLength(newGrantName, 'grants');
		if (invalidName) {
			errorMessageName = errorMessages['durations']['name.cannot_be_less_than_three_characters'];
			return;
		}

		dispatch('editGrant', updatedGrantDuration);
		closeModal();
	};

	const decodeToken = async (token) => {
		let res = atob(token);

		try {
			res = JSON.parse(res);
			invalidToken = false;
			errorMessageAssociation.set([]);
		} catch (err) {
			errorMessageAssociation.set(errorMessages['bind_token']['invalid']);
			invalidToken = true;
		}

		tokenApplicationName = res.appName;
		tokenApplicationGroup = res.groupName;
	};

	const getDuration = (grantDuration = {}) => {
		const duration = convertFromMilliseconds(
			grantDuration.durationInMilliseconds,
			grantDuration.durationMetadata
		);
		const durationType =
			duration > 1 ? grantDuration.durationMetadata + 's' : grantDuration.durationMetadata;
		return `${duration} ${durationType}`;
	};
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<div class="modal-backdrop" on:click={closeModal} transition:fade />
<div class="modal" transition:fly={{ y: 300 }}>
	<!-- svelte-ignore a11y-click-events-have-key-events -->
	<img src={closeSVG} alt="close" class="close-button" on:click={closeModal} />
	<h2 class:condensed={title?.length > 25}>{title}</h2>
	<hr style="width:80%" />
	<div class="content">
		{#if errorMsg}
			<p>{errorDescription}</p>
		{/if}

		{#if reminderMsg}
			<p>{reminderDescription}</p>
		{/if}

		<!-- svelte-ignore a11y-autofocus -->
		<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
				>Grant Name:</span
			>
			<input
				id="name"
				data-cy="grant-name"
				autofocus
				class:invalid={invalidName}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
				bind:value={newGrantName}
				on:blur={() => {
					newGrantName = newGrantName.trim();
				}}
				on:keydown={(event) => {
					errorMessageName = '';

					if (event.which === returnKey) {
						newGrantName = newGrantName.trim();
					}
				}}
				on:click={() => {
					errorMessageDuration = '';
					errorMessageName = '';
				}}
			/>
		</div>
		{#if actionAddGrant}
			<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
				<span
					style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
					>Group:</span
				>
				<input
					data-cy="group-name"
					id="group-context"
					readonly
					disabled
					style="background: rgb(246, 246, 246); width: 13.2rem;"
					bind:value={$groupContext.name}
				/>
			</div>
		{/if}

		{#if actionEditGrant}
			<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
				<span
					style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
					>Group:</span
				>
				<input
					data-cy="group-name"
					id="group-context"
					readonly
					disabled
					style="background: rgb(246, 246, 246); width: 13.2rem;"
					value={selectedGrant.groupName}
				/>
			</div>
		{/if}
		{#if actionAddGrant}
			<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
				<span
					style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
					>Grant Token:</span
				>
				<textarea
					data-cy="bind-token-input"
					style="margin-top: 0.5rem; margin-bottom: 0.5rem; width: 13.5rem"
					type="search"
					rows="8"
					placeholder={messages['modal']['input.grant.token.placeholder']}
					bind:value={bindToken}
					on:blur={() => {
						bindToken = bindToken?.trim();
					}}
				/>
				{#if $errorMessageAssociation}
					<span
						class="error-message"
						style="	top: 23rem; right: 6.3rem"
						class:hidden={$errorMessageAssociation?.length === 0}
					>
						{$errorMessageAssociation}
					</span>
				{/if}
			</div>
		{/if}

		<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
				>Application Name:</span
			>
			<input
				data-cy="application-name"
				id="group-context"
				readonly
				disabled
				style="background: rgb(246, 246, 246); width: 13.2rem;"
				value={tokenApplicationName || ''}
			/>
		</div>

		<div style="font-size: 1rem; margin: 1.3rem 0 1.1rem 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
				>Application Group:</span
			>
			<input
				data-cy="application-group"
				id="group-context"
				readonly
				disabled
				style="background: rgb(246, 246, 246); width: 13.2rem;"
				value={tokenApplicationGroup || ''}
			/>
		</div>

		<hr />

		<div style="font-size: 1rem; margin: 1.3rem 0 0 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
				>Grant Duration:</span
			>
			<div class="dropdown">
				<select
					bind:value={selectedDuration}
					style="background: rgb(246, 246, 246); width: 13.9rem; border-radius: 0; margin-left: 0"
				>
					{#each $grantDurations as value}<option {value}>{value.name}</option>{/each}
				</select>
			</div>
		</div>

		<div style="font-size: 1rem; margin: 1.3rem 0 1.1rem 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 11rem; text-align: right"
				>Selected Duration:</span
			>

			<input
				data-cy="application-group"
				id="group-context"
				readonly
				disabled
				style="background: rgb(246, 246, 246); width: 13.2rem;"
				value={selectedDuration ? getDuration(selectedDuration) : 'Select Above'}
			/>
		</div>

		<hr />
		{#if actionAddGrant}
			<button
				data-cy="button-add-grant"
				class="action-button"
				disabled={newGrantName.length < minNameLength ||
					!selectedGroup ||
					!bindToken ||
					$errorMessageAssociation.length > 0}
				class:action-button-invalid={newGrantName.length < minNameLength ||
					!bindToken ||
					$errorMessageAssociation.length > 0}
				on:click={() => actionAddGrantEvent()}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						actionAddGrantEvent();
					}
				}}
			>
				Add Grant
			</button>
		{/if}

		{#if actionEditGrant}
			<button
				data-cy="button-edit-grant"
				class="action-button"
				disabled={newGrantName.length < minNameLength || !duration}
				class:action-button-invalid={newGrantName.length < minNameLength || !duration}
				on:click={() => actionEditGrantEvent()}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						actionEditGrantEvent();
					}
				}}
			>
				Update Grant
			</button>
		{/if}

		{#if errorMessageDuration?.substring(0, errorMessageDuration?.indexOf(' ')) === messages['modal']['error.message.topic.substring'] && errorMessageDuration?.length > 0}
			<span
				class="error-message"
				style="	top: 10.5rem; right: 2.2rem"
				class:hidden={errorMessageDuration?.length === 0}
			>
				{errorMessageDuration}
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

		{#if errorMessageName?.substring(0, errorMessageName?.indexOf(' ')) === messages['modal']['error.message.application.substring'] && errorMessageName?.length > 0}
			<span
				class="error-message"
				style="	top: 10.5rem; right: 1.4rem"
				class:hidden={errorMessageName?.length === 0}
			>
				{errorMessageName}
			</span>
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
				closeModal();
			}}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					closeModal();
				}
			}}
			>{closeModalText}
		</button>
	</div>
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

	.action-button-invalid {
		color: grey;
		cursor: default;
	}

	.action-button:focus {
		outline: 0;
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
		width: 35rem;
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
		width: 29rem;
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
		/* width: 79%; */
		border-color: rgba(0, 0, 0, 0.07);
	}

	.condensed {
		font-stretch: extra-condensed;
	}
</style>

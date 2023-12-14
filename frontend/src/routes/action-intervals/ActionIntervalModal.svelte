<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { fade, fly } from 'svelte/transition';
	import { createEventDispatcher } from 'svelte';
	import { onMount, onDestroy } from 'svelte';
	import { DateInput } from 'date-picker-svelte';
	import closeSVG from '../../icons/close.svg';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import errorMessageAssociation from '../../stores/errorMessageAssociation';
	import groupContext from '../../stores/groupContext';
	import modalOpen from '../../stores/modalOpen';
	import AdminDetails from '../../lib/AdminDetails.svelte';

	export let title;

	export let selectedActionInterval = {};
	export let actionAddActionInterval = false;
	export let actionEditActionInterval = false;
	export let newActionIntervalName = '';
	export let errorDescription = '';
	export let reminderDescription = '';
	export let errorMsg = false;
	export let reminderMsg = false;
	export let closeModalText = messages['modal']['close.modal.label'];

	let startDate = new Date();
	let endDate = new Date();

	const dispatch = createEventDispatcher();

	// Constants
	const returnKey = 13;
	const minNameLength = 3;

	// Bind Token
	let bindToken;
	let tokenApplicationName, tokenApplicationGroup, tokenApplicationEmail;
	let invalidToken = false;

	// Error Handling
	let invalidName = false;
	let errorMessageActionInterval = '';
	let errorMessageName = '';

	// SearchBox
	let selectedGroup;

	// Bind Token Decode
	$: if (bindToken?.length > 0) {
		const tokenBody = bindToken.substring(bindToken.indexOf('.') + 1, bindToken.lastIndexOf('.'));
		decodeToken(tokenBody);
	} else {
		errorMessageAssociation.set([]);
	}

	onMount(() => {
		modalOpen.set(true);
		if (actionAddActionInterval) {
			if ($groupContext) selectedGroup = $groupContext.id;
		}
		if (actionEditActionInterval) {
			newActionIntervalName = selectedActionInterval.name;
			startDate = new Date(selectedActionInterval.startDate);
			endDate = new Date(selectedActionInterval.endDate);
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

	const actionAddActionIntervalEvent = async () => {
		let newActionInterval = {
			name: newActionIntervalName,
			groupId: selectedGroup,
			startDate: startDate,
			endDate: endDate
		};

		invalidName = !validateNameLength(newActionIntervalName, 'actionIntervals');
		if (invalidName) {
			errorMessageName =
				errorMessages['action-intervals']['name.cannot_be_less_than_three_characters'];
			return;
		}

		dispatch('addActionInterval', newActionInterval);
		closeModal();
	};

	const actionEditActionIntervalEvent = async () => {
		let updatedActionInterval = {
			...selectedActionInterval,
			name: newActionIntervalName,
			startDate: startDate,
			endDate: endDate
		};

		invalidName = !validateNameLength(newActionIntervalName, 'actionIntervals');
		if (invalidName) {
			errorMessageName =
				errorMessages['action-intervals']['name.cannot_be_less_than_three_characters'];
			return;
		}

		dispatch('editActionInterval', updatedActionInterval);
		closeModal();
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

		<!-- svelte-ignore a11y-autofocus -->
		<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 9.2rem; text-align: right"
				>Action Interval Name:</span
			>
			<input
				id="name"
				data-cy="action-interval-name-input"
				autofocus
				class:invalid={invalidName}
				style="background: rgb(246, 246, 246); width: 13.2rem; margin-right: 2rem"
				bind:value={newActionIntervalName}
				on:blur={() => {
					newActionIntervalName = newActionIntervalName.trim();
				}}
				on:keydown={(event) => {
					errorMessageName = '';

					if (event.which === returnKey) {
						newActionIntervalName = newActionIntervalName.trim();
					}
				}}
				on:click={() => {
					errorMessageActionInterval = '';
					errorMessageName = '';
				}}
			/>
		</div>
		{#if actionAddActionInterval}
			<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
				<span
					style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 9.2rem; text-align: right"
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

		{#if actionEditActionInterval}
			<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
				<span
					style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 9.2rem; text-align: right"
					>Group:</span
				>
				<input
					data-cy="group-name"
					id="group-context"
					readonly
					disabled
					style="background: rgb(246, 246, 246); width: 13.2rem;"
					value={selectedActionInterval.groupName}
				/>
			</div>
		{/if}

		<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem;padding-right: 1rem; min-width: 9.2rem; text-align: right"
				>Start Date:</span
			>

			<DateInput format="yyyy-MM-dd" closeOnSelection bind:value={startDate} max={new Date(2050, 12, 31)}/>
		</div>

		<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem;padding-right: 1rem; min-width: 9.2rem; text-align: right"
				>End Date:</span
			>

			<DateInput format="yyyy-MM-dd" closeOnSelection bind:value={endDate}  max={new Date(2050, 12, 31)}/>
		</div>

		<div style="font-size: 1rem; margin: 1.1rem 0 0 0.2rem; width: fit-content; display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem;padding-right: 1rem; min-width: 9.2rem; text-align: right"
				>Admins:</span
			>
			{#if selectedActionInterval.groupId}
				<table>
					<tr>
						<td style="border-bottom: none;">
							<AdminDetails
								groupId={selectedActionInterval.groupId}
								adminCategory="topic"
							/>
						</td>
					</tr>
				</table>
			{/if}
		</div>

		<hr />

		{#if actionAddActionInterval}
			<!-- content here -->
			<button
				data-cy="button-add-action-interval"
				class="action-button"
				disabled={newActionIntervalName.length < minNameLength || !selectedGroup}
				class:action-button-invalid={newActionIntervalName.length < minNameLength}
				on:click={() => actionAddActionIntervalEvent()}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						actionAddActionIntervalEvent();
					}
				}}
			>
				Add Action Interval
			</button>
		{/if}

		{#if actionEditActionInterval}
			<button
				data-cy="button-add-action-interval"
				class="action-button"
				disabled={newActionIntervalName.length < minNameLength}
				class:action-button-invalid={newActionIntervalName.length < minNameLength}
				on:click={() => actionEditActionIntervalEvent()}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						actionEditActionIntervalEvent();
					}
				}}
			>
				Update
			</button>
		{/if}

		{#if errorMessageActionInterval?.substring(0, errorMessageActionInterval?.indexOf(' ')) === messages['modal']['error.message.topic.substring'] && errorMessageActionInterval?.length > 0}
			<span
				class="error-message"
				style="	top: 10.5rem; right: 2.2rem"
				class:hidden={errorMessageActionInterval?.length === 0}
			>
				{errorMessageActionInterval}
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
		width: 33rem;
		max-height: 90vh;
		background: rgb(246, 246, 246);
		border-radius: 15px;
		z-index: 100;
		box-shadow: 0 2px 8px rgba(0, 0, 0, 0.26);
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
		width: 79%;
		border-color: rgba(0, 0, 0, 0.07);
	}

	.condensed {
		font-stretch: extra-condensed;
	}
	:root {
		--date-input-width: 14rem;
	}
</style>

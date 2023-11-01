<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import Select from 'svelte-select';
	import { fade, fly } from 'svelte/transition';
	import { createEventDispatcher } from 'svelte';
	import { onMount, onDestroy } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import closeSVG from '../../icons/close.svg';
	import messages from '$lib/messages.json';
	import modalOpen from '../../stores/modalOpen';
	import actionIntervalsStore from '../../stores/actionIntervals';
	import topicSetsDetails from '../../stores/topicSetsDetails';
	import SearchIcon from './SearchIcon.svelte';
	import deleteSVG from '../../icons/delete.svg';
	import addSVG from '../../icons/add.svg';

	export let title;

	export let selectedGrant = {};
	export let selectedAction = {};
	export let actionAddAction = false;
	export let actionEditAction = false;
	export let isPublishAction = false;
	export let errorMsg = false;
	export let closeModalText = messages['modal']['close.modal.label'];

	const minCharactersToTriggerSearch = 3;

	// Editing
	let topicSearchString = '';
	let topicSetSearchString = '';

	let topicsRowsSelectedTrue = false;
	let topicsAllRowsSelectedTrue = false;
	let topicsRowsSelected = [];

	let topicSetsRowsSelectedTrue = false;
	let topicSetsAllRowsSelectedTrue = false;
	let topicSetsRowsSelected = [];

	let partitionsRowsSelectedTrue = false;
	let partitionsAllRowsSelectedTrue = false;
	let partitionsRowSelected = [];

	let selectedTopic = '';
	let selectedTopicSet = '';

	const dispatch = createEventDispatcher();

	// Constants
	const returnKey = 13;

	let partition = '';
	let selectedActionInterval;
	let selectedTopicSets = [];
	let selectedTopics = [];
	let selectedPartitions = [];

	onMount(() => {
		modalOpen.set(true);
		getActionIntervals();
		if (actionEditAction) {
			selectedTopicSets = selectedAction.topicSets || [];
			selectedTopics = selectedAction.topics || [];
			selectedPartitions = selectedAction.partitions || [];
		}
	});

	onDestroy(() => modalOpen.set(false));

	function closeModal() {
		dispatch('cancel');
	}

	const getActionIntervals = async () => {
		try {
			let res;

			res = await httpAdapter.get(`/action_intervals`);

			actionIntervalsStore.set(res.data.content);

			if (actionAddAction) {
				selectedActionInterval = $actionIntervalsStore[0];
			} else {
				const selectedActionIntervalFromStore = $actionIntervalsStore.find(
					(actionInterval) => actionInterval.id === selectedAction.actionIntervalId
				);
				selectedActionInterval = selectedActionIntervalFromStore;
			}
		} catch (err) {
			console.error(err);
		}
	};

	const loadTopicOptions = async (filterText) => {
		if (!filterText.length || filterText.length < minCharactersToTriggerSearch)
			return Promise.resolve([]);
		try {
			const topicsResponse = await httpAdapter.get(
				`/topics?filter=${topicSearchString}&group=${selectedGrant.groupId}`
			);

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
		} catch (err) {
			console.error(err);
		}
	};

	const loadTopicSetOptions = async (filterText) => {
		if (!filterText.length || filterText.length < minCharactersToTriggerSearch)
			return Promise.resolve([]);
		try {
			const topicsResponse = await httpAdapter.get(
				`/topic-sets?filter=${topicSearchString}&group=${selectedGrant.groupId}`
			);

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
		} catch (err) {
			console.err(err);
		}
	};

	const onTopicSelect = () => {
		const topicToAdd = {
			name: selectedTopic.value,
			id: selectedTopic.id,
			groupId: selectedTopic.groupId
		};

		const newTopics = [...selectedTopics];
		newTopics.push(topicToAdd);
		selectedTopics = newTopics;
		selectedTopic = '';
	};

	const onTopicSetSelect = () => {
		const topicSetToAdd = {
			name: selectedTopicSet.value,
			id: selectedTopicSet.id,
			groupId: selectedTopicSet.groupId
		};

		const newTopicSets = [...selectedTopicSets];
		newTopicSets.push(topicSetToAdd);
		selectedTopicSets = newTopicSets;
		selectedTopicSet = '';
	};

	const actionAddActionEvent = async () => {
		let newAction = {
			actionIntervalId: selectedActionInterval.id,
			partitions: selectedPartitions,
			topicSetIds: selectedTopicSets.map((topicSet) => topicSet.id),
			topicIds: selectedTopics.map((topic) => topic.id),
			applicationGrantId: selectedGrant.id,
			publishAction: isPublishAction
		};

		dispatch('addAction', newAction);
		closeModal();
	};

	const actionEditActionEvent = async () => {
		let updatedGrantDuration = {
			id: selectedAction.id,
			actionIntervalId: selectedActionInterval.id,
			partitions: selectedPartitions,
			topicSetIds: selectedTopicSets.map((topicSet) => topicSet.id),
			topicIds: selectedTopics.map((topic) => topic.id),
			applicationGrantId: selectedGrant.id,
			publishAction: isPublishAction
		};

		dispatch('editAction', updatedGrantDuration);
		closeModal();
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
		<div style="display: flex">
			<span
				style="font-weight: 300; vertical-align: 1.12rem;  line-height: 2rem; padding-right: 1rem; min-width: 8rem; text-align: right"
				>Action Interval:</span
			>
			<div class="dropdown">
				<select bind:value={selectedActionInterval} class="dropdown-select">
					{#each $actionIntervalsStore as value}<option {value}>{value.name}</option>{/each}
				</select>
			</div>
		</div>

		<div class="topic-and-partition-wrapper">
			<div style="min-width: 17rem; padding: 0 1rem; flex: 1">
				<h1>Topic Sets</h1>
				<div class="search-delete-container">
					<div class="search-wrapper">
						<Select
							type="search"
							loadOptions={loadTopicSetOptions}
							bind:filterText={topicSetSearchString}
							on:change={onTopicSetSelect}
							placeholder="Search and add a topic set"
							bind:value={selectedTopicSet}
							--border-radius="0"
							--border="1px solid #555"
						>
							<div slot="prepend" style="padding: 3px 10px 0 0">
								<SearchIcon />
							</div>
						</Select>
					</div>
				</div>
				{#if selectedTopicSets?.length}
					<table data-cy="topics-table" class="main" style="margin-top: 1rem; min-width: 17rem">
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								<td style="line-height: 1rem;">
									<input
										tabindex="-1"
										type="checkbox"
										class="topics-checkbox"
										style="margin-right: 0.5rem"
										bind:indeterminate={topicSetsRowsSelectedTrue}
										on:click={(e) => {
											if (e.target.checked) {
												topicSetsRowsSelected = selectedTopicSets;
												topicSetsRowsSelectedTrue = false;
												topicSetsAllRowsSelectedTrue = true;
											} else {
												topicSetsAllRowsSelectedTrue = false;
												topicSetsRowsSelectedTrue = false;
												topicSetsRowsSelected = [];
											}
										}}
										checked={topicSetsAllRowsSelectedTrue}
									/>
								</td>

								<td class="header-column" style="min-width: 7rem"
									>{messages['topic-sets.detail']['table.column.one']}</td
								>
								<td>
									<img
										src={deleteSVG}
										alt="options"
										class="dot"
										class:button-disabled={topicSetsRowsSelected.length === 0}
										style="margin-left: 0.5rem;"
										on:click={() => {
											selectedTopicSets = selectedTopicSets.filter(
												(ts) => !topicSetsRowsSelected.includes(ts)
											);
											topicSetsAllRowsSelectedTrue = false;
											topicSetsRowsSelectedTrue = false;
											topicSetsRowsSelected = [];
										}}
										on:keydown={(event) => {
											if (event.which === returnKey) {
												selectedTopicSets = selectedTopicSets.filter(
													(ts) => !topicSetsRowsSelected.includes(ts)
												);
												topicSetsAllRowsSelectedTrue = false;
												topicSetsRowsSelectedTrue = false;
												topicSetsRowsSelected = [];
											}
										}}
									/>
								</td>
							</tr>
						</thead>
						<tbody>
							{#each selectedTopicSets as topicSet}
								<tr>
									<td style="line-height: 1rem; width: 2rem; ">
										<input
											tabindex="-1"
											type="checkbox"
											class="topics-checkbox"
											checked={topicSetsAllRowsSelectedTrue ||
												topicSetsRowsSelected.includes(topicSet)}
											on:change={(e) => {
												if (e.target.checked === true) {
													topicSetsRowsSelected.push(topicSet);
													// reactive statement
													topicSetsRowsSelected = topicSetsRowsSelected;
													topicSetsRowsSelectedTrue = true;
												} else {
													topicSetsRowsSelected = topicSetsRowsSelected.filter(
														(selection) => selection !== topicSet
													);
													if (topicSetsRowsSelected.length === 0) {
														topicSetsRowsSelectedTrue = false;
													}
												}
											}}
										/>
									</td>

									<td data-cy="topic" style="width: max-content">{topicSet.name}</td>

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
											on:click={() => {
												selectedTopicSets = selectedTopicSets.filter((ts) => ts !== topicSet);
											}}
										/>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				{:else}
					<div class="no-topics">
						<p>No Topic Sets Found.</p>
					</div>
				{/if}
			</div>

			<div style="min-width: 17rem; padding: 0 1rem; flex: 1">
				<h1>Topics</h1>
				<div class="search-delete-container">
					<div class="search-wrapper">
						<Select
							type="search"
							loadOptions={loadTopicOptions}
							bind:filterText={topicSearchString}
							on:change={onTopicSelect}
							placeholder="Search and add a topic"
							bind:value={selectedTopic}
							--border-radius="0"
							--border="1px solid #555"
						>
							<div slot="prepend" style="padding: 3px 10px 0 0">
								<SearchIcon />
							</div>
						</Select>
					</div>
				</div>
				{#if selectedTopics?.length}
					<table data-cy="topics-table" class="main" style="margin-top: 1rem; min-width: 17rem">
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								<td style="line-height: 1rem;">
									<input
										tabindex="-1"
										type="checkbox"
										class="topics-checkbox"
										style="margin-right: 0.5rem"
										bind:indeterminate={topicsRowsSelectedTrue}
										on:click={(e) => {
											if (e.target.checked) {
												topicsRowsSelected = selectedTopics;
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

								<td class="header-column" style="min-width: 7rem"
									>{messages['topic-sets.detail']['table.column.one']}</td
								>
								<td>
									<img
										src={deleteSVG}
										alt="options"
										class="dot"
										class:button-disabled={topicsRowsSelected.length === 0}
										style="margin-left: 0.5rem;"
										on:click={() => {
											selectedTopics = selectedTopics.filter(
												(ts) => !topicsRowsSelected.includes(ts)
											);
											topicsAllRowsSelectedTrue = false;
											topicsRowsSelectedTrue = false;
											topicsRowsSelected = [];
										}}
										on:keydown={(event) => {
											if (event.which === returnKey) {
												selectedTopics = selectedTopics.filter(
													(ts) => !topicsRowsSelected.includes(ts)
												);
												topicsAllRowsSelectedTrue = false;
												topicsRowsSelectedTrue = false;
												topicsRowsSelected = [];
											}
										}}
									/>
								</td>
							</tr>
						</thead>
						<tbody>
							{#each selectedTopics as topic}
								<tr>
									<td style="line-height: 1rem; width: 2rem; ">
										<input
											tabindex="-1"
											type="checkbox"
											class="topics-checkbox"
											checked={topicsAllRowsSelectedTrue || topicsRowsSelected.includes(topic)}
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

									<td data-cy="topic" style="width: max-content">{topic.name}</td>

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
											on:click={() => {
												selectedTopics = selectedTopics.filter((tpc) => tpc !== topic);
											}}
										/>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				{:else}
					<div class="no-topics">
						<p>No Topics Found.</p>
					</div>
				{/if}
			</div>

			<div style="min-width: 17rem; padding: 0 1rem; flex: 1">
				<h1>Partitions</h1>
				<div class="partition-add-container">
					<div class="partition-input">
						<input type="text" placeholder="Type and Add" bind:value={partition} />
						<div>
							<img
								data-cy="add-duration"
								class:button-disabled={partition.length === 0}
								src={addSVG}
								alt="options"
								class="dot"
								on:click={() => {
									const newPartitions = [...selectedPartitions];
									newPartitions.push(partition);
									selectedPartitions = newPartitions;
									partition = '';
								}}
								on:keydown={(event) => {
									if (event.which === returnKey) {
									}
								}}
							/>
						</div>
					</div>
				</div>
				{#if selectedPartitions?.length}
					<table data-cy="topics-table" class="main" style="margin-top: 1rem; min-width: 17rem">
						<thead>
							<tr style="border-top: 1px solid black; border-bottom: 2px solid">
								<td style="line-height: 1rem;">
									<input
										tabindex="-1"
										type="checkbox"
										class="topics-checkbox"
										style="margin-right: 0.5rem"
										bind:indeterminate={partitionsRowsSelectedTrue}
										on:click={(e) => {
											if (e.target.checked) {
												partitionsRowSelected = selectedPartitions;
												partitionsRowsSelectedTrue = false;
												partitionsAllRowsSelectedTrue = true;
											} else {
												partitionsAllRowsSelectedTrue = false;
												partitionsRowsSelectedTrue = false;
												partitionsRowSelected = [];
											}
										}}
										checked={partitionsAllRowsSelectedTrue}
									/>
								</td>

								<td class="header-column" style="min-width: 7rem">Partition</td>
								<td>
									<img
										src={deleteSVG}
										alt="options"
										class="dot"
										class:button-disabled={partitionsRowSelected.length === 0}
										style="margin-left: 0.5rem;"
										on:click={() => {
											if (partitionsRowSelected.length > 0) {
												selectedPartitions = selectedPartitions.filter(
													(p) => !partitionsRowSelected.includes(p)
												);
												partitionsAllRowsSelectedTrue = false;
												partitionsRowsSelectedTrue = false;
												partitionsRowSelected = [];
											}
										}}
										on:keydown={(event) => {
											if (event.which === returnKey) {
											}
										}}
									/>
								</td>
							</tr>
						</thead>
						<tbody>
							{#each selectedPartitions as partition}
								<tr>
									<td style="line-height: 1rem; width: 2rem; ">
										<input
											tabindex="-1"
											type="checkbox"
											class="topics-checkbox"
											checked={partitionsRowSelected.includes(partition)}
											on:change={(e) => {
												if (e.target.checked === true) {
													partitionsRowSelected.push(partition);
													// reactive statement
													partitionsRowSelected = partitionsRowSelected;
													partitionsRowsSelectedTrue = true;
												} else {
													partitionsRowSelected = partitionsRowSelected.filter(
														(selection) => selection !== partition
													);
													if (partitionsRowSelected.length === 0) {
														partitionsRowsSelectedTrue = false;
													}
												}
											}}
										/>
									</td>

									<td data-cy="topic" style="width: max-content">{partition}</td>

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
											on:click={() => {
												selectedPartitions = selectedPartitions.filter((p) => p !== partition);
											}}
										/>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				{:else}
					<div class="no-topics">
						<p>No Partitions Found.</p>
					</div>
				{/if}
			</div>
		</div>

		{#if actionAddAction}
			<button
				data-cy="button-add-grant"
				class="action-button"
				disabled={false}
				class:action-button-invalid={false}
				on:click={() => actionAddActionEvent()}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						actionAddActionEvent();
					}
				}}
			>
				Add
			</button>
		{/if}

		{#if actionEditAction}
			<button
				data-cy="button-edit-grant"
				class="action-button"
				disabled={!selectedActionInterval}
				class:action-button-invalid={!selectedActionInterval}
				on:click={() => actionEditActionEvent()}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						actionEditActionEvent();
					}
				}}
			>
				Update
			</button>
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
	.topic-and-partition-wrapper {
		display: flex;
		flex-direction: row;
		margin-top: 1rem;
		margin-bottom: 2rem;
	}
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
		width: 65rem;
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
		margin: 2.3rem;
		width: 90%;
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

	.partition-input {
		display: flex;
		flex-direction: row;
		justify-content: space-between;
		align-items: center;
		width: 100%;
	}

	.partition-input > input {
		width: 85%;
		height: 1.5rem;
		border: 1px solid #555;
		padding: 0.5rem;
		margin-right: 1rem;
	}

	.no-topics {
		margin: 3rem 4rem;
	}

	.dropdown-select {
		border: 1px solid #555;
		border-radius: 0;
		min-width: 13rem;
	}
</style>

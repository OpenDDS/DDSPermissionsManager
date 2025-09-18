<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { fade, fly } from 'svelte/transition';
	import { createEventDispatcher } from 'svelte';
	import { onMount, onDestroy } from 'svelte';
	import closeSVG from '../icons/close.svg';
	import messages from '$lib/messages.json';
	import modalOpen from '../stores/modalOpen';
	import Accordion, { Panel, Header, Content } from '@smui-extra/accordion';
	import List, { Item, Separator, Text } from '@smui/list';

	export let title;

	export let selectedGrant = {};
	export let isPublishAction = false;
	export let errorMsg = false;
	export let closeModalText = messages['modal']['close.modal.label'];

	let allActions = [];
	const dispatch = createEventDispatcher();

	// Constants
	const returnKey = 13;

	onMount(() => {
		if (isPublishAction) {
			allActions = selectedGrant.actions.filter((action) => action.publishAction === true) || [];
		} else {
			allActions = selectedGrant.actions.filter((action) => action.publishAction === false) || [];
		}

		console.log(allActions);

		modalOpen.set(true);
	});

	onDestroy(() => modalOpen.set(false));

	function closeModal() {
		dispatch('cancel');
	}
</script>


<button aria-label="interactive element"  on:click={closeModal}><div class="modal-backdrop icon-button"  transition:fade /></button>
<div class="modal" transition:fly={{ y: 300 }}>
	
	<button aria-label="close"  on:click={closeModal}><img src={closeSVG} alt="" class="close-button icon-button"  /></button>
	<h2 class:condensed={title?.length > 25}>
		{isPublishAction ? 'Publish Actions' : 'Subscribe Actions'}
	</h2>
	<hr style="width:80%" />
	<div class="content">
		<div class="accordion-container">
			<Accordion>
				{#each allActions as action (action.id)}
					<Panel>
						<Header>
							Action Interval: <span style="font-weight: 700; margin-left: 1rem"
								>{action.actionIntervalName}</span
							>
							<span slot="description" style="margin-left: 4rem"
								>Topic Sets: {action.topicSets ? action.topicSets.length : 0}, Topics: {action.topics
									? action.topics.length
									: 0}, Partition: {action.partitions ? action.partitions.length : 0}</span
							>
						</Header>
						<Content>
							<div class="topic-and-partition-wrapper">
								<div style="min-width: 10rem; padding: 0 1rem; flex: 1">
									<h2 style="margin: 0 0.8rem">Topic Sets</h2>

									{#if action.topicSets?.length}
										<div>
											<List nonInteractive>
												{#each action.topicSets as topicSet (topicSet.id)}
													<Item><Text>{topicSet.name}</Text></Item>
													<Separator />
												{/each}
											</List>
										</div>
									{:else}
										<div class="no-topics">
											<p>No Topic Sets Found.</p>
										</div>
									{/if}
								</div>

								<div style="min-width: 10rem; padding: 0 1rem; flex: 1">
									<h2 style="margin: 0 0.8rem">Topics</h2>
									{#if action.topics?.length}
										<div>
											<List nonInteractive>
												{#each action.topics as topic (topic.id)}
													<Item><Text>{topic.name}</Text></Item>
													<Separator />
												{/each}
											</List>
										</div>
									{:else}
										<div class="no-topics">
											<p>No Topics Found.</p>
										</div>
									{/if}
								</div>
								<div style="min-width: 10rem; padding: 0 1rem; flex: 1">
									<h2 style="margin: 0 0.8rem">Partitions</h2>
									{#if action.partitions?.length}
										<div>
											<List nonInteractive>
												{#each action.partitions as partition (partition)}
													<Item><Text>{partition}</Text></Item>
													<Separator />
												{/each}
											</List>
										</div>
									{:else}
										<div class="no-topics">
											<p>No Partitions Found.</p>
										</div>
									{/if}
								</div>
							</div>
						</Content>
					</Panel>
				{/each}
			</Accordion>
		</div>

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
.icon-button {
	background: none;
	border: none;
	padding: 0;
	margin: 0;
	cursor: pointer;
}

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

	.no-topics {
		margin: 3rem 1rem;
	}
</style>

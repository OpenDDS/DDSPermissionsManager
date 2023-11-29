<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { onMount } from 'svelte';
	import { httpAdapter } from '../appconfig';
	import { inview } from 'svelte-inview';
	import refreshPage from '../stores/refreshPage';
	import groupContext from '../stores/groupContext';
	import singleGroupCheck from '../stores/singleGroupCheck';
	import isSingleGroup from '../stores/isSingleGroup';
	import contextMessage from '../stores/contextMessage';
	import messages from '$lib/messages.json';

	export let actionAddApplication = false;
	export let isGroupContext = false;

	let status = 'blur';
	let comboboxfilter;
	let list;
	let selected = -1;
	let previousLength;

	// Constants
	const groupsDropdownSuggestion = 7;
	const searchStringLength = 3;
	const waitTime = 500;

	// SearchBox
	let searchGroups = '';
	let searchGroupActive = false;
	let searchGroupResults;
	let groupResultPage = 0;
	let hasMoreGroups = true;
	let stopSearchingGroups = false;
	let timer;
	let selectedGroup;

	$: if ($refreshPage && $singleGroupCheck !== 'checked') searchGroup('singleGroupCheck');

	$: if ($contextMessage) {
		contextMessage.set(false);
		selectedGroup = $groupContext;
		searchGroups = selectedGroup.name;
	}

	$: if (!$groupContext) {
		searchGroups = '';
		selectedGroup = '';
	}

	// If the user does not select any of the available groups, we clear the filter on blur
	$: if (status === 'blur' && !selectedGroup) searchGroups = '';

	// Search Groups Feature
	$: if (
		searchGroups?.trim().length >= searchStringLength &&
		searchGroupActive &&
		!stopSearchingGroups
	) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			searchGroup(searchGroups.trim());
			stopSearchingGroups = true;
		}, waitTime);
	}

	// Clear the selection on blur
	$: {
		if (status === 'blur' && selected !== -1) {
			selected = -1;
		}
	}

	onMount(async () => {
		groupContext.subscribe((value) => {
			if(value?.name) searchGroups = value.name;
		});

		// Changes the text in the Modal's group field placeholder
		if (isGroupContext)
			document.querySelector('#combobox-1').placeholder =
				messages['group.context']['combobox.placeholder'];
		if (actionAddApplication)
			document.querySelector('#combobox-1').placeholder =
				messages['group.context']['add.application.placeholder'];

		// Keeps the state of the group context when changing pages
		if ($groupContext) {
			selectedGroup = $groupContext;
			searchGroups = selectedGroup.name;
		}
		if ($singleGroupCheck !== 'checked') await searchGroup('singleGroupCheck');
	});

	const searchGroup = async (searchGroupStr) => {
		let res;

		if (searchGroupStr === 'allgroups')
			res = await httpAdapter.get(
				`/groups?page=${groupResultPage}&size=${groupsDropdownSuggestion}`
			);

		if (searchGroupStr === 'singleGroupCheck') {
			singleGroupCheck.set('checked');

			res = await httpAdapter.get(`/groups?page=${groupResultPage}&size=2`);

			// If this user belong to only one group
			if (res.data?.content?.length === 1) {
				searchGroupActive = false;
				selectedGroup = res.data.content;
				searchGroups = selectedGroup[0].name;
				isSingleGroup.set(true);
				groupContext.set(...res.data.content);

				return;
			} else return;
		} else if (searchGroupStr !== 'allgroups' && searchGroupStr !== 'singleGroupCheck') {
			res = await httpAdapter.get(
				`/groups?page=${groupResultPage}&size=${groupsDropdownSuggestion}&filter=${searchGroupStr}`
			);
		}

		if (res.data.content) previousLength = res.data.content.length;

		// For all cases
		if (res.data && res.data?.content?.length < groupsDropdownSuggestion) {
			hasMoreGroups = false;
		}

		if (
			res.data?.content?.length > 0 &&
			JSON.stringify(searchGroupResults) !== JSON.stringify(res.data.content)
		) {
			if (searchGroupResults) searchGroupResults = [...searchGroupResults, ...res.data.content];
			else searchGroupResults = [...res.data.content];
		}

		if (searchGroupResults.length === 0) {
			searchGroupResults = [{ name: messages['group.context']['empty.results'] }];
		}
	};

	async function handleKeyDown(e) {
		switch (e.key) {
			case 'Escape':
				comboboxfilter.blur();
				break;
			case 'Enter':
				if (selected === -1) return;
				selectedGroup = searchGroupResults[selected];
				groupContext.set(selectedGroup);
				searchGroups = searchGroupResults[selected]?.name;
				searchGroupActive = false;

				comboboxfilter.blur();
				break;
			case 'ArrowUp':
				if (selected === -1 || selected === 0) {
					selected = searchGroupResults.length - 1;
				} else {
					selected--;
				}
				list.querySelector(`#listbox-1-option-${selected}`).scrollIntoView(false);

				break;
			case 'ArrowDown':
				if (selected !== -1) {
					selected = (selected + 1) % searchGroupResults.length;
				} else {
					selected = 0;
				}
				list?.querySelector(`#listbox-1-option-${selected}`).scrollIntoView(false);

				break;
			case 'Backspace':
				const searchInput = searchGroups.slice(0, searchGroups.length - 1);

				if (searchInput?.length >= searchStringLength) {
					searchGroupResults = [];

					searchGroup(searchInput);
				} else if (searchInput?.length > 0 && searchInput?.length < searchStringLength) {
					searchGroupResults = [];
				} else {
					searchGroupResults = [];
					searchGroup('allgroups');
				}
		}

		// Resume group search
		if (
			e.key !== 'ArrowDown' &&
			e.key !== 'ArrowUp' &&
			e.key !== 'Enter' &&
			e.key !== 'Escape' &&
			e.key !== 'Backspace'
		) {
			searchGroupResults = [];
			stopSearchingGroups = false;
			hasMoreGroups = true;
			groupResultPage = 0;
		}
	}

	const loadMoreResultsGroups = (e) => {
		if (e.detail.inView && hasMoreGroups) {
			groupResultPage++;
			searchGroup(searchGroups);
		}
	};

	const options = {
		rootMargin: '20px',
		unobserveOnEnter: true
	};
</script>

<div class="container">
	<div
		class="wrapper"
		aria-expanded={status === 'focus'}
		aria-owns="listbox-1"
		aria-haspopup="listbox"
	>
		<div style="display:inline-flex">
			<!-- svelte-ignore a11y-role-has-required-aria-props -->
			<input
				data-cy="group-input"
				placeholder={messages['group.context']['group.input.placeholder']}
				role="combobox"
				id="combobox-1"
				aria-autocomplete="list"
				aria-controls="listbox-1"
				aria-activedescendant={selected !== -1 ? `listbox-1-option-${selected}` : null}
				bind:this={comboboxfilter}
				on:keydown={handleKeyDown}
				required
				bind:value={searchGroups}
				type="text"
				on:focus={() => {
					status = 'focus';
					searchGroupResults = [];
					searchGroupActive = true;
					selectedGroup = '';
				}}
				on:click={() => {
					searchGroupResults = [];
					groupResultPage = 0;
					hasMoreGroups = true;
					searchGroupActive = true;
					selectedGroup = '';
					stopSearchingGroups = false;
					if (searchGroups?.length === 0) searchGroup('allgroups');
				}}
				on:blur={() => {
					status = 'blur';
					if (!selectedGroup.name) groupContext.set();
				}}
			/>

			{#if ((isGroupContext && selectedGroup) || $groupContext) && !$isSingleGroup}
				<button
					class="button-blue"
					style="cursor: pointer; width: 3.6rem; height: 1.7rem; margin-top: 1.75rem; margin-left: 1rem "
					on:click={() => {
						selectedGroup = '';
						searchGroups = '';
						groupContext.set('clear');
					}}
				>
					{messages['group.context']['search.clear.button']}
				</button>
			{/if}
		</div>
		<div>
			{#if searchGroupResults}
				{#if status === 'focus' && searchGroupResults.length > 0}
					<ul bind:this={list} id="listbox-1" role="listbox" tabindex={-1}>
						{#each searchGroupResults as group, i (group)}
							<li
								id="listbox-1-option-{i}"
								role="option"
								aria-setsize={searchGroupResults.length}
								aria-posinset={i + 1}
								aria-selected={selected === i}
								class:selected={selected === i}
								on:mousedown={() => {
									searchGroupActive = false;
									selectedGroup = group;
									groupContext.set(selectedGroup);
									searchGroups = group.name;

									comboboxfilter.blur();
									selected = -1;
								}}
							>
								{group.name}
							</li>
						{/each}
						<div use:inview={{ options }} on:change={loadMoreResultsGroups} />
					</ul>
				{/if}
			{/if}
		</div>
	</div>
</div>

<style>
	* {
		box-sizing: border-box;
	}
	.container {
		width: 250px;
		position: relative;
	}

	input {
		width: 14rem;
		background: transparent url('../icons/expand.svg') no-repeat right;
		background-size: 10%;
		border-width: 1px;
		margin: 1.5rem 0 1rem 0;
		height: 2.1rem;
	}

	ul {
		top: 3.6rem;
		width: 14rem;
		max-height: 12.5rem;
		overflow-y: auto;
		padding-left: 0;
		list-style: none;
		position: absolute;
		display: block;
		border: 1px solid #000;
		margin-top: -0.1rem;
		background-color: white;
		z-index: 5000;
	}

	li {
		padding: 5px 10px;
	}

	li:hover,
	.selected {
		background-color: #efefef;
	}
</style>

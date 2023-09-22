<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { onMount, onDestroy } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import users from '../../stores/users';
	import Modal from '../../lib/Modal.svelte';
	import RetrievedTimestamp from '../../lib/RetrievedTimestamp.svelte';
	import userValidityCheck from '../../stores/userValidityCheck';
	import refreshPage from '../../stores/refreshPage';
	import { goto } from '$app/navigation';
	import { browser } from '$app/environment';
	import GroupMembership from './GroupMembership.svelte';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import deleteSVG from '../../icons/delete.svg';
	import addSVG from '../../icons/add.svg';
	import pageforwardSVG from '../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../icons/pagefirst.svg';
	import pagelastSVG from '../../icons/pagelast.svg';
	import userEmail from '../../stores/userEmail';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import superUsersTotalPages from '../../stores/superUsersTotalPages';
	import superUsersTotalSize from '../../stores/superUsersTotalSize';
	import retrievedTimestamps from '../../stores/retrievedTimestamps.js';
	import {updateRetrievalTimestamp} from '../../utils.js';

	export let data, errors;

	// Redirects the User to the Login screen if not authenticated
	$: if (browser) {
		setTimeout(() => {
			if (!$isAuthenticated) goto(`/`, true);
		}, waitTime);
	}

	// Locks the background scroll when modal is open
	$: if (browser && (addSuperUserVisible || deleteSuperUserVisible)) {
		document.body.classList.add('modal-open');
	} else if (browser && !(addSuperUserVisible || deleteSuperUserVisible)) {
		document.body.classList.remove('modal-open');
	}

	// Checkboxes selection
	$: if ($users?.length === superUsersRowsSelected?.length) {
		superUsersRowsSelectedTrue = false;
		superUsersAllRowsSelectedTrue = true;
	} else if (superUsersRowsSelected?.length > 0) {
		superUsersRowsSelectedTrue = true;
	} else {
		superUsersAllRowsSelectedTrue = false;
	}

	// Promises
	let promise;

	// Constants
	const returnKey = 13;

	// Tables
	let superUsersRowsSelected = [];
	let superUsersRowsSelectedTrue = false;
	let superUsersAllRowsSelectedTrue = false;

	// Error Handling
	let errorMsg, errorObject;

	// Modals
	let errorMessageVisible = false;
	let deleteSuperUserVisible = false;
	let addSuperUserVisible = false;

	// SearchBox
	let searchString;
	const searchStringLength = 3;
	let searchUserResults;
	let timer;
	const waitTime = 1000;

	// Forms
	let emailValue = '';

	// Reactive Statements
	$: if (addSuperUserVisible === false) emailValue = '';
	$: if (deleteSuperUserVisible === true) addSuperUserVisible = false;

	// Pagination
	let superUsersPerPage = 10;

	let superUsersCurrentPage = 0;

	// Search Feature
	$: if (searchString?.trim().length >= searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			searchUser(searchString.trim());
		}, waitTime);
	}

	$: if (searchString?.trim().length < searchStringLength) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadAllSuperUsers();
		}, waitTime);
	}

	const reloadAllSuperUsers = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/admins?page=${page}&size=${superUsersPerPage}&filter=${searchString}`
				);
			} else {
				res = await httpAdapter.get(`/admins?page=${page}&size=${superUsersPerPage}`);
			}
			if (res.data) {
				superUsersTotalPages.set(res.data.totalPages);
				superUsersTotalSize.set(res.data.totalSize);
			}
			users.set(res.data.content);
			superUsersCurrentPage = page;
			updateRetrievalTimestamp(retrievedTimestamps, 'superUsers');
		} catch (err) {
			if (err.response.status === 401)
				errorMessage(
					errorMessages['super_user']['loading.error.title'],
					errorMessages['super_user']['unauthorized.error']
				);
			else errorMessage(errorMessages['super_user']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		headerTitle.set('My Users');
		detailView.set();
		if (document.querySelector('#super-users-table') == null)
			if ($isAdmin) promise = await reloadAllSuperUsers();
	});

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

	const searchUser = async (searchString) => {
		searchUserResults = await httpAdapter.get(
			`/admins?page=0&size=${superUsersPerPage}&filter=${searchString}`
		);
		if (searchUserResults.data.content) {
			users.set(searchUserResults.data.content);
		} else {
			users.set([]);
		}
		superUsersTotalPages.set(searchUserResults.data.totalPages);
		if (searchUserResults.data.totalSize !== undefined)
			superUsersTotalSize.set(searchUserResults.data.totalSize);
		superUsersCurrentPage = 0;
		updateRetrievalTimestamp(retrievedTimestamps, 'superUsers');
	};

	const addSuperUser = async (userEmail) => {
		const res = await httpAdapter
			.post(`/admins/save`, {
				email: userEmail
			})
			.catch((err) => {
				if (err.response.status === 401)
					errorMessage(
						errorMessages['super_user']['saving.error.title'],
						errorMessages['super_user']['unauthorized.error']
					);
				else errorMessage(errorMessages['super_user']['saving.error.title'], err.message);
			});
		userValidityCheck.set(true);
		updateRetrievalTimestamp(retrievedTimestamps, 'superUsers');
	};

	const deleteSelectedSuperUsers = async () => {
		try {
			for (const superUser of superUsersRowsSelected) {
				await httpAdapter.put(`/admins/remove_admin/${superUser.id}`);
			}
			updateRetrievalTimestamp(retrievedTimestamps, 'superUsers');
		} catch (err) {
			const decodedError = decodeError(Object.create(...err.response.data));

			if (err.response.status === 401)
				errorMessage(
					errorMessages['super_user']['deleting.error.title'],
					errorMessages['super_user']['unauthorized.error']
				);
			else
				errorMessage(
					errorMessages['super_user']['deleting.error.title'],
					errorMessages[decodedError.category][decodedError.code]
				);
		}

		superUsersRowsSelected = [];

		userValidityCheck.set(true);
	};

	const deselectAllSuperUsersCheckboxes = () => {
		superUsersAllRowsSelectedTrue = false;
		superUsersRowsSelectedTrue = false;
		superUsersRowsSelected = [];
		let checkboxes = document.querySelectorAll('.super-user-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.super-user-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const decodeError = (errorObject) => {
		errorObject = errorObject.code.replaceAll('-', '_');
		const cat = errorObject.substring(0, errorObject.indexOf('.'));
		const code = errorObject.substring(errorObject.indexOf('.') + 1, errorObject.length);
		return { category: cat, code: code };
	};
</script>

<svelte:head>
	<title>{messages['user']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Users" />
</svelte:head>

{#key $refreshPage}
	{#if $isAuthenticated}
		<h1 data-cy="users">{messages['user']['title.user']}</h1>
		<GroupMembership />

		{#await promise then _}
			{#if errorMessageVisible}
				<Modal
					title={errorMsg}
					errorMsg={true}
					errorDescription={errorObject}
					closeModalText={errorMessages['modal']['button.close']}
					on:cancel={() => errorMessageClear()}
				/>
			{/if}

			{#if $isAdmin}
				{#if addSuperUserVisible}
					<Modal
						title={messages['user']['add.super.user.title']}
						email={true}
						actionAddSuperUser={true}
						on:cancel={() => (addSuperUserVisible = false)}
						on:addSuperUser={async (e) => {
							await addSuperUser(e.detail);
							await reloadAllSuperUsers();
							addSuperUserVisible = false;
						}}
					/>
				{/if}

				{#if deleteSuperUserVisible && !errorMessageVisible}
					<Modal
						title="{messages['user']['delete.title.user']} {superUsersRowsSelected.length > 1
							? messages['user']['delete.multiple.super.user']
							: messages['user']['delete.single.super.user']}"
						actionDeleteSuperUsers={true}
						on:deleteSuperUsers={async () => {
							const deleteActiveSuperUser = superUsersRowsSelected.some(
								(superUser) => superUser.email === $userEmail
							);
							await deleteSelectedSuperUsers();

							if (deleteActiveSuperUser) {
								const checkValidity = async () => {
									if ($userValidityCheck) {
										setTimeout(() => checkValidity, 500);
									} else {
										await reloadAllSuperUsers();
									}
								};

								checkValidity();
							} else await reloadAllSuperUsers();
							deselectAllSuperUsersCheckboxes();
							deleteSuperUserVisible = false;
						}}
						on:cancel={() => {
							if (superUsersRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
								superUsersRowsSelected = [];

							deleteSuperUserVisible = false;
						}}
					/>
				{/if}

				<div class="content">
					<h1 data-cy="super-users">{messages['user']['title.super.user']}</h1>

					<form class="searchbox">
						<input
							data-cy="search-super-users-table"
							class="searchbox"
							type="search"
							placeholder={messages['user']['search.placeholder.super.user']}
							bind:value={searchString}
							on:blur={() => {
								searchString = searchString?.trim();
							}}
							on:keydown={(event) => {
								if (event.which === returnKey) {
									document.activeElement.blur();
									searchString = searchString?.trim();
								}
							}}
						/>
					</form>

					{#if searchString?.length > 0}
						<button
							class="button-blue"
							style="cursor: pointer; width: 4rem; height: 2.1rem"
							on:click={() => (searchString = '')}
						>
							{messages['user']['search.clear.button.super.user']}
						</button>
					{/if}
					<img
						src={deleteSVG}
						alt="options"
						class="dot"
						class:button-disabled={superUsersRowsSelected.length === 0}
						style="margin-left: 0.5rem"
						on:click={() => {
							if (superUsersRowsSelected.length > 0) deleteSuperUserVisible = true;
						}}
						on:keydown={(event) => {
							if (event.which === returnKey) {
								if (superUsersRowsSelected.length > 0) deleteSuperUserVisible = true;
							}
						}}
						on:mouseenter={() => {
							if (superUsersRowsSelected.length === 0) {
								const tooltip = document.querySelector('#delete-super-users');
								setTimeout(() => {
									tooltip.classList.remove('tooltip-hidden');
									tooltip.classList.add('tooltip');
								}, 1000);
							}
						}}
						on:mouseleave={() => {
							if (superUsersRowsSelected.length === 0) {
								const tooltip = document.querySelector('#delete-super-users');
								setTimeout(() => {
									tooltip.classList.add('tooltip-hidden');
									tooltip.classList.remove('tooltip');
								}, 1000);
							}
						}}
					/>

					<span
						id="delete-super-users"
						class="tooltip-hidden"
						style="margin-left: 9.5rem; margin-top: -1.8rem"
					>
						{messages['user']['delete.tooltip.super.user']}
					</span>

					<img
						data-cy="add-super-user"
						src={addSVG}
						alt="options"
						class="dot"
						on:click={() => {
							addSuperUserVisible = true;
						}}
						on:keydown={(event) => {
							if (event.which === returnKey) {
								addSuperUserVisible = true;
							}
						}}
					/>

					{#if $users?.length > 0}
						<table data-cy="super-users-table" id="super-users-table" style="margin-top: 0.5rem">
							<thead>
								<tr style="border-top: 1px solid black; border-bottom: 2px solid">
									<td>
										<input
											tabindex="-1"
											type="checkbox"
											class="super-user-checkbox"
											style="margin-right: 0.5rem"
											bind:indeterminate={superUsersRowsSelectedTrue}
											on:click={(e) => {
												if (e.target.checked) {
													superUsersRowsSelected = $users;
													superUsersRowsSelectedTrue = false;
													superUsersAllRowsSelectedTrue = true;
												} else {
													superUsersAllRowsSelectedTrue = false;
													superUsersRowsSelectedTrue = false;
													superUsersRowsSelected = [];
												}
											}}
											checked={superUsersAllRowsSelectedTrue}
										/>
									</td>
									<td>{messages['user']['table.super.user.column.one']}</td>
									<td />
								</tr>
							</thead>
							<tbody>
								{#each $users as user}
									<tr class:highlighted={user.email === $userEmail}>
										<td style="width: 2rem">
											<input
												tabindex="-1"
												type="checkbox"
												class="super-user-checkbox"
												checked={superUsersAllRowsSelectedTrue}
												on:change={(e) => {
													if (e.target.checked === true) {
														superUsersRowsSelected.push(user);
														// reactive statement
														superUsersRowsSelected = superUsersRowsSelected;
														superUsersRowsSelectedTrue = true;
													} else {
														superUsersRowsSelected = superUsersRowsSelected.filter(
															(selection) => selection !== user
														);
														if (superUsersRowsSelected.length === 0) {
															superUsersRowsSelectedTrue = false;
														}
													}
												}}
											/>
										</td>
										<td style="margin-left: 0.3rem">{user.email}</td>
										<td style="cursor: pointer; text-align: right; padding-right: 0.25rem">
											<!-- svelte-ignore a11y-click-events-have-key-events -->
											<img
												data-cy="delete-super-users-icon"
												src={deleteSVG}
												width="25px"
												alt="delete user"
												on:click={() => {
													if (!superUsersRowsSelected?.some((usr) => usr === user))
														superUsersRowsSelected.push(user);
													deleteSuperUserVisible = true;
												}}
											/>
										</td>
									</tr>
								{/each}
							</tbody>
						</table>
					{:else}
						<p style="margin-left: 0.3rem">{messages['user']['empty.super.users']}</p>
					{/if}
				</div>

				<div class="pagination">
					<span>{messages['pagination']['rows.per.page']}</span>
					<select
						tabindex="-1"
						on:change={(e) => {
							superUsersPerPage = e.target.value;
							reloadAllSuperUsers();
						}}
						name="RowsPerPage"
					>
						<option value="10">10</option>
						<option value="25">25</option>
						<option value="50">50</option>
						<option value="75">75</option>
						<option value="100">100&nbsp;</option>
					</select>
					<span style="margin: 0 2rem 0 2rem">
						{#if $superUsersTotalSize > 0}
							{1 + superUsersCurrentPage * superUsersPerPage}
						{:else}
							0
						{/if}
						- {Math.min(superUsersPerPage * (superUsersCurrentPage + 1), $superUsersTotalSize)} of
						{$superUsersTotalSize}
					</span>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagefirstSVG}
						alt="first page"
						class="pagination-image"
						class:disabled-img={superUsersCurrentPage === 0}
						on:click={() => {
							deselectAllSuperUsersCheckboxes();
							if (superUsersCurrentPage > 0) {
								superUsersCurrentPage = 0;
								reloadAllSuperUsers();
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagebackwardsSVG}
						alt="previous page"
						class="pagination-image"
						class:disabled-img={superUsersCurrentPage === 0}
						on:click={() => {
							deselectAllSuperUsersCheckboxes();
							if (superUsersCurrentPage > 0) {
								superUsersCurrentPage--;
								reloadAllSuperUsers(superUsersCurrentPage);
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pageforwardSVG}
						alt="next page"
						class="pagination-image"
						class:disabled-img={superUsersCurrentPage + 1 === $superUsersTotalPages ||
							$users?.length === undefined}
						on:click={() => {
							deselectAllSuperUsersCheckboxes();
							if (superUsersCurrentPage + 1 < $superUsersTotalPages) {
								superUsersCurrentPage++;
								reloadAllSuperUsers(superUsersCurrentPage);
							}
						}}
					/>
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						src={pagelastSVG}
						alt="last page"
						class="pagination-image"
						class:disabled-img={superUsersCurrentPage + 1 === $superUsersTotalPages ||
							$users?.length === undefined}
						on:click={() => {
							deselectAllSuperUsersCheckboxes();
							if (superUsersCurrentPage < $superUsersTotalPages) {
								superUsersCurrentPage = $superUsersTotalPages - 1;
								reloadAllSuperUsers(superUsersCurrentPage);
							}
						}}
					/>
				</div>
			{/if}

			<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['superUsers']} />
			<p style="margin-top: 8rem">{messages['footer']['message']}</p>
		{/await}
	{/if}
{/key}

<style>
	.content {
		width: fit-content;
		min-width: 32rem;
		margin-right: 1rem;
	}

	table {
		width: 32rem;
	}

	.dot {
		float: right;
	}

	p {
		font-size: large;
	}
</style>

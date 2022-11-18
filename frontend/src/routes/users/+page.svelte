<script>
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../appconfig';
	import users from '../../stores/users';
	import groups from '../../stores/groups';
	import Modal from '../../lib/Modal.svelte';
	import { goto } from '$app/navigation';
	import { browser } from '$app/env';
	import GroupMembership from './GroupMembership.svelte';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import threedotsSVG from '../../icons/threedots.svg';
	import deleteSVG from '../../icons/delete.svg';
	import addSVG from '../../icons/add.svg';
	import pageforwardSVG from '../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../icons/pagefirst.svg';
	import pagelastSVG from '../../icons/pagelast.svg';

	export let data, errors;

	// Redirects the User to the Login screen if not authenticated
	$: if (!$isAuthenticated && browser) {
		goto(`/`, true);
	}

	// Constants
	const returnKey = 13;

	// DropDowns
	let superUsersDropDownVisible = false;
	let superUsersDropDownMouseEnter = false;

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
	const waitTime = 500;

	// Forms
	let emailValue = '';

	// Reactive Statements
	$: if (addSuperUserVisible === false) emailValue = '';
	$: if (deleteSuperUserVisible === true) addSuperUserVisible = false;

	// Selection
	let selectedUserId;

	// Pagination
	let superUsersPerPage = 10;
	let superUsersTotalPages, superUsersTotalSize;
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

	onMount(async () => {
		headerTitle.set('Users');
		detailView.set();

		await reloadAllSuperUsers();

		const groupsData = await httpAdapter.get(`/groups`);
		groups.set(groupsData.data.content);
	});

	const errorMessage = (errMsg, errObj) => {
		errorMsg = errMsg;
		errorObject = errObj;
		errorMessageVisible = true;
	};

	const errorMessageClear = () => {
		errorMsg = '';
		errorObject = '';
		errorMessageVisible = false;
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
		superUsersTotalPages = searchUserResults.data.totalPages;
		if (searchUserResults.data.totalSize !== undefined)
			superUsersTotalSize = searchUserResults.data.totalSize;
		superUsersCurrentPage = 0;
	};

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
				superUsersTotalPages = res.data.totalPages;
				superUsersTotalSize = res.data.totalSize;
			}
			users.set(res.data.content);
			superUsersCurrentPage = page;
		} catch (err) {
			errorMessage('Error Loading Super Admins', err.message);
		}
	};

	const addSuperUser = async (userEmail) => {
		await httpAdapter
			.post(`/admins/save`, {
				email: userEmail
			})
			.catch((err) => {
				errorMessage('Error Saving Super Admin', err.message);
			});
	};

	const deleteSelectedSuperUsers = async () => {
		try {
			for (const superUser of superUsersRowsSelected) {
				await httpAdapter.put(`/admins/remove-admin/${superUser.id}`, {});
			}
		} catch (err) {
			errorMessage('Error Deleting Super User', err.message);
		}
		superUsersRowsSelected = [];
	};

	const deselectAllSuperUsersCheckboxes = () => {
		superUsersAllRowsSelectedTrue = false;
		superUsersRowsSelectedTrue = false;
		superUsersRowsSelected = [];
		let checkboxes = document.querySelectorAll('.super-user-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};
</script>

<svelte:head>
	<title>Users | DDS Permissions Manager</title>
	<meta name="description" content="DDS Permissions Manager Users" />
</svelte:head>

{#if $isAuthenticated}
	<h1>Users</h1>
	<GroupMembership />

	{#if $isAdmin}
		{#if errorMessageVisible}
			<Modal
				title={errorMsg}
				description={errorObject}
				on:cancel={() => {
					errorMessageVisible = false;
					errorMessageClear();
				}}
				><br /><br />
				<div class="confirm">
					<button
						class="button-delete"
						on:click={() => {
							errorMessageVisible = false;
							errorMessageClear();
						}}>Ok</button
					>
				</div>
			</Modal>
		{/if}

		{#if addSuperUserVisible}
			<Modal
				title="Add Super User"
				email={true}
				actionAddSuperUser={true}
				on:cancel={() => (addSuperUserVisible = false)}
				on:addSuperUser={async (e) => {
					await addSuperUser(e.detail);
					reloadAllSuperUsers();
					addSuperUserVisible = false;
				}}
			/>
		{/if}

		{#if deleteSuperUserVisible && !errorMessageVisible}
			<Modal
				title="Delete {superUsersRowsSelected.length > 1 ? 'Users' : 'User'}"
				actionDeleteSuperUsers={true}
				on:deleteSuperUsers={async () => {
					await deleteSelectedSuperUsers();
					reloadAllSuperUsers();
					deselectAllSuperUsersCheckboxes();
					deleteSuperUserVisible = false;
				}}
				on:cancel={() => (deleteSuperUserVisible = false)}
			/>
		{/if}

		<div class="content">
			<h1>Super User</h1>

			<form class="searchbox">
				<input
					class="searchbox"
					type="search"
					placeholder="Search"
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

			<div
				class="dot"
				on:mouseleave={() => {
					setTimeout(() => {
						if (!superUsersDropDownMouseEnter) superUsersDropDownVisible = false;
					}, waitTime);
				}}
				on:click={() => {
					if (!deleteSuperUserVisible && !addSuperUserVisible)
						superUsersDropDownVisible = !superUsersDropDownVisible;
				}}
			>
				<img src={threedotsSVG} alt="options" style="scale:50%" />

				{#if superUsersDropDownVisible && !deleteSuperUserVisible && !addSuperUserVisible}
					<table
						class="dropdown"
						on:mouseenter={() => {
							if (superUsersDropDownVisible) superUsersDropDownMouseEnter = true;
						}}
						on:mouseleave={() => {
							setTimeout(() => {
								superUsersDropDownVisible = false;
							}, waitTime);
						}}
					>
						<tr
							class:disabled={superUsersRowsSelected.length === 0}
							on:click={async () => {
								superUsersDropDownVisible = false;
								if (superUsersRowsSelected.length > 0) deleteSuperUserVisible = true;
							}}
						>
							<td>
								Delete Selected {superUsersRowsSelected.length > 1 ? 'Users' : 'User'}
							</td>
							<td>
								<img
									src={deleteSVG}
									alt="delete user"
									height="35rem"
									style="vertical-align: -0.8rem"
									class:disabled-img={superUsersRowsSelected.length === 0}
								/>
							</td>
						</tr>

						<tr>
							<td
								style="border-bottom-color: transparent"
								on:click={() => {
									superUsersDropDownVisible = false;
									addSuperUserVisible = true;
								}}
							>
								Add New Super User
							</td>
							<td
								style="width: 0.1rem; height: 2.2rem; padding-left: 0; vertical-align: middle;border-bottom-color: transparent"
							>
								<img
									src={addSVG}
									alt="add user"
									height="27rem"
									style="vertical-align: middle; margin-left: 1.3rem"
								/>
							</td>
						</tr>
					</table>
				{/if}
			</div>

			{#if $users && $users.length > 0}
				<table style="margin-top: 0.5rem">
					<tr style="border-top: 1px solid black">
						<td>
							<input
								type="checkbox"
								class="super-user-checkbox"
								style="margin-right: 0.5rem"
								bind:indeterminate={superUsersRowsSelectedTrue}
								on:click={(e) => {
									superUsersDropDownVisible = false;
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
						<td>E-mail</td>
						<td />
					</tr>
					{#each $users as user}
						<tr>
							<td style="width: 2rem">
								<input
									type="checkbox"
									class="super-user-checkbox"
									checked={superUsersAllRowsSelectedTrue}
									on:change={(e) => {
										superUsersDropDownVisible = false;
										if (e.target.checked === true) {
											superUsersRowsSelected.push(user);
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
								<img
									src={deleteSVG}
									width="25px"
									alt="delete user"
									on:click={() => {
										if (!superUsersRowsSelected.some((usr) => usr === user))
											superUsersRowsSelected.push(user);
										deleteSuperUserVisible = true;
									}}
								/>
							</td>
						</tr>
					{/each}
				</table>
			{:else}
				<p style="margin-left: 0.3rem">No Super Users Found</p>
			{/if}
		</div>

		<div class="pagination">
			<span>Rows per page</span>
			<select
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
				{#if superUsersTotalSize > 0}
					{1 + superUsersCurrentPage * superUsersPerPage}
				{:else}
					0
				{/if}
				-{Math.min(superUsersPerPage * (superUsersCurrentPage + 1), superUsersTotalSize)} of
				{superUsersTotalSize}
			</span>
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
			<img
				src={pageforwardSVG}
				alt="next page"
				class="pagination-image"
				class:disabled-img={superUsersCurrentPage + 1 === superUsersTotalPages}
				on:click={() => {
					deselectAllSuperUsersCheckboxes();
					if (superUsersCurrentPage + 1 < superUsersTotalPages) {
						superUsersCurrentPage++;
						reloadAllSuperUsers(superUsersCurrentPage);
					}
				}}
			/>
			<img
				src={pagelastSVG}
				alt="last page"
				class="pagination-image"
				class:disabled-img={superUsersCurrentPage + 1 === superUsersTotalPages}
				on:click={() => {
					deselectAllSuperUsersCheckboxes();
					if (superUsersCurrentPage < superUsersTotalPages) {
						superUsersCurrentPage = superUsersTotalPages - 1;
						reloadAllSuperUsers(superUsersCurrentPage);
					}
				}}
			/>
		</div>
	{/if}
{/if}

<style>
	.content {
		width: 25rem;
	}

	table {
		width: 25rem;
	}

	.hidden {
		display: none;
	}

	.dot {
		float: right;
	}

	.dropdown {
		margin-top: 8.5rem;
		margin-right: 9.2rem;
		width: 13.5rem;
	}
</style>
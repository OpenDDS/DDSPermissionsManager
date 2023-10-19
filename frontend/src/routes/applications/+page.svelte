<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { onMount } from 'svelte';
	import { isAuthenticated, isAdmin } from '../../stores/authentication';
	import { httpAdapter } from '../../appconfig';
	import permissionsByGroup from '../../stores/permissionsByGroup';
	import refreshPage from '../../stores/refreshPage';
	import Modal from '../../lib/Modal.svelte';
	import RetrievedTimestamp from '../../lib/RetrievedTimestamp.svelte';
	import applications from '../../stores/applications';
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { browser } from '$app/environment';
	import headerTitle from '../../stores/headerTitle';
	import detailView from '../../stores/detailView';
	import deleteSVG from '../../icons/delete.svg';
	import detailSVG from '../../icons/detail.svg';
	import addSVG from '../../icons/add.svg';
	import pageforwardSVG from '../../icons/pageforward.svg';
	import pagebackwardsSVG from '../../icons/pagebackwards.svg';
	import pagefirstSVG from '../../icons/pagefirst.svg';
	import pagelastSVG from '../../icons/pagelast.svg';
	import lockSVG from '../../icons/lock.svg';
	import copySVG from '../../icons/copy.svg';
	import errorMessages from '$lib/errorMessages.json';
	import messages from '$lib/messages.json';
	import userValidityCheck from '../../stores/userValidityCheck';
	import groupContext from '../../stores/groupContext';
	import curlCommands from '$lib/curlCommands.json';
	import showSelectGroupContext from '../../stores/showSelectGroupContext';
	import singleGroupCheck from '../../stores/singleGroupCheck';
	import createItem from '../../stores/createItem';
	import applicationsTotalPages from '../../stores/applicationsTotalPages';
	import applicationsTotalSize from '../../stores/applicationsTotalSize';
	import ApplicationDetails from './ApplicationDetails.svelte';
	import retrievedTimestamps from '../../stores/retrievedTimestamps.js';
	import { updateRetrievalTimestamp } from '../../utils.js';

	export let data, errors;

	// Group Context
	$: if ($groupContext?.id) reloadAllApps();

	$: if ($groupContext === 'clear') {
		groupContext.set();
		singleGroupCheck.set();
		selectedGroup = '';
		reloadAllApps();
	}

	// Permission Badges Create
	$: if ($createItem === 'application') {
		createItem.set(false);
		addApplicationVisible = true;
	}

	// Redirects the User to the Login screen if not authenticated
	$: if (browser) {
		setTimeout(() => {
			if (!$isAuthenticated) goto(`/`, true);
		}, waitTime);
	}

	// Locks the background scroll when modal is open
	$: if (browser && (addApplicationVisible || deleteApplicationVisible || errorMessageVisible)) {
		document.body.classList.add('modal-open');
	} else if (
		browser &&
		!(addApplicationVisible || deleteApplicationVisible || errorMessageVisible)
	) {
		document.body.classList.remove('modal-open');
	}

	// checkboxes selection
	$: if ($applications?.length === applicationsRowsSelected?.length) {
		applicationsRowsSelectedTrue = false;
		applicationsAllRowsSelectedTrue = true;
	} else if (applicationsRowsSelected?.length > 0) {
		applicationsRowsSelectedTrue = true;
	} else {
		applicationsAllRowsSelectedTrue = false;
	}

	// Messages
	let deleteToolip;
	let deleteMouseEnter = false;

	let addTooltip;
	let addMouseEnter = false;

	// Promises
	let promise, promiseDetail;

	// Public flag
	let isPublic, checkboxSelector;

	// Constants
	const fiveSeconds = 5000;
	const returnKey = 13;
	const searchStringLength = 3;
	const waitTime = 1000;

	// Authentication
	let isApplicationAdmin = false;

	// Curl Commands
	let curlCommandsDecodedCodeThree,
		curlCommandsDecodedCodeFour,
		curlCommandsDecodedCodeFive,
		curlCommandsDecodedCodeSix,
		curlCommandsDecodedCodeSeven,
		curlCommandsDecodedCodeEight;

	// Error Handling
	let errorMsg, errorObject;

	// Password & Tokens
	let password;
	let bindToken;

	// Modals
	let errorMessageVisible = false;
	let addApplicationVisible = false;
	let deleteApplicationVisible = false;
	let applicationDetailVisible = false;
	let editApplicationVisible = false;

	// Tables
	let applicationsRowsSelected = [];
	let applicationsRowsSelectedTrue = false;
	let applicationsAllRowsSelectedTrue = false;
	let applicationsPerPage = 10;

	// Applications SearchBox
	let searchString;
	let searchAppResults;

	// Groups SearchBox
	let searchGroups;

	// Forms
	let generateCredentialsVisible = false;
	let generateBindTokenVisible = false;
	let showCopyPasswordNotificationVisible = false;
	let showCopyBindTokenNotificationVisible = false;
	let copiedVisible = false;

	// Timer
	let timer, timerTwo;

	// App
	let applicationListVisible = true;
	let appName, appDescription, appPublic;
	let selectedGroup = '';

	// Pagination
	let applicationsCurrentPage = 0;

	// Selection
	let selectedAppName,
		selectedAppId,
		selectedAppGroupId,
		selectedAppGroupName,
		selectedAppDescription,
		selectedAppDescriptionSelector,
		selectedAppPublic,
		selectedAppDateUpdated;

	// Validation
	let previousAppName;

	// Application Detail
	let applicationDetailId, ApplicationDetailGroupId;

	const isNumericOnly = (str) => {
		var pattern = /^\d+$/;
		return pattern.test(str);
	};

	// Return to List view
	$: if ($detailView === 'backToList') {
		headerTitle.set(messages['application']['title']);
		reloadAllApps();
		returnToApplicationsList();
	}

	// Search Feature
	$: if (
		searchString?.trim().length >= searchStringLength &&
		!isNumericOnly(searchString?.trim())
	) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			searchApp(searchString.trim());
		}, waitTime);
	} else if (isNumericOnly(searchString?.trim())) {
		clearTimeout(timerTwo);
		timerTwo = setTimeout(() => {
			searchApp(searchString.trim());
		}, waitTime);
	}

	$: if (searchString?.trim().length < searchStringLength && !isNumericOnly(searchString?.trim())) {
		clearTimeout(timer);
		timer = setTimeout(() => {
			reloadAllApps();
		}, waitTime);
	}

	// Reset add group form once closed
	$: if (addApplicationVisible === false) {
		searchGroups = '';
		appName = '';
	}

	const reloadAllApps = async (page = 0) => {
		try {
			let res;
			if (searchString && searchString.length >= searchStringLength) {
				res = await httpAdapter.get(
					`/applications?page=${page}&size=${applicationsPerPage}&filter=${searchString}`
				);
			} else if ($groupContext) {
				res = await httpAdapter.get(
					`/applications?page=${page}&size=${applicationsPerPage}&group=${$groupContext.id}`
				);
			} else {
				res = await httpAdapter.get(`/applications?page=${page}&size=${applicationsPerPage}`);
			}

			if (res.data) {
				applicationsTotalPages.set(res.data.totalPages);
				applicationsTotalSize.set(res.data.totalSize);
			}
			applications.set(res.data.content);
			applicationsCurrentPage = page;

			updateRetrievalTimestamp(retrievedTimestamps, 'applications');
		} catch (err) {
			userValidityCheck.set(true);
			applications.set();
			errorMessage(errorMessages['application']['loading.error.title'], err.message);
		}
	};

	onMount(async () => {
		detailView.set('first run');

		headerTitle.set(messages['application']['title']);

		if (document.querySelector('.content') == null) promise = await reloadAllApps();

		if ($permissionsByGroup) {
			isApplicationAdmin = $permissionsByGroup.some(
				(groupPermission) => groupPermission.isApplicationAdmin === true
			);
		}
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

	const curlCommandsDecode = () => {
		curlCommandsDecodedCodeThree = curlCommands['authenticate']
			.replace('${APP_ID}', `${selectedAppId}`)
			.replace('${DPM_URL}', `${$page.url.protocol}//${$page.url.host}`);

		curlCommandsDecodedCodeFour = curlCommands['identityCa'].replace(
			'${DPM_URL}',
			`${$page.url.protocol}//${$page.url.host}`
		);

		curlCommandsDecodedCodeFive = curlCommands['permissionsCa'].replace(
			'${DPM_URL}',
			`${$page.url.protocol}//${$page.url.host}`
		);

		curlCommandsDecodedCodeSix = curlCommands['governance'].replace(
			'${DPM_URL}',
			`${$page.url.protocol}//${$page.url.host}`
		);

		curlCommandsDecodedCodeSeven = curlCommands['keyPair'].replace(
			'${DPM_URL}',
			`${$page.url.protocol}//${$page.url.host}`
		);

		curlCommandsDecodedCodeEight = curlCommands['permissionsFile'].replace(
			'${DPM_URL}',
			`${$page.url.protocol}//${$page.url.host}`
		);
	};

	const searchApp = async (searchString) => {
		if (!isNumericOnly(searchString)) {
			searchAppResults = await httpAdapter.get(
				`/applications?page=0&size=${applicationsPerPage}&filter=${searchString}`
			);
		} else {
			searchAppResults = await httpAdapter.get(
				`/applications?page=0&size=${applicationsPerPage}&applicationId=${searchString}`
			);
		}

		if (searchAppResults.data.content) {
			applications.set(searchAppResults.data.content);
		} else {
			applications.set([]);
		}
		applicationsTotalPages.set(searchAppResults.data.totalPages);
		if (searchAppResults.data.totalSize !== undefined)
			applicationsTotalSize.set(searchAppResults.data.totalSize);
		applicationsCurrentPage = 0;
		updateRetrievalTimestamp(retrievedTimestamps, 'applications');
	};

	const addApplication = async (
		forwardedAppName,
		forwardedAppDescription,
		forwardedAppPublic,
		forwardedSearchGroups,
		forwardedSelectedGroup
	) => {
		if (!forwardedSelectedGroup) {
			const groupId = await httpAdapter.get(
				`/groups?page=0&size=${applicationsPerPage}&filter=${forwardedSearchGroups}`
			);

			if (
				groupId.data.content &&
				groupId.data.content[0]?.name.toUpperCase() === forwardedSearchGroups.toUpperCase()
			) {
				forwardedSelectedGroup = groupId.data.content[0]?.id;
			}
		}

		try {
			await httpAdapter.post(`/applications/save`, {
				name: forwardedAppName,
				description: forwardedAppDescription,
				public: forwardedAppPublic,
				group: forwardedSelectedGroup
			});
			addApplicationVisible = false;
			updateRetrievalTimestamp(retrievedTimestamps, 'applications');
		} catch (err) {
			if (err.response.data && err.response.status === 400) {
				const decodedError = decodeError(Object.create(...err.response.data));
				errorMessage(
					errorMessages['application']['adding.error.title'],
					errorMessages[decodedError.category][decodedError.code]
				);
			}
		}
		selectedGroup = '';

		await reloadAllApps();
	};

	const deleteSelectedApplications = async () => {
		try {
			for (const app of applicationsRowsSelected) {
				await httpAdapter.delete(`/applications/${app.id}`);
			}
			updateRetrievalTimestamp(retrievedTimestamps, 'applications');
		} catch (err) {
			const decodedError = decodeError(Object.create(...err.response.data));
			errorMessage(
				errorMessages['application']['deleting.error.title'],
				errorMessages[decodedError.category][decodedError.code]
			);
		}

		selectedAppId = '';
		selectedAppName = '';

		returnToAppsList();
	};

	const loadApplicationDetail = async (appId, groupId) => {
		const appDetail = await httpAdapter.get(`/applications/show/${appId}`);
		applicationListVisible = false;
		applicationDetailVisible = true;

		selectedAppId = appId;
		selectedAppGroupId = groupId;
		selectedAppName = appDetail.data.name;
		selectedAppGroupName = appDetail.data.groupName;
		selectedAppDescription = appDetail.data.description;
		selectedAppPublic = appDetail.data.public;
		selectedAppDateUpdated = appDetail.data.dateUpdated;
		isPublic = selectedAppPublic;
		curlCommandsDecode();
	};

	const returnToApplicationsList = () => {
		generateCredentialsVisible = false;
		generateBindTokenVisible = false;
		password = '';
		bindToken = '';
		applicationDetailVisible = false;
		applicationListVisible = true;
	};

	const returnToAppsList = () => {
		applicationDetailVisible = false;
		applicationListVisible = true;
	};

	const copyPassword = async () => {
		navigator.clipboard.writeText(password);
	};

	const copyBindToken = async () => {
		navigator.clipboard.writeText(bindToken);
	};

	const generatePassword = async (applicationId) => {
		try {
			const res = await httpAdapter.get(`/applications/generate_passphrase/${applicationId}`);
			password = res.data;
			generateCredentialsVisible = true;
		} catch (err) {
			errorMessage(errorMessages['application']['generating.passphrase.error.title'], err.message);
		}
	};

	const generateBindToken = async (applicationId) => {
		try {
			const res = await httpAdapter.get(`/applications/generate_grant_token/${applicationId}`);
			bindToken = res.data;
			generateBindTokenVisible = true;
		} catch (err) {
			errorMessage(errorMessages['application']['generating.token.error.title'], err.message);
		}
	};

	const showCopyPasswordNotification = () => {
		showCopyPasswordNotificationVisible = true;
		setTimeout(() => {
			showCopyPasswordNotificationVisible = false;
		}, fiveSeconds);
	};

	const showCopyBindTokenNotification = () => {
		showCopyBindTokenNotificationVisible = true;
		setTimeout(() => {
			showCopyBindTokenNotificationVisible = false;
		}, fiveSeconds);
	};

	const showCopyCommand = (position) => {
		copiedVisible = position;
		setTimeout(() => {
			copiedVisible = 0;
		}, fiveSeconds);
	};

	const deselectAllApplicationsCheckboxes = () => {
		applicationsAllRowsSelectedTrue = false;
		applicationsRowsSelectedTrue = false;
		applicationsRowsSelected = [];
		let checkboxes = document.querySelectorAll('.apps-checkbox');
		checkboxes.forEach((checkbox) => (checkbox.checked = false));
	};

	const numberOfSelectedCheckboxes = () => {
		let checkboxes = document.querySelectorAll('.apps-checkbox');
		checkboxes = Array.from(checkboxes);
		return checkboxes.filter((checkbox) => checkbox.checked === true).length;
	};

	const deleteTopicApplicationAssociation = async (permissionId) => {
		await httpAdapter.delete(`/application_permissions/${permissionId}`);
		await loadApplicationDetail(applicationDetailId, ApplicationDetailGroupId);
	};

	const decodeError = (errorObject) => {
		errorObject = errorObject.code.replaceAll('-', '_');
		const cat = errorObject.substring(0, errorObject.indexOf('.'));
		const code = errorObject.substring(errorObject.indexOf('.') + 1, errorObject.length);
		return { category: cat, code: code };
	};

	const getGroupVisibilityPublic = async (groupName) => {
		try {
			const res = await httpAdapter.get(`/groups?filter=${groupName}`);
			if (res.data?.content[0]?.public) return true;
			else return false;
		} catch (err) {
			errorMessage(errorMessages['group']['error.loading.visibility'], err.message);
		}
	};
</script>

<svelte:head>
	<title>{messages['application']['tab.title']}</title>
	<meta name="description" content="DDS Permissions Manager Applications" />
</svelte:head>

{#key $refreshPage}
	{#if $isAuthenticated}
		{#await promise then _}
			{#if errorMessageVisible}
				<Modal
					title={errorMsg}
					errorMsg={true}
					errorDescription={errorObject}
					closeModalText={errorMessages['modal']['button.close']}
					on:cancel={() => errorMessageClear()}
					on:keydown={(event) => {
						if (event.which === returnKey) {
							errorMessageClear();
						}
					}}
				/>
			{/if}

			{#if deleteApplicationVisible && !errorMessageVisible}
				<Modal
					actionDeleteApplications={true}
					title="{messages['application']['delete.title']} {applicationsRowsSelected.length > 1
						? messages['application']['delete.multiple']
						: messages['application']['delete.single']}"
					on:cancel={() => {
						if (applicationsRowsSelected?.length === 1 && numberOfSelectedCheckboxes() === 0)
							applicationsRowsSelected = [];

						deleteApplicationVisible = false;
					}}
					on:deleteApplications={async () => {
						await deleteSelectedApplications();
						reloadAllApps();
						deselectAllApplicationsCheckboxes();
						deleteApplicationVisible = false;
					}}
				/>
			{/if}

			{#if addApplicationVisible && !errorMessageVisible}
				<Modal
					title={messages['application']['add']}
					applicationName={true}
					group={true}
					actionAddApplication={true}
					appCurrentGroupPublic={$groupContext?.public}
					on:cancel={() => (addApplicationVisible = false)}
					on:addApplication={(e) => {
						addApplication(
							e.detail.appName,
							e.detail.appDescription,
							e.detail.appPublic,
							e.detail.searchGroups,
							e.detail.selectedGroup
						);
					}}
				/>
			{/if}

			{#if $applicationsTotalSize !== undefined && $applicationsTotalSize != NaN}
				<div class="content">
					{#if !applicationDetailVisible}
						<h1 data-cy="applications">My Applications</h1>

						<form class="searchbox">
							<input
								data-cy="search-applications-table"
								class="searchbox"
								type="search"
								placeholder={messages['application']['search.placeholder']}
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
								>{messages['application']['search.clear.button']}</button
							>
						{/if}

						<img
							src={deleteSVG}
							alt="options"
							class="dot"
							class:button-disabled={(!$isAdmin &&
								!$permissionsByGroup?.find(
									(gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true
								)) ||
								applicationsRowsSelected.length === 0}
							style="margin-left: 0.5rem; margin-right: 1rem"
							on:click={() => {
								if (applicationsRowsSelected.length > 0) deleteApplicationVisible = true;
							}}
							on:keydown={(event) => {
								if (event.which === returnKey) {
									if (applicationsRowsSelected.length > 0) deleteApplicationVisible = true;
								}
							}}
							on:mouseenter={() => {
								deleteMouseEnter = true;
								if (
									$isAdmin ||
									$permissionsByGroup?.find(
										(gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true
									)
								) {
									if (applicationsRowsSelected.length === 0) {
										deleteToolip = messages['application']['delete.tooltip'];
										const tooltip = document.querySelector('#delete-applications');
										setTimeout(() => {
											if (deleteMouseEnter) {
												tooltip.classList.remove('tooltip-hidden');
												tooltip.classList.add('tooltip');
											}
										}, 1000);
									}
								} else {
									deleteToolip =
										messages['application']['delete.tooltip.application.admin.required'];
									const tooltip = document.querySelector('#delete-applications');
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
								if (applicationsRowsSelected.length === 0) {
									const tooltip = document.querySelector('#delete-applications');
									setTimeout(() => {
										if (!deleteMouseEnter) {
											tooltip.classList.add('tooltip-hidden');
											tooltip.classList.remove('tooltip');
										}
									}, 1000);
								}
							}}
						/>
						<span
							id="delete-applications"
							class="tooltip-hidden"
							style="margin-left: 11.3rem; margin-top: -1.8rem"
							>{deleteToolip}
						</span>

						<img
							data-cy="add-application"
							src={addSVG}
							alt="options"
							class="dot"
							class:button-disabled={(!$isAdmin &&
								!$permissionsByGroup?.find(
									(gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true
								)) ||
								!$groupContext}
							on:click={() => {
								if (
									$groupContext &&
									($isAdmin ||
										$permissionsByGroup?.find(
											(gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true
										))
								) {
									addApplicationVisible = true;
								} else if (
									!$groupContext &&
									($permissionsByGroup?.some((gm) => gm.isApplicationAdmin === true) || $isAdmin)
								)
									showSelectGroupContext.set(true);
							}}
							on:keydown={(event) => {
								if (event.which === returnKey) {
									if (
										$groupContext &&
										($isAdmin ||
											$permissionsByGroup?.find(
												(gm) =>
													gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true
											))
									) {
										addApplicationVisible = true;
									} else if (
										!$groupContext &&
										($permissionsByGroup?.some((gm) => gm.isApplicationAdmin === true) || $isAdmin)
									)
										showSelectGroupContext.set(true);
								}
							}}
							on:mouseenter={() => {
								addMouseEnter = true;
								if (
									(!$isAdmin &&
										$groupContext &&
										!$permissionsByGroup?.find(
											(gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true
										)) ||
									(!$isAdmin &&
										!$groupContext &&
										!$permissionsByGroup?.some((gm) => gm.isApplicationAdmin === true))
								) {
									addTooltip = messages['application']['add.tooltip.application.admin.required'];
									const tooltip = document.querySelector('#add-application');
									setTimeout(() => {
										if (addMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, waitTime);
								} else if (
									!$groupContext &&
									($permissionsByGroup?.some((gm) => gm.isApplicationAdmin === true) || $isAdmin)
								) {
									addTooltip = messages['application']['add.tooltip.select.group'];
									const tooltip = document.querySelector('#add-application');
									setTimeout(() => {
										if (addMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, 1000);
								}
							}}
							on:mouseleave={() => {
								addMouseEnter = false;
								const tooltip = document.querySelector('#add-application');
								setTimeout(() => {
									if (!addMouseEnter) {
										tooltip.classList.add('tooltip-hidden');
										tooltip.classList.remove('tooltip');
									}
								}, waitTime);
							}}
						/>
						<span
							id="add-application"
							class="tooltip-hidden"
							style="margin-left: 8rem; margin-top: -1.8rem"
							>{addTooltip}
						</span>
					{/if}

					{#if $applications?.length > 0 && applicationListVisible && !applicationDetailVisible}
						<table
							data-cy="applications-table"
							class="main-table"
							style="margin-top: 0.5rem"
							class:application-table-admin={isApplicationAdmin || $isAdmin}
						>
							<thead>
								<tr style="border-top: 1px solid black; border-bottom: 2px solid">
									{#if ($permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true) || $isAdmin) && !applicationDetailVisible}
										<td>
											<input
												tabindex="-1"
												type="checkbox"
												class="apps-checkbox"
												style="margin-right: 0.5rem"
												bind:indeterminate={applicationsRowsSelectedTrue}
												on:click={(e) => {
													if (e.target.checked) {
														applicationsRowsSelected = $applications;
														applicationsRowsSelectedTrue = false;
														applicationsAllRowsSelectedTrue = true;
													} else {
														applicationsAllRowsSelectedTrue = false;
														applicationsRowsSelectedTrue = false;
														applicationsRowsSelected = [];
													}
												}}
												checked={applicationsAllRowsSelectedTrue}
											/>
										</td>
									{/if}
									<td class="header-column" style="line-height: 2.2rem; min-width: 7rem"
										>{messages['application']['table.column.one']}</td
									>
									<td class="header-column">{messages['application']['table.column.two']}</td>
									<td class="header-column">{messages['application']['table.column.three']}</td>
								</tr>
							</thead>

							{#if $applications.length > 0}
								<tbody>
									{#each $applications as app, i}
										<tr>
											{#if ($permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true) || $isAdmin) && !applicationDetailVisible}
												<td style="width: 2rem">
													<input
														tabindex="-1"
														type="checkbox"
														class="apps-checkbox"
														checked={applicationsAllRowsSelectedTrue}
														on:change={(e) => {
															if (e.target.checked === true) {
																applicationsRowsSelected.push(app);
																// reactive statement
																applicationsRowsSelected = applicationsRowsSelected;
																applicationsRowsSelectedTrue = true;
															} else {
																applicationsRowsSelected = applicationsRowsSelected.filter(
																	(selection) => selection !== app
																);
																if (applicationsRowsSelected.length === 0) {
																	applicationsRowsSelectedTrue = false;
																}
															}
														}}
													/>
												</td>
											{/if}

											<td
												style="cursor: pointer; line-height: 2.2rem; width: max-content"
												on:click={() => {
													applicationDetailId = app.id;
													ApplicationDetailGroupId = app.group;

													loadApplicationDetail(app.id, app.group);
													headerTitle.set(app.name);
													detailView.set(true);
													history.pushState(
														{ path: '/applications' },
														'My Applications',
														'/applications'
													);
												}}
												on:keydown={(event) => {
													if (event.which === returnKey) {
														loadApplicationDetail(app.id, app.group);
													}
												}}
												>{app.name}
											</td>

											<td>{app.id}</td>

											<td style="padding-left: 0.5rem">{app.groupName}</td>

											<td style="cursor: pointer; width:1rem">
												<img
													data-cy="detail-application-icon"
													src={detailSVG}
													height="18rem"
													width="18rem"
													style="margin-left: 2rem; vertical-align: -0.1rem"
													alt="edit user"
													on:click={() => {
														applicationDetailId = app.id;
														ApplicationDetailGroupId = app.group;

														loadApplicationDetail(app.id, app.group);
														headerTitle.set(app.name);
														detailView.set(true);
													}}
													on:keydown={(event) => {
														if (event.which === returnKey) {
															loadApplicationDetail(app.id, app.group);
														}
													}}
												/>
											</td>

											<td
												style="cursor: pointer; text-align: right; padding-right: 0.25rem; width:1rem"
											>
												<!-- svelte-ignore a11y-click-events-have-key-events -->
												<img
													data-cy="delete-application-icon"
													src={deleteSVG}
													alt="delete application"
													width="27rem"
													style="cursor: pointer"
													on:click={() => {
														selectedAppId = app.id;
														selectedAppName = app.name;
														deleteApplicationVisible = true;
													}}
													on:click={() => {
														if (
															!applicationsRowsSelected.some((application) => application === app)
														)
															applicationsRowsSelected.push(app);
														deleteApplicationVisible = true;
													}}
												/>
											</td>
										</tr>
									{/each}
								</tbody>
							{/if}
						</table>
					{:else if !applicationDetailVisible && applicationListVisible}
						<p>
							{messages['application']['empty.applications']}
							<br />
							{#if $groupContext && ($permissionsByGroup?.find((gm) => gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true) || $isAdmin)}
								<!-- svelte-ignore a11y-click-events-have-key-events -->
								<span
									class="link"
									on:click={() => {
										if (
											$groupContext &&
											($permissionsByGroup?.find(
												(gm) =>
													gm.groupName === $groupContext?.name && gm.isApplicationAdmin === true
											) ||
												$isAdmin)
										)
											addApplicationVisible = true;
										else if (
											!$groupContext &&
											($permissionsByGroup?.some((gm) => gm.isApplicationAdmin === true) ||
												$isAdmin)
										)
											showSelectGroupContext.set(true);
									}}
								>
									{messages['application']['empty.applications.action.two']}
								</span>
								{messages['application']['empty.applications.action.result']}
							{:else if !$groupContext && ($permissionsByGroup?.some((gm) => gm.isApplicationAdmin === true) || $isAdmin)}
								{messages['application']['empty.applications.action']}
							{/if}
						</p>
					{/if}

					{#await promiseDetail then _}
						{#if $applications && applicationDetailVisible && !applicationListVisible}
							<ApplicationDetails
								{isApplicationAdmin}
								{selectedAppId}
								{selectedAppGroupId}
								{selectedAppGroupName}
								{selectedAppName}
								{selectedAppDescription}
								{selectedAppPublic}
								{selectedAppDateUpdated}
								appCurrentGroupPublic={$groupContext?.public}
								on:deleteTopicApplicationAssociation={(e) => {
									deleteTopicApplicationAssociation(e.detail);
								}}
								on:reloadAllApps={() => reloadAllApps()}
								on:loadApplicationDetail={() =>
									loadApplicationDetail(selectedAppId, selectedAppGroupId)}
							/>

							<div style="display: inline-flex; height: 7rem; margin-left: -1rem">
								<button
									data-cy="generate-bind-token-button"
									style="width: 13.5rem; height: 3rem; padding: 0 1rem; margin: 4rem 1rem;"
									class="button-blue"
									class:button-disabled={!isApplicationAdmin && !$isAdmin}
									disabled={!isApplicationAdmin && !$isAdmin}
									on:click={() => generateBindToken(selectedAppId)}
								>
									<img
										src={lockSVG}
										alt="generate bind token"
										height="20rem"
										style="vertical-align: middle; filter: invert(); margin-right: 0.4rem; margin-left: -0.5rem"
									/>
									<span style="vertical-align: middle">
										{messages['application.detail']['button.generate.grant.token']}
									</span>
								</button>

								<button
									data-cy="generate-password-button"
									style="width: 13.5rem; height: 3rem; margin-top: 4rem; padding: 0 1rem;"
									class="button-blue"
									class:button-disabled={!isApplicationAdmin && !$isAdmin}
									disabled={!isApplicationAdmin && !$isAdmin}
									on:click={() => generatePassword(selectedAppId)}
								>
									<img
										src={lockSVG}
										alt="generate password"
										height="20rem"
										style="vertical-align: middle; filter: invert(); margin-right: 0.4rem; margin-left: -0.5rem"
									/>
									<span style="vertical-align: middle">
										{messages['application.detail']['button.generate.password']}
									</span>
								</button>
							</div>

							<div style="margin-top: 1.5rem; font-weight: 500;  font-size: 0.9rem">
								{messages['application.detail']['user.name.label']}
								<span style="font-weight: 300">{selectedAppId}</span>
							</div>
							<div style="font-weight: 500;  font-size: 0.9rem; margin-top: 0.5rem;">
								{messages['application.detail']['group.id.label']}
								<span style="font-weight: 300">{selectedAppGroupId}</span>
							</div>

							{#if generateCredentialsVisible}
								<div style="margin-top: 2rem; font-weight: 500; font-size: 0.9rem">
									{messages['application.detail']['password.label']}
								</div>
								<!-- svelte-ignore a11y-click-events-have-key-events -->
								<div
									style="margin-top: 0.3rem;  font-weight: 300; cursor: pointer"
									on:click={() => {
										copyPassword(selectedAppId);
										showCopyPasswordNotification();
									}}
								>
									<span data-cy="generated-password" style="vertical-align: middle">{password}</span
									>
									<img
										src={copySVG}
										alt="copy password"
										height="20rem"
										style="transform: scaleY(-1); filter: contrast(25%); vertical-align: middle; margin-left: 1rem"
										on:click={() => {
											copyPassword(selectedAppId);
											showCopyPasswordNotification();
										}}
									/>
								</div>

								{#if showCopyPasswordNotificationVisible}
									<div class="bubble">{messages['application.detail']['password.copied']}</div>
								{/if}
							{/if}

							{#if generateBindTokenVisible}
								<div style="margin-top: 2rem; font-weight: 500; font-size: 0.9rem">
									{messages['application.detail']['grant.token.label']}

									<textarea
										rows="5"
										cols="50"
										readonly
										style="vertical-align: middle; width: 44.7rem; margin-left: 0.5rem; margin-top: 1rem; resize: none"
										>{bindToken}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										data-cy="bind-token-copy"
										src={copySVG}
										alt="copy bind token"
										height="20rem"
										style="transform: scaleY(-1); filter: contrast(25%); vertical-align: middle; margin-left: 0.5rem; cursor: pointer"
										on:click={() => {
											copyBindToken(selectedAppId);
											showCopyBindTokenNotification();
										}}
									/>
								</div>

								{#if showCopyBindTokenNotificationVisible}
									<div class="bubble-bind-token">
										{messages['application.detail']['grant.token.copied']}
									</div>
								{/if}
							{/if}
							<div class="curl-commands">
								<!-- svelte-ignore missing-declaration -->
								<div>
									{messages['application.detail']['curl.commands.general.instructions']}
								</div>

								<div class="section-title">
									{messages['application.detail']['curl.commands.export.password.label']}
								</div>
								<section style="display:inline-flex;">
									<textarea rows="1" style="width:50rem; resize: none" readonly
										>{curlCommands.appPassword}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										data-cy="curl-command-1-copy"
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommands.appPassword);
											showCopyCommand(1);
										}}
									/>

									{#if copiedVisible === 1}
										<div class="bubble-commands" style="margin-top: -0.4rem">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
								<div class="section-title">
									{messages['application.detail']['curl.commands.export.nonce.label']}
								</div>
								<section style="display:inline-flex;">
									<textarea rows="1" style="width:50rem; resize: none" readonly
										>{curlCommands.appNonce}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										data-cy="curl-command-2-copy"
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommands.appNonce);
											showCopyCommand(2);
										}}
									/>

									{#if copiedVisible === 2}
										<div class="bubble-commands" style="margin-top: -0.4rem">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
								<div class="section-title">
									{messages['application.detail']['curl.commands.authenticate.label']}
								</div>
								<section style="display:inline-flex;">
									<textarea rows="2" style="width:50rem; resize: none" readonly
										>{curlCommandsDecodedCodeThree}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										data-cy="curl-command-3-copy"
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommandsDecodedCodeThree);
											showCopyCommand(3);
										}}
									/>

									{#if copiedVisible === 3}
										<div class="bubble-commands" style="margin-top: -0.4rem">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
								<div class="section-title">
									{messages['application.detail'][
										'curl.commands.download.identity.ca.certificate.label'
									]}
								</div>
								<section style="display:inline-flex;">
									<textarea rows="2" style="width:50rem; resize: none" readonly
										>{curlCommandsDecodedCodeFour}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommandsDecodedCodeFour);
											showCopyCommand(4);
										}}
									/>
									{#if copiedVisible === 4}
										<div class="bubble-commands">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
								<div class="section-title">
									{messages['application.detail'][
										'curl.commands.download.permission.ca.certificate.label'
									]}
								</div>
								<!-- svelte-ignore a11y-click-events-have-key-events -->
								<section style="display:inline-flex;">
									<textarea rows="2" style="width:50rem; resize: none" readonly
										>{curlCommandsDecodedCodeFive}</textarea
									>
									<img
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommandsDecodedCodeFive);
											showCopyCommand(5);
										}}
									/>

									{#if copiedVisible === 5}
										<div class="bubble-commands">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
								<div class="section-title">
									{messages['application.detail']['curl.commands.download.governance.file.label']}
								</div>
								<section style="display:inline-flex;">
									<textarea rows="2" style="width:50rem; resize: none" readonly
										>{curlCommandsDecodedCodeSix}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommandsDecodedCodeSix);
											showCopyCommand(6);
										}}
									/>

									{#if copiedVisible === 6}
										<div class="bubble-commands">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
								<div class="section-title">
									{messages['application.detail']['curl.commands.download.keypair.label']}
								</div>
								<section style="display:inline-flex;">
									<textarea rows="2" style="width:50rem; resize: none" readonly
										>{curlCommandsDecodedCodeSeven}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommandsDecodedCodeSeven);
											showCopyCommand(7);
										}}
									/>

									{#if copiedVisible === 7}
										<div class="bubble-commands">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
								<div class="section-title">
									{messages['application.detail'][
										'curl.commands.download.permissions.document.label'
									]}
								</div>
								<section style="display:inline-flex">
									<textarea rows="2" style="width:50rem; resize: none" readonly
										>{curlCommandsDecodedCodeEight}</textarea
									>
									<!-- svelte-ignore a11y-click-events-have-key-events -->
									<img
										src={copySVG}
										alt="copy code"
										width="20rem"
										height="20rem"
										style="margin-left: 0.5rem; cursor: pointer; transform: scaleY(-1); filter: contrast(25%);"
										on:click={() => {
											navigator.clipboard.writeText(curlCommandsDecodedCodeEight);
											showCopyCommand(8);
										}}
									/>

									{#if copiedVisible === 8}
										<div class="bubble-commands">
											{messages['application.detail']['curl.commands.copied.notification']}
										</div>
									{/if}
								</section>
							</div>
						{/if}
					{/await}
				</div>

				{#if !applicationDetailVisible}
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<div class="pagination">
						<span>{messages['pagination']['rows.per.page']}</span>
						<select
							tabindex="-1"
							on:change={(e) => {
								applicationsPerPage = e.target.value;
								reloadAllApps();
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
							{#if $applicationsTotalSize > 0}
								{1 + applicationsCurrentPage * applicationsPerPage}
							{:else}
								0
							{/if}
							- {Math.min(
								applicationsPerPage * (applicationsCurrentPage + 1),
								$applicationsTotalSize
							)}
							of
							{$applicationsTotalSize}
						</span>

						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagefirstSVG}
							alt="first page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage === 0}
							on:click={() => {
								deselectAllApplicationsCheckboxes();
								if (applicationsCurrentPage > 0) {
									applicationsCurrentPage = 0;
									reloadAllApps();
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pagebackwardsSVG}
							alt="previous page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage === 0}
							on:click={() => {
								deselectAllApplicationsCheckboxes();
								if (applicationsCurrentPage > 0) {
									applicationsCurrentPage--;
									reloadAllApps(applicationsCurrentPage);
								}
							}}
						/>
						<!-- svelte-ignore a11y-click-events-have-key-events -->
						<img
							src={pageforwardSVG}
							alt="next page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage + 1 === $applicationsTotalPages ||
								$applications?.length === undefined}
							on:click={() => {
								deselectAllApplicationsCheckboxes();
								if (applicationsCurrentPage + 1 < $applicationsTotalPages) {
									applicationsCurrentPage++;
									reloadAllApps(applicationsCurrentPage);
								}
							}}
						/>
						<img
							src={pagelastSVG}
							alt="last page"
							class="pagination-image"
							class:disabled-img={applicationsCurrentPage + 1 === $applicationsTotalPages ||
								$applications?.length === undefined}
							on:click={() => {
								deselectAllApplicationsCheckboxes();
								if (applicationsCurrentPage < $applicationsTotalPages) {
									applicationsCurrentPage = $applicationsTotalPages - 1;
									reloadAllApps(applicationsCurrentPage);
								}
							}}
						/>
					</div>
				{/if}
				<RetrievedTimestamp retrievedTimestamp={$retrievedTimestamps['applications']} />
				<p style="margin-top: 8rem">{messages['footer']['message']}</p>
			{/if}
		{/await}
	{/if}
{/key}

<style>
	.content {
		width: 100%;
		min-width: 32rem;
		margin-right: 1rem;
	}

	.dot {
		float: right;
	}

	table.application-table-admin {
		width: 43.5rem;
		line-height: 1rem;
	}

	.main-table {
		min-width: 34rem;
	}

	tr {
		height: 2.2rem;
	}

	p {
		font-size: large;
	}

	.bubble {
		position: absolute;
		background: rgb(0, 0, 0);
		color: #ffffff;
		font-family: Arial;
		font-size: 15px;
		vertical-align: middle;
		text-align: center;
		width: 10.2rem;
		height: 25px;
		border-radius: 10px;
		padding-top: 8px;
		margin-top: 0.75rem;
		left: 22.1rem;
	}

	.bubble:after {
		content: '';
		position: absolute;
		display: block;
		width: 0;
		z-index: 1;
		border-style: solid;
		border-color: #000000 transparent;
		border-width: 0 10px 10px;
		top: -10px;
		left: 26%;
		margin-left: -20px;
	}

	.bubble-bind-token {
		position: relative;
		background: rgb(0, 0, 0);
		color: #ffffff;
		font-family: Arial;
		font-size: 15px;
		vertical-align: middle;
		text-align: center;
		width: 10.2rem;
		height: 25px;
		border-radius: 10px;
		padding-top: 8px;
		margin-top: 0.75rem;
		float: right;
		right: 4rem;
	}

	.bubble-bind-token:after {
		content: '';
		position: absolute;
		display: block;
		width: 0;
		z-index: 1;
		border-style: solid;
		border-color: #000000 transparent;
		border-width: 0 10px 10px;
		top: -10px;
		left: 26%;
		margin-left: -20px;
	}

	.bubble-commands {
		position: relative;
		background: rgb(0, 0, 0);
		color: #ffffff;
		font-size: 0.8rem;
		text-align: center;
		width: 4.5rem;
		height: 1.3rem;
		border-radius: 10px;
		padding-top: 0.1rem;
		margin-left: 0.7rem;
		text-align: center;
		padding-top: 0.3rem;
	}

	.bubble-commands:after {
		content: '';
		position: absolute;
		display: block;
		width: 0;
		height: 0;
		z-index: 1;
		border-style: solid;
		border-color: #000000 transparent;
		top: 0.2rem;
		left: 17%;
		margin-left: -20px;
		border-top: 10px solid transparent;
		border-bottom: 10px solid transparent;
		border-right: 10px solid black;
	}

	.curl-commands {
		margin-top: 3rem;
	}

	section {
		width: 58rem;
	}

	.section-title {
		font-size: 1.1rem;
		font-weight: 600;
		margin-top: 1rem;
		margin-bottom: 0.3rem;
	}

	.header-column {
		font-weight: 600;
	}
</style>

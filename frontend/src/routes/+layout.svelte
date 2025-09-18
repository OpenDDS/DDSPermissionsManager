<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { onLoggedIn, isAuthenticated } from '../stores/authentication';
	import { httpAdapter } from '../appconfig';
	import { browser } from '$app/environment';
	import { page } from '$app/stores';
	import refreshPage from '../stores/refreshPage';
	import lastRefresh from '../stores/lastRefresh';
	import detailView from '../stores/detailView';
	import featureFlagConfigStore from '../stores/featureFlagConfig';
	import permissionsByGroup from '../stores/permissionsByGroup';
	import groupAdminGroups from '../stores/groupAdminGroups';
	import topicAdminTopics from '../stores/topicAdminTopics';
	import applicationAdminApplications from '../stores/applicationAdminApplications';
	import universalSearchList from '../stores/universalSearchList';
	import Modal from '$lib/Modal.svelte';
	import Header from '$lib/Header.svelte';
	import headerTitle from '../stores/headerTitle';
	import Navigation from '$lib/Navigation.svelte';
	import userValidityCheck from '../stores/userValidityCheck';
	import lastActivity from '../stores/lastActivity';
	import userEmail from '../stores/userEmail';
	import modalOpen from '../stores/modalOpen';
	import messages from '$lib/messages.json';
	import '../app.css';

	

	let reminderMessageVisible = false;
	let timer, timeout;

	// Extend Session
	let reminderMsg, reminderObject;

	const userValidityInterval = 180000; // 3 minutes
	const sixtyMin = 3600000;

	let APIisBroadcastingEvents = false;

	let avatarName;

	$: if ($userValidityCheck === 'reloadAllSuperUsers') checkValidity();

	$: if (browser && reminderMessageVisible) {
		document.body.classList.add('modal-open');
	} else if (browser && !reminderMessageVisible) {
		document.body.classList.remove('modal-open');
	}

	const userClicked = () => {
		lastActivity.set(Date.now());

		clearTimeout(timer);
	timer = setTimeout(() => goto(resolve('/api/logout'), true), sixtyMin);
	};

	const reminderMessage = (remMsg, remObj) => {
		reminderMsg = remMsg;
		reminderObject = remObj;
		reminderMessageVisible = true;
	};

	onMount(async () => {
		document.body.addEventListener('click', userClicked);

		window.addEventListener('popstate', (event) => { // eslint-disable-line no-unused-vars
			// Search Button
			if (
				$page.url?.pathname === '/search/' &&
				$headerTitle !== messages['universal.search']['header.title'] &&
				$universalSearchList === false
			) {
				headerTitle.set(messages['universal.search']['header.title']);
				universalSearchList.set(true);
			}
			// Topics Button
			if ($page.url?.pathname === '/topics/' && $headerTitle !== messages['topic']['title'])
				detailView.set('backToList');
			// Applications Button
			if (
				$page.url?.pathname === '/applications/' &&
				$headerTitle !== messages['application']['title']
			)
				detailView.set('backToList');
			if ($page.url?.pathname === '/groups/' && $headerTitle !== messages['group']['title'])
				detailView.set('backToList');
		});

		userClicked();

		await getFeatureFlagConfig();

		await refreshToken_Info();

		setInterval(checkValidity, userValidityInterval);
	});

	const refreshToken = async () => {
		try {
			await httpAdapter.get('/oauth/access_token');
			userValidityCheck.set(false);

			await refreshToken_Info();
		} catch (err) {
			goto(resolve('/api/logout'), true);
		}
	};

	const getFeatureFlagConfig = async () => {
		try {
			const res = await httpAdapter.get('/config');
			if (res.data) {
				featureFlagConfigStore.set(res.data);
				APIisBroadcastingEvents = $featureFlagConfigStore?.DPM_WEBSOCKETS_BROADCAST_CHANGES;
			}
		} catch (err) {
			console.error(err);
		}
	};

	const refreshToken_Info = async (updatedTokenInfo = '') => {
		if (!updatedTokenInfo) {
			const res = await httpAdapter.get(`/token_info`);
			updatedTokenInfo = res.data;

			// We only have JWT_REFRESH_TOKEN and no JWT
			if (res.status === 200 && Object.keys(updatedTokenInfo).length === 1) {
				refreshToken();
				return;
			}

			if (updatedTokenInfo === '') {
				return;
			}
		}
		userValidityCheck.set(false);

		permissionsByGroup.set(updatedTokenInfo.permissionsByGroup);

		if ($permissionsByGroup) {
			let groupsList = [];
			let topicsList = [];
			let applicationsList = [];

			for (const group of $permissionsByGroup) {
				if (group.isGroupAdmin) groupsList.push(group);
				if (group.isTopicAdmin) topicsList.push(group);
				if (group.isApplicationAdmin) applicationsList.push(group);
			}
			groupAdminGroups.set(groupsList);
			topicAdminTopics.set(topicsList);
			applicationAdminApplications.set(applicationsList);
		}

		onLoggedIn(updatedTokenInfo);
		avatarName = updatedTokenInfo.username.slice(0, 1).toUpperCase();
		userEmail.set(updatedTokenInfo.username);

		if (!APIisBroadcastingEvents) {
			refreshPageFn();
		}
	};

	const refreshPageFn = () => {
		if ($modalOpen === false) {
			clearTimeout(timeout);
			refreshPage.set(Date.now());
			if ($lastRefresh === null) lastRefresh.set($refreshPage);
		} else {
			timeout = setTimeout(refreshPageFn, 5000);
		}
	};

	const checkValidity = async () => {
		try {
			const fiftyFiveMin = 3300000;

			if (Date.now() - $lastActivity > fiftyFiveMin) {
				const msg = messages['refresh_token']['five_min_reminder'];
				reminderMessage('Session Expiration', msg);
			}

			const res = await httpAdapter.get(`/token_info`);

			// We don't have any of the two JWT tokens
			if (res.status === 204 || res.status === 404) goto(resolve('/api/logout'), true);

			// IF- We only have a JWT_REFRESH_TOKEN; ELSE- We have both JWT tokens
			if (res.status === 200 && Object.keys(res.data).length === 1) refreshToken();
			else refreshToken_Info(res.data);
		} catch (err) {
			const oneHour = 3600000;

			// If: the user is not active for 1hr we log them out, Else: we refresh the session
			if (Date.now() - $lastActivity > oneHour) goto(resolve('/api/logout'), true);
			else {
				refreshToken();
			}
		}
	};
</script>

{#if reminderMessageVisible}
	<Modal
		title={reminderMsg}
		reminderMsg={true}
		reminderDescription={reminderObject}
		closeModalText="No"
		on:cancel={() => {
			reminderMessageVisible = false;
		}}
		on:extendSession={() => {
			reminderMessageVisible = false;
			refreshToken();
		}}
	/>
{/if}

<div
	class:grid-container={$isAuthenticated}
	class:grid-container-not-authenticated={!$isAuthenticated}
>
	<div class="grid-item-horizontal">
		<Header {avatarName} userEmail={$userEmail} />
	</div>
	{#if $isAuthenticated}
		<div class="grid-item-vertical-nav">
			<Navigation />
		</div>
		<div class="grid-item-vertical-main">
			<main>
				<slot />
			</main>
		</div>
	{:else}
		<div class="grid-item-vertical-main-not-authenticated">
			<main>
				<slot />
			</main>
		</div>
	{/if}
</div>

<style>
	.grid-container {
		display: grid;
		width: fit-content;
		grid-template-columns: 0.3fr 1fr 1fr;
		grid-template-rows: auto;
		grid-template-areas:
			'header header header'
			'nav main main';
	}

	.grid-container-not-authenticated {
		display: grid;
		width: 100%;
		grid-template-columns: 0.3fr 1fr 1fr;
		grid-template-rows: auto;
		grid-template-areas:
			'header header header'
			'main-not-authenticated main-not-authenticated main-not-authenticated'
			'footer footer footer';
	}

	.grid-item-horizontal {
		grid-area: header;
	}

	.grid-item-vertical-nav {
		grid-area: nav;
	}

	.grid-item-vertical-main {
		grid-area: main;
		grid-column: 2;
	}

	.grid-item-vertical-main-not-authenticated {
		grid-area: main-not-authenticated;
	}

	main {
		padding: 1rem;
		box-sizing: border-box;
	}
</style>

<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAdmin } from '../stores/authentication';
	import usersSVG from '../icons/users.svg';
	import groupsSVG from '../icons/groups.svg';
	import topicsSVG from '../icons/topics.svg';
	import appsSVG from '../icons/apps.svg';
	import grantsSVG from '../icons/grants.svg';
	import expirationsSVG from '../icons/expirations.svg';
	import topicSetsSVG from '../icons/topicsets.svg';
	import timerSVG from '../icons/timer.svg';
	import searchSVG from '../icons/search.svg';
	import { page } from '$app/stores';
	import groupContext from '../stores/groupContext';
	import { httpAdapter } from '../appconfig';
	import topicsA from '../stores/topicsA';
	import applications from '../stores/applications';
	import users from '../stores/users';
	import groups from '../stores/groups';
	import groupMembershipList from '../stores/groupMembershipList';
	import applicationsTotalPages from '../stores/applicationsTotalPages';
	import applicationsTotalSize from '../stores/applicationsTotalSize';
	import topicsTotalPages from '../stores/topicsTotalPages';
	import topicsTotalSize from '../stores/topicsTotalSize';
	import superUsersTotalPages from '../stores/superUsersTotalPages';
	import superUsersTotalSize from '../stores/superUsersTotalSize';
	import groupMembershipsTotalPages from '../stores/groupMembershipsTotalPages';
	import groupMembershipsTotalSize from '../stores/groupMembershipsTotalSize';
	import groupsTotalPages from '../stores/groupsTotalPages';
	import groupsTotalSize from '../stores/groupsTotalSize';
	import detailView from '../stores/detailView';
	import universalSearchList from '../stores/universalSearchList';
	import messages from '$lib/messages.json';
	import topicSets from '../stores/topicSets';

	const itemsPerPage = 10;

	let groupMembershipListArray = [];

	const preloadTopics = async (page = 0) => {
		let resTopics;
		if ($groupContext) {
			resTopics = await httpAdapter.get(
				`/topics?page=${page}&size=${itemsPerPage}&group=${$groupContext.id}`
			);
		} else {
			resTopics = await httpAdapter.get(`/topics?page=${page}&size=${itemsPerPage}`);
		}
		if (resTopics.data) {
			topicsTotalPages.set(resTopics.data.totalPages);
			topicsTotalSize.set(resTopics.data.totalSize);
		}
		topicsA.set(resTopics.data.content);
	};


	const preloadTopicSets = async (page = 0) => {
		let resTopicSets;
		if ($groupContext) {
			resTopicSets = await httpAdapter.get(
					`/topic-sets?page=${page}&size=${itemsPerPage}&group=${$groupContext.id}`
				);
		} else {
			resTopicSets = await httpAdapter.get(`/topic-sets?page=${page}&size=${itemsPerPage}`);
		}
		if (resTopicSets.data) {
			topicsTotalPages.set(resTopicSets.data.totalPages);
			topicsTotalSize.set(resTopicSets.data.totalSize);
		}
		topicSets.set(resTopicSets?.data?.content);
	};

	const preloadApps = async (page = 0) => {
		let res;
		if ($groupContext) {
			res = await httpAdapter.get(
				`/applications?page=${page}&size=${itemsPerPage}&group=${$groupContext.id}`
			);
		} else {
			res = await httpAdapter.get(`/applications?page=${page}&size=${itemsPerPage}`);
		}

		if (res.data) {
			applicationsTotalPages.set(res.data.totalPages);
			applicationsTotalSize.set(res.data.totalSize);
		}
		applications.set(res.data.content);
	};

	const preloadSuperUsers = async (page = 0) => {
		const res = await httpAdapter.get(`/admins?page=${page}&size=${itemsPerPage}`);

		if (res.data) {
			superUsersTotalPages.set(res.data.totalPages);
			superUsersTotalSize.set(res.data.totalSize);
		}
		users.set(res.data.content);
	};

	const preloadGroupMemberships = async (page = 0) => {
		let res;
		if ($groupContext) {
			res = await httpAdapter.get(
				`/group_membership?page=${page}&size=${itemsPerPage}&group=${$groupContext.id}`
			);
		} else {
			res = await httpAdapter.get(`/group_membership?page=${page}&size=${itemsPerPage}`);
		}

		if (res.data) {
			groupMembershipsTotalPages.set(res.data.totalPages);
			groupMembershipsTotalSize.set(res.data.totalSize);
		}
		if (res.data.content) {
			createGroupMembershipList(res.data.content, res.data.totalPages);
		} else {
			groupMembershipList.set();
		}
	};

	const createGroupMembershipList = async (data, totalPages, totalSize) => {
		data?.forEach((groupMembership) => {
			let newGroupMembership = {
				applicationAdmin: groupMembership.applicationAdmin,
				groupAdmin: groupMembership.groupAdmin,
				topicAdmin: groupMembership.topicAdmin,
				groupName: groupMembership.permissionsGroupName,
				groupId: groupMembership.permissionsGroup,
				groupMembershipId: groupMembership.id,
				userId: groupMembership.permissionsUser,
				userEmail: groupMembership.permissionsUserEmail
			};
			groupMembershipListArray.push(newGroupMembership);
		});
		groupMembershipList.set(groupMembershipListArray);

		groupMembershipListArray = [];
		groupMembershipsTotalPages.set(totalPages);
		if (totalSize !== undefined) groupMembershipsTotalSize.set(totalSize);
	};

	const preloadGroups = async (page = 0) => {
		const res = await httpAdapter.get(`/groups?page=${page}&size=${itemsPerPage}`);

		if (res.data) {
			groupsTotalPages.set(res.data.totalPages);
			groupsTotalSize.set(res.data.totalSize);
		}

		groups.set(res.data.content);
		groupsTotalPages.set(res.data.totalPages);
	};
</script>

<nav>
	<ul>
		<!-- svelte-ignore a11y-click-events-have-key-events -->
		<li
			class:active={$page.url.pathname === '/groups/'}
			on:mouseenter={() => {
				if (!$groups) preloadGroups();
			}}
			on:click={() => detailView.set('backToList')}
		>
			<a sveltekit:prefetch href="/groups">
				<img src={groupsSVG} alt="groups" class="menu-icon" />{messages['navigation']['item.one']}
			</a>
		</li>

		<li
			class:active={$page.url.pathname === '/users/'}
			on:mouseenter={() => {
				if ($isAdmin && !$users) {
					preloadSuperUsers();
					preloadGroupMemberships();
				} else if (!$isAdmin && !$groupMembershipList) preloadGroupMemberships();
			}}
		>
			<a sveltekit:prefetch href="/users">
				<img src={usersSVG} alt="users" class="menu-icon" />{messages['navigation']['item.two']}
			</a>
		</li>

		<!-- svelte-ignore a11y-click-events-have-key-events -->
		<li
			class:active={$page.url.pathname === '/topics/'}
			on:mouseenter={() => {
				if (!$topicsA) preloadTopics();
			}}
			on:click={() => detailView.set('backToList')}
		>
			<a sveltekit:prefetch href="/topics">
				<img src={topicsSVG} alt="topics" class="menu-icon" />{messages['navigation']['item.three']}
			</a>
		</li>

		<!-- svelte-ignore a11y-click-events-have-key-events -->
		<li
			class:active={$page.url.pathname === '/applications/'}
			on:mouseenter={() => {
				if (!$applications) preloadApps();
			}}
			on:click={() => detailView.set('backToList')}
		>
			<a sveltekit:prefetch href="/applications">
				<img src={appsSVG} alt="applications" class="menu-icon" />{messages['navigation'][
					'item.four'
				]}
			</a>
		</li>

		<!-- svelte-ignore a11y-click-events-have-key-events -->
		<li
			class:active={$page.url.pathname === '/search/'}
			on:click={() => universalSearchList.set(true)}
		>
			<a sveltekit:prefetch href="/search">
				<img src={searchSVG} alt="search" class="menu-icon" />{messages['navigation']['item.five']}
			</a>
		</li>

		<!-- svelte-ignore a11y-click-events-have-key-events -->
		<li class:active={$page.url.pathname === '/grants/'} on:mouseenter={() => {}} on:click={() => detailView.set('backToList')}>
			<a sveltekit:prefetch href="/grants">
				<img src={grantsSVG} alt="grants" class="menu-icon" />{messages['navigation']['item.six']}
			</a>
		</li>

		<div class="nested-list">
			<!-- svelte-ignore a11y-click-events-have-key-events -->
			<li class:active={$page.url.pathname === '/durations/'} on:mouseenter={() => {}}>
				<a sveltekit:prefetch href="/durations">
					<img src={timerSVG} alt="durations" class="menu-icon" />{messages['navigation'][
						'item.seven'
					]}
				</a>
			</li>

			<!-- svelte-ignore a11y-click-events-have-key-events -->
			<li class:active={$page.url.pathname === '/action-intervals/'} on:mouseenter={() => {}} 			on:click={() => detailView.set('backToList')}>
				<a sveltekit:prefetch href="/action-intervals">
					<img src={expirationsSVG} alt="action-intervals" class="menu-icon" />{messages['navigation'][
						'item.eight'
					]}
				</a>
			</li>

			<!-- svelte-ignore a11y-click-events-have-key-events -->
			<li class:active={$page.url.pathname === '/topic-sets/'} on:mouseenter={() => {if (!$topicSets) preloadTopicSets();}} 			on:click={() => detailView.set('backToList')}>
				<a sveltekit:prefetch href="/topic-sets">
					<img src={topicSetsSVG} alt="topic-sets" class="menu-icon" />{messages['navigation'][
						'item.nine'
					]}
				</a>
			</li>
		</div>
	</ul>
</nav>

<style>
	nav {
		width: 18.4rem;
		justify-content: center;
		margin-top: 2.5rem;
	}

	nav a {
		display: flex;
		height: 100%;
		align-items: center;
		padding: 0 1em;
		color: var(--heading-color);
		font-weight: 600;
		font-size: 1rem;
		letter-spacing: 0.1em;
	}

	nav a img {
		padding-right: 0.5rem;
	}

	a:hover {
		text-decoration: none;
	}

	ul {
		padding: 0;
		height: 3em;
		list-style: none;
		border-radius: 50px;
		border-width: 0px;
		border-style: solid;
		background-size: contain;
	}

	li {
		position: relative;
		height: 100%;
	}

	li.active {
		border-radius: 50px;
		border-width: 0px;
		border-style: solid;
		background-color: #e8def8;
	}

	.menu-icon {
		scale: 65%;
		margin: 0.1rem 0 0.1rem 0;
	}

	.nested-list {
		margin-left: 2rem;
		display: flex;
		flex-direction: column;
		height: 8rem;
	}
</style>

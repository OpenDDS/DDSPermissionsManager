<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import DDSLock from '../icons/logo.png';
	import logoutSVG from '../icons/logout.svg';
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import { isAuthenticated, isAdmin } from '../stores/authentication';
	import { httpAdapter } from '../appconfig';
	import headerTitle from '../stores/headerTitle';
	import detailView from '../stores/detailView';
	import pagebackwardsSVG from '../icons/pagebackwards.svg';
	import groupsSVG from '../icons/groups.svg';
	import topicsSVG from '../icons/topics.svg';
	import appsSVG from '../icons/apps.svg';
	import selectPromptSVG from '../icons/selectPrompt.svg';
	import groupContext from '../stores/groupContext';
	import ComboBox from './ComboBox.svelte';
	import refreshPage from '../stores/refreshPage';
	import lastRefresh from '../stores/lastRefresh';
	import permissionBadges from '../stores/permissionBadges';
	import showSelectGroupContext from '../stores/showSelectGroupContext';
	import tooltips from '$lib/tooltips.json';
	import createItem from '../stores/createItem';
	import messages from '$lib/messages.json';

	export let avatarName;
	export let userEmail;

	// Headers Constants
	let topicsHeader = messages['header']['topics.header.constant'];
	let applicationsHeader = messages['header']['applications.header.constant'];
	let groupsHeader = messages['header']['groups.header.constant'];
	let searchHeader = messages['header']['search.header.constant'];

	// Constants
	const waitTime = 1000;
	const returnKey = 13;

	// Tooltip
	let isGroupAdminToolip, isTopicAdminTooltip, isApplicationAdminTooltip;
	let isGroupAdminMouseEnter = false;
	let isTopicAdminMouseEnter = false;
	let isApplicationAdminMouseEnter = false;

	// Avatar
	let avatarDropdownMouseEnter = false;
	let avatarDropdownVisible = false;

	// Group Context Badges
	let permissionsForGroupContext;
	let isGroupAdminInContext, isTopicAdminInContext, isApplicationAdminInContext;

	// Reactive statements
	$: if ($groupContext?.id) getPermissionsForGroupContext();
	else {
		isGroupAdminInContext = false;
		isTopicAdminInContext = false;
		isApplicationAdminInContext = false;
	}

	$: if ($refreshPage !== $lastRefresh && $groupContext?.id) {
		getPermissionsForGroupContext();
		lastRefresh.set($refreshPage);
	}

	// Shows the select group context prompt briefly and then removes the prompt
	$: if ($showSelectGroupContext)
		setTimeout(() => {
			showSelectGroupContext.set(false);
		}, waitTime * 2);

	detailView.set();

	const getPermissionsForGroupContext = async () => {
		permissionsForGroupContext = await httpAdapter.get(
			`/group_membership?page=0&size=1&filter=${userEmail}&group=${$groupContext.id}`
		);

		if (permissionsForGroupContext?.data?.content || $isAdmin) {
			permissionsForGroupContext = permissionsForGroupContext.data.content;

			if ((permissionsForGroupContext?.length > 0 && permissionsForGroupContext[0].groupAdmin) || $isAdmin) {
				isGroupAdminInContext = true;
				isGroupAdminToolip = tooltips['isGroupAdmin'];
			} else {
				isGroupAdminInContext = false;
				isGroupAdminToolip = tooltips['isNotGroupAdmin'];
			}
			if ((permissionsForGroupContext?.length > 0 && permissionsForGroupContext[0].topicAdmin) || $isAdmin) {
				isTopicAdminInContext = true;
				isTopicAdminTooltip = tooltips['isTopicAdmin'];
			} else {
				isTopicAdminInContext = false;
				isTopicAdminTooltip = tooltips['isNotTopicAdmin'];
			}
			if ((permissionsForGroupContext?.length > 0 && permissionsForGroupContext[0].applicationAdmin) || $isAdmin) {
				isApplicationAdminInContext = true;
				isApplicationAdminTooltip = tooltips['isApplicationAdmin'];
			} else {
				isApplicationAdminInContext = false;
				isApplicationAdminTooltip = tooltips['isNotApplicationAdmin'];
			}
		}

		permissionBadges.set({
			isGroupAdminInContext: isGroupAdminInContext,
			isTopicAdminInContext: isTopicAdminInContext,
			isApplicationAdminInContext: isApplicationAdminInContext,
			isGroupAdminToolip: isGroupAdminToolip,
			isTopicAdminTooltip: isTopicAdminTooltip,
			isApplicationAdminTooltip: isApplicationAdminTooltip
		});
	};
</script>

<header>
	<div class="header-bar">
		<div style="display:inline-flex">
			{#if $isAuthenticated}
				{#await permissionsForGroupContext then _}
					<div style="display:inline-flex">
						<img src={DDSLock} alt="logo" class="logo" />
						<div class="logo-text">{messages['header']['title']}</div>
					</div>
					{#key $refreshPage}
						<div
							class:permission-badges={$groupContext?.id}
							class:permission-badges-hidden={!$groupContext?.id}
						>
							<!-- svelte-ignore a11y-click-events-have-key-events -->
							<img
								src={groupsSVG}
								alt="Group Admin"
								width="23rem"
								height="23rem"
								class:permission-badges-blue={isGroupAdminInContext || $isAdmin}
								class:permission-badges-grey={!isGroupAdminInContext && !$isAdmin}
								on:mouseenter={() => {
									isGroupAdminMouseEnter = true;
									if (isGroupAdminInContext || $isAdmin)
										isGroupAdminToolip = tooltips['createUserAllowed'] + $groupContext.name;
									else isGroupAdminToolip = tooltips['createUserNotAllowed'];
									const tooltip = document.querySelector('#is-group-admin');
									setTimeout(() => {
										if (isGroupAdminMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, 1000);
								}}
								on:mouseleave={() => {
									isGroupAdminMouseEnter = false;
									const tooltip = document.querySelector('#is-group-admin');
									setTimeout(() => {
										if (!isGroupAdminMouseEnter) {
											tooltip.classList.add('tooltip-hidden');
											tooltip.classList.remove('tooltip');
										}
									}, 1000);
								}}
								on:click={() => {
									if (isGroupAdminInContext || $isAdmin) {
										if ($page.url.pathname === '/users/') createItem.set('user');
										else {
											createItem.set('user');
											goto(`/users`, true);
										}
									}
								}}
							/>

							<span
								id="is-group-admin"
								class="tooltip-hidden"
								style="margin-top: 1.8rem; margin-left: -5rem"
								>{isGroupAdminToolip}
							</span>

							<!-- svelte-ignore a11y-click-events-have-key-events -->
							<img
								src={topicsSVG}
								alt="Topic Admin"
								width="23rem"
								height="23rem"
								class:permission-badges-blue={isTopicAdminInContext || $isAdmin}
								class:permission-badges-grey={!isTopicAdminInContext && !$isAdmin}
								on:mouseenter={() => {
									isTopicAdminMouseEnter = true;
									if (isTopicAdminInContext || $isAdmin)
										isTopicAdminTooltip = tooltips['createTopicAllowed'] + $groupContext.name;
									else isTopicAdminTooltip = tooltips['createTopicNotAllowed'];
									const tooltip = document.querySelector('#is-topic-admin');
									setTimeout(() => {
										if (isTopicAdminMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, 1000);
								}}
								on:mouseleave={() => {
									isTopicAdminMouseEnter = false;
									const tooltip = document.querySelector('#is-topic-admin');
									setTimeout(() => {
										if (!isTopicAdminMouseEnter) {
											tooltip.classList.add('tooltip-hidden');
											tooltip.classList.remove('tooltip');
										}
									}, 1000);
								}}
								on:click={() => {
									if (isTopicAdminInContext || $isAdmin) {
										if ($page.url.pathname === '/topics/') createItem.set('topic');
										else {
											createItem.set('topic');
											goto(`/topics`, true);
										}
									}
								}}
							/>

							<span
								id="is-topic-admin"
								class="tooltip-hidden"
								style="margin-top: 1.8rem; margin-left: -5rem"
								>{isTopicAdminTooltip}
							</span>

							<!-- svelte-ignore a11y-click-events-have-key-events -->
							<img
								src={appsSVG}
								alt="Application Admin"
								width="23rem"
								height="23rem"
								class:permission-badges-blue={isApplicationAdminInContext || $isAdmin}
								class:permission-badges-grey={!isApplicationAdminInContext && !$isAdmin}
								on:mouseenter={() => {
									isApplicationAdminMouseEnter = true;
									if (isApplicationAdminInContext || $isAdmin)
										isApplicationAdminTooltip =
											tooltips['createApplicationAllowed'] + $groupContext.name;
									else isApplicationAdminTooltip = tooltips['createApplicationNotAllowed'];
									const tooltip = document.querySelector('#is-application-admin');
									setTimeout(() => {
										if (isApplicationAdminMouseEnter) {
											tooltip.classList.remove('tooltip-hidden');
											tooltip.classList.add('tooltip');
										}
									}, 1000);
								}}
								on:mouseleave={() => {
									isApplicationAdminMouseEnter = false;
									const tooltip = document.querySelector('#is-application-admin');
									setTimeout(() => {
										if (!isApplicationAdminMouseEnter) {
											tooltip.classList.add('tooltip-hidden');
											tooltip.classList.remove('tooltip');
										}
									}, 1000);
								}}
								on:click={() => {
									if (isApplicationAdminInContext || $isAdmin) {
										if ($page.url.pathname === '/applications/') createItem.set('application');
										else {
											createItem.set('application');
											goto(`/applications`, true);
										}
									}
								}}
							/>

							<span
								id="is-application-admin"
								class="tooltip-hidden"
								style="margin-top: 1.8rem; margin-left: -5rem"
								>{isApplicationAdminTooltip}
							</span>
						</div>
					{/key}
				{/await}

				<img
					src={selectPromptSVG}
					alt="select group"
					width="35rem"
					height="35rem"
					class:show-prompt-hidden={!$showSelectGroupContext}
					class:show-prompt={$showSelectGroupContext}
					class:bounce={$showSelectGroupContext}
				/>

				<div class="combobox">
					<ComboBox isGroupContext={true} />
				</div>
			{:else}
				<div style="display:inline-flex">
					<img src={DDSLock} alt="logo" class="logo" />
					<div class="logo-text">{messages['header']['title']}</div>
				</div>
			{/if}
		</div>

		{#if $isAuthenticated}
			<div class="header-title">
				{#if $detailView && $headerTitle !== topicsHeader && $headerTitle !== applicationsHeader && $headerTitle !== searchHeader && $page.url.pathname !== '/search/'}
					<!-- svelte-ignore a11y-click-events-have-key-events -->
					<img
						class="go-back"
						src={pagebackwardsSVG}
						alt="back to topics"
						on:click={() => detailView.set('backToList')}
					/>
				{/if}

				<div id="header-label" style="vertical-align: middle; margin-left: 1rem">
					{$headerTitle}
				</div>
			</div>

			<!-- svelte-ignore a11y-no-noninteractive-tabindex -->
			<div
				data-cy="avatar-dropdown"
				tabindex="0"
				class="dot"
				on:mouseenter={() => (avatarDropdownMouseEnter = true)}
				on:mouseleave={() => {
					setTimeout(() => {
						if (!avatarDropdownMouseEnter) avatarDropdownVisible = false;
					}, waitTime);
					avatarDropdownMouseEnter = false;
				}}
				on:click={() => (avatarDropdownVisible = !avatarDropdownVisible)}
				on:keydown={(event) => {
					if (event.which === returnKey) {
						avatarDropdownVisible = !avatarDropdownVisible;
					}
				}}
			>
				{avatarName}
			</div>
		{/if}
	</div>
</header>

<hr style="border-color: rgba(0, 0, 0, 0.15);" />

{#if avatarDropdownVisible}
	<div style="display: flex; justify-content: flex-end; margin-bottom: -1.87rem">
		<table
			class="avatar-dropdown"
			class:hidden={!$isAuthenticated}
			on:mouseenter={() => (avatarDropdownMouseEnter = true)}
			on:mouseleave={() => {
				setTimeout(() => {
					setTimeout(() => {
						if (!avatarDropdownMouseEnter) avatarDropdownVisible = false;
					});
				}, waitTime);
				avatarDropdownMouseEnter = false;
			}}
		>
			<tr>
				<td style="font-weight: 300">{userEmail}</td>
			</tr>

			<tr>
				<td
					data-cy="logout-button"
					on:click={() => goto('/api/logout', true)}
					on:keydown={(event) => {
						if (event.which === returnKey) {
							goto('/api/logout', true);
						}
					}}
					on:focusout={() => (avatarDropdownVisible = false)}
				>
					<a href="/api/logout" style="float:right">
						{messages['header']['avatar.logout.button']}
						<img src={logoutSVG} alt="logout" class="icon-logout" />
					</a>
				</td>
			</tr>
		</table>
	</div>
{/if}

<style>
	.combobox {
		margin: -1.5rem;
	}

	table > tr > td {
		line-height: 2.4rem;
		padding: 0 1rem 0 1rem;
	}

	.header-bar {
		display: flex;
		align-self: center;
		height: 2rem;
		justify-content: space-between;
		width: 100%;
	}

	.logo {
		height: 30px;
		width: 30px;
		margin-left: 1.55rem;
	}

	.logo-text {
		align-self: center;
		margin-left: 1rem;
		font-size: 1.2rem;
		letter-spacing: -0.02rem;
		min-width: max-content;
	}

	.header-title {
		display: inline-flex;
		justify-content: center;
		align-items: center;
		font-size: 1.2rem;
		text-align: center;
	}

	.icon-logout {
		width: 21px;
		height: 21px;
		align-self: center;
		padding-left: 1rem;
	}

	.dot {
		position: sticky;
		position: -webkit-sticky; /* Safari */
		align-self: center;
		right: 1vw;
		height: 2.4rem;
		width: 2.4rem;
		min-width: 2.4rem;
		border-radius: 50%;
		font-weight: 600;
		font-size: 0.9rem;
	}

	.dot:hover {
		cursor: pointer;
	}

	.avatar-dropdown {
		position: sticky;
		position: -webkit-sticky; /* Safari */
		align-self: center;
		right: 0vw;
		float: right;
		width: fit-content;
		margin: -0.65rem 0 -2.4rem 1vw;
		background-color: #f3edf7;
		padding-left: 1rem;
		font-size: 0.9rem;
		font-weight: 500;
		line-height: 2.8rem;
		box-shadow: 0px 1px 2px rgba(0, 0, 0, 0.3), 0px 2px 6px 2px rgba(0, 0, 0, 0.15);
	}

	.go-back {
		vertical-align: middle;
		margin-left: 2rem;
		cursor: pointer;
		width: 30px;
		height: 30px;
	}

	a {
		display: flex;
		color: unset;
		height: 2.4rem;
		align-items: center;
	}

	a:hover {
		text-decoration: none;
		color: unset;
	}

	@-webkit-keyframes bounce {
		0%,
		20%,
		50%,
		80%,
		100% {
			-webkit-transform: translateY(0);
		}
		40% {
			-webkit-transform: translateY(-30px);
		}
		60% {
			-webkit-transform: translateY(-15px);
		}
	}

	.show-prompt-hidden {
		margin: auto 2rem;
		visibility: hidden;
	}

	.show-prompt {
		margin: auto 2rem;
		filter: invert(14%) sepia(79%) saturate(5378%) hue-rotate(0deg) brightness(78%) contrast(124%);
		-webkit-animation-duration: 1.1s;
		animation-duration: 1.1s;
		-webkit-animation-fill-mode: both;
		animation-fill-mode: both;
	}

	@keyframes bounce {
		0%,
		20%,
		50%,
		80%,
		100% {
			transform: translateX(0);
		}
		40% {
			transform: translateX(-20px);
		}
		60% {
			transform: translateX(-15px);
		}
	}

	.bounce {
		-webkit-animation-name: bounce;
		animation-name: bounce;
	}
</style>

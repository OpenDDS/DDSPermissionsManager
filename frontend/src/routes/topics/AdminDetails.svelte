<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAuthenticated } from '../../stores/authentication';
	import { onMount } from 'svelte';
	import { httpAdapter } from '../../appconfig';

	export let groupId;

	// Promises
	let promise;

	// Topic Admins
	let groupMembership;
	let admins;

	const fetchAndUpdateAdmin = async () => {
		let adminEmails = [];

		promise = await httpAdapter.get(`/group_membership?page=0&size=100&group=${groupId}`);
		groupMembership = promise.data.content;

		adminEmails = groupMembership
			.filter((member) => member.topicAdmin)
			.map((member) => member.permissionsUserEmail);

		return adminEmails;
	};

	onMount(async () => {
		try {
			admins = await fetchAndUpdateAdmin();
		} catch (err) {
		}
	});
</script>

{#if $isAuthenticated}

	{#await promise then _}
		{#if admins}
			<div>
				<strong>Admins</strong>
			</div>
			<div style="display: inline-flex;">
				<ul class="topics-details">
					{#each admins as email}
						<li>{email}</li>
					{/each}
				</ul>
			</div>
		{/if}
	{/await}
{/if}

<style>
	.topics-details {
		font-size: 12pt;
		height: auto;
		width: 41rem;
		/* margin: 0 0; */
		margin-top: 1.6rem;
	}
</style>

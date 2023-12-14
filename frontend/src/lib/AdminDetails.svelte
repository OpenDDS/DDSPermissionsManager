<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import { isAuthenticated } from '../stores/authentication';
	import { onMount } from 'svelte';
	import { httpAdapter } from '../appconfig';

	export let groupId;
	export let adminCategory;

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
			.filter((member) => member[`${adminCategory}Admin`])
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
			{#each admins as admin, i}
				<div>
					{admin}{#if i < admins.length - 1},{/if}
				</div>
			{/each}
		{/if}
	{/await}
{/if}

<style>
	.admin-details {
		color: red;
		list-style-type: none;
		padding-left: 0;
	}
</style>

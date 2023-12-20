<script>
	import messages from '$lib/messages.json';
	import groupsSVG from '../../../icons/groups.svg';
	import topicsSVG from '../../../icons/topics.svg';
	import appsSVG from '../../../icons/apps.svg';

	export let users;
</script>

<table
data-cy="users-table"
id="group-memberships-table"
style="margin-top:0.5rem; min-width: 50rem; width:max-content"
>
	<thead>
		<tr style="border-top: 1px solid black; border-bottom: 2px solid">
			<td class="header-column" style="font-stretch:ultra-condensed; width:fit-content">
				{messages['user']['table.user.column.one']}
			</td>
			<td class="header-column" style="font-stretch:ultra-condensed; width:6.25rem">
				<center>{messages['user']['table.user.column.three']}</center>
			</td>
			<td class="header-column" style="font-stretch:ultra-condensed; width:6rem">
				<center>{messages['user']['table.user.column.four']}</center>
			</td>
			<td class="header-column" style="font-stretch:ultra-condensed; width:7.8rem">
				<center>{messages['user']['table.user.column.five']}</center>
			</td>
		</tr>
	</thead>
	<tbody>
	{#each $users as user, i}
		<tr>
			<td style="width:fit-content">{user.userEmail}</td>
			<td>
				<center>
					{#if user.groupAdmin}
						<img
							src={groupsSVG}
							alt="group admin"
							width="21rem"
							height="21rem"
							style="vertical-align:middle"
						/>
					{:else}
						-
					{/if}
				</center>
			</td>
			<td>
				<center
					>{#if user.topicAdmin}
						<img
							src={topicsSVG}
							alt="topic admin"
							width="21rem"
							height="21rem"
							style="vertical-align:middle"
						/>
					{:else}
						-
					{/if}
				</center>
			</td>
			<td data-cy="is-application-admin">
				<center
					>{#if user.applicationAdmin}
						<img
							src={appsSVG}
							alt="application admin"
							width="21rem"
							height="21rem"
							style="vertical-align:middle"
						/>
					{:else}
						-
					{/if}
				</center>
			</td>
		</tr>
	{/each}
	</tbody>
</table>
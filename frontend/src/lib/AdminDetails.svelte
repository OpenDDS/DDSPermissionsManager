<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	export let admins;
</script>

{#if admins}
   {#each admins as admin, i (admin)}
	   <div>
		   {admin}{#if i < admins.length - 1},{/if}
	   </div>
   {/each}
{/if}

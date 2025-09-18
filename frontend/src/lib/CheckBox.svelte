<!-- Copyright 2023 DDS Permissions Manager Authors-->
<script>
	import messages from '$lib/messages.json';
	import closeSVG from '../icons/close.svg';
	import nonEmptyInputField from '../stores/nonEmptyInputField';
	export let label = '';
	export let checked = false;
	export let partitionList = [];

	let inputValue;

	// Constants
	const returnKey = 13;

	function toggle() {
		checked = !checked;
		if (!checked) partitionList = [];
	}
</script>


<div class="checkbox-container">
	<input type="checkbox" class="checkbox" on:click={toggle} bind:checked />
	<!-- svelte-ignore a11y-label-has-associated-control -->
	<label>{label}</label>

	{#if checked}
		<input
			style="margin-left: 0.75rem"
			bind:value={inputValue}
			placeholder={messages['modal']['associate.application.input.access.placeholder']}
			on:keydown={(event) => {
				if (event.which === returnKey) {
					if (inputValue) {
						if (partitionList.filter((partition) => partition === inputValue)?.length === 0) {
							partitionList.push(inputValue.trim());
							partitionList = partitionList;
						}

						inputValue = '';
					}
				}
			}}
			on:blur={() => {
				let store = [];
				if (inputValue && $nonEmptyInputField) {
					store.push(...$nonEmptyInputField);
					store.push(label + ': ' + inputValue);
					nonEmptyInputField.set(store);
				} else if (inputValue) {
					store.push(label + ': ' + inputValue);
					nonEmptyInputField.set(store);
				} else if (!inputValue && $nonEmptyInputField) {
					nonEmptyInputField.set(
						$nonEmptyInputField.filter((partition) => !partition.includes(label))
					);
				} else if (!inputValue && !$nonEmptyInputField) {
					nonEmptyInputField.set(false);
				}
			}}
		/>
		<button
			class:button-blue={inputValue}
			class:button-disabled={!inputValue}
			disabled={!inputValue}
			style="border-radius: 15px"
			on:click={() => {
				if (inputValue) {
					if (partitionList.filter((partition) => partition === inputValue)?.length === 0) {
						partitionList.push(inputValue.trim());
						partitionList = partitionList;
					}

					inputValue = '';

					if ($nonEmptyInputField)
						nonEmptyInputField.set(
							$nonEmptyInputField.filter((partition) => !partition.includes(label))
						);
				}
			}}>+</button
		>
	{/if}
</div>

<div style="margin: 0.5rem 0 0.5rem 0">
	<ul class="stylish-list">
		{#each partitionList as item (item)}
			<li class="list-item">
				<span class="list-item-text">{item}</span>
				
				<button aria-label="remove partition"  on:click={() => (partitionList = partitionList.filter((partition) => partition !== item))}><img
					src={closeSVG}
					height="15rem"
					alt=""
					class="close-button icon-button"
					style="cursor: pointer; margin-top: 0.15rem"
					
				/></button>
			</li>
		{/each}
	</ul>
</div>

<style>
.icon-button {
	background: none;
	border: none;
	padding: 0;
	margin: 0;
	cursor: pointer;
}

	.checkbox-container {
		display: flex;
		align-items: center;
		cursor: pointer;
	}

	.checkbox {
		width: 20px;
		height: 20px;
		border: 2px solid #000;
		border-radius: 3px;
		display: flex;
		justify-content: center;
		align-items: center;
	}

	input {
		width: 6.65rem;
		max-height: 1.1rem;
	}

	button {
		width: 1.5rem;
		height: 1.5rem;
		margin: 0 0.5rem 0 0.5rem;
	}

	label {
		margin-left: 8px;
	}

	.stylish-list {
		list-style: none;
		padding: 0;
		margin: 0;
		font-size: 16px;
		line-height: 1.5;
		color: #333;
	}

	.list-item {
		display: flex;
		justify-content: space-between;
		font-size: 0.9rem;
		padding: 5px 10px;
		border-bottom: 1px solid #ccc;
		width: 13.75rem;
		transition: background-color 0.2s ease;
	}

	.list-item:hover {
		background-color: #f5f5f5;
	}

	.list-item-text {
		font-weight: 600;
	}
</style>

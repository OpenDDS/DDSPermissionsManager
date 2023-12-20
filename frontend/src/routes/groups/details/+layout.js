import groupDetailsButton from '../../../stores/groupDetailsButton';

export function load() {
	return {
		menuOptions: [
			{ id: 1, label: 'Users', active: false },
			{ id: 2, label: 'Topics', active: false },
			{ id: 3, label: 'Applications', active: false },
			{ id: 4, label: 'Grants', active: false }
		],
		groupButton: new Promise(resolve => {
			const unsubscribe = groupDetailsButton.subscribe(value => {
				resolve({
					value
				});
			});
		})
	};
}

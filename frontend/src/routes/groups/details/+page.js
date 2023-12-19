import groupContext from '../../../stores/groupContext';

export async function load() {

	return new Promise(resolve => {
		const unsubscribe = groupContext.subscribe(value => {
			resolve({
				group: value
			});
		});
	})
}

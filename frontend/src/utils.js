// Copyright 2023 DDS Permissions Manager Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
export const updateRetrievalTimestamp = (store, path) => {
	const currentTime = new Date();

	store.update((storeValues) => {
		return {
			...storeValues,
			[path]: currentTime
		};
	});
};

export const convertFromMilliseconds = (durationInMilliseconds, durationType) => {
	let convertedDuration = 0;
	switch (durationType) {
		case 'Minute':
			convertedDuration = durationInMilliseconds / 60000;
			break;
		case 'Day':
			convertedDuration = durationInMilliseconds / 86400000;
			break;
		case 'Month':
			convertedDuration = durationInMilliseconds / 2629746000;
			break;
		case 'Year':
			convertedDuration = durationInMilliseconds / 31556952000;
			break;
	}
	return convertedDuration;
};

export const getDurationInMilliseconds = (duration, durationType) => {
	let durationInMilliseconds = 0;
	switch (durationType) {
		case 'Minute':
			durationInMilliseconds = duration * 60000;
			break;
		case 'Day':
			durationInMilliseconds = duration * 86400000;
			break;
		case 'Month':
			durationInMilliseconds = duration * 2629746000;
			break;
		case 'Year':
			durationInMilliseconds = duration * 31556952000;
			break;
	}
	return durationInMilliseconds;
};

export default null;

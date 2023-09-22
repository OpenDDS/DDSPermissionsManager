export const updateRetrievalTimestamp = (store, path) => {
    const currentTime = new Date();

    store.update(storeValues => {
        return {
            ...storeValues,
            [path]: currentTime
        }
    });
};

export default null;
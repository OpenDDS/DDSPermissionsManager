# Permissions Manager UI Documentation

This project was built using [`Svelte Kit`](https://kit.svelte.dev/docs/introduction).

## Install Node.JS dependencies

```bash
npm install
```

## Starting the UI with Hot Module Replacement

```bash
# Start Svelte Kit in localhost:3000

npm run dev
```

Note that by default you will be forwarded to port 8080 which corresponds to the deployed version of the app (when the backend is started). You can simply switch to port 3000 to make use of HMR.

```bash
# or start the server and open the app in a new browser tab

npm run dev -- --open

```

## Svelte Kit Config

`svelte.config.js` is the configuration file for the adapter (used to deploy), to prerender by default, etc.

## Main Resources

Within `/src` folder we find the following:

`lib` folder contains utilities such as the group search `ComboBox`, the `Header`, the `Modal`, the `Switch`, and the `Navigation` menu. It also contains the `externalized messages` and `curl commands` (JSON format)

`routes` folder contains the structure of the URLs.
First `+layout.svelte` is loaded, followed by `+page.svelte` at the `/src` root level.
After authentication is completed, we are redirected to `/groups` that serves as the home page.

`app.css` defines the global styles.

`appconfig.js` provides a function with a base URL (pointing to the backend) plus some extra configuration parameters to make every requests more simple, without the need to retype these values over and over again.

`stores` folder contains all the Svelte stores (state variables) that share data among all components that imports them.


## Application Functionality

### Authentication

It's handled by `/stores/authentication.js` which is a set of derived stores that looks into `token_info` and concludes whether the user `isAuthenticated` and determines if the user `isAdmin`.

In `+layout.svelte`, we keep a record of the last click the user triggered on the screen, and if the user is not active for more than 1hr, we automatically log them out.
In the case the user is active within the hour (which is the expiration time of the JWT token), we automatically refresh the token for them, extending the session.

### Constants

`Topics` and `Applications` have a 'detail view' that depends on `topicsHeader` and `applicationHeader` constants within `Header.svelte`

There are also other constants such as `waitTime` which defines the time delay the UI waits before sending requests to the backend when the user is typing, how long the drowdowns linger before dissapearing, and so on. Every timer uses this constant to have consistency across the app and a single point to modify all values.

`minNameLength` defines the minimum length for names.

`groupsDropdownSuggestion` defines how many results we retrieve from a given request to the backend.

`searchStringLength` defines how many characters need to be typed before the search functionality activates.

### Modals

All modals use the same custom component, but differ according to the parameters passed when creating them.
Modals `dispatch messages` to the parent component that usually handles the work.

Here\'s an example of a modal with `title`, `topicName`, `group` and `actionAddTopic` parameters. It also handles the dispatch messages `cancel` and `addTopic` that forwards an event object with data from the modal back to the parent component that called it, in this case `/topics/+page.svelte`:

```bash
<Modal
    title="Add Topic"
    topicName={true}
    group={true}
    actionAddTopic={true}
    on:cancel={() => (addTopicVisible = false)}
    on:addTopic={(e) => {
    newTopicName = e.detail.newTopicName;
    searchGroups = e.detail.searchGroups;
    selectedGroup = e.detail.selectedGroup;
    anyApplicationCanRead = e.detail.anyApplicationCanRead;
    selectedApplicationList = e.detail.selectedApplicationList;
    addTopic();
}}
/>
```

### Users Screen

This screen is composed of two components: `/users/+page.svelte` which provides the `Super Users` functionality and `/users/GroupMembership.svelte` that provides the `Users` functionality. This second component is a subcomponent of the first one.

Every time the user changes permissions in the `Users` screen, the `refreshToken` function is called.
This will refresh the JWT token (reading the JWT_REFRESH_TOKEN cookie) and re-rendering the whole UI to accomodate the new permissions.

### Applications & Topics Screen

Both of these screens have a 'detail view' of the table items.
Topic details is a separate component called `TopicDetails.svelte` within `/src/routes/topics`.
In the case of Application details, it's embedded within the main application component located at `/src/routes/applications/+page.svelte`.

### Infinite scrolling

Implemented with `svelte-inview` plugin.

### Optimizations

`renderAvatar` and `loginCompleted` functions handle the flickering or the undesired display of components as the application is loading.

### Important styles common to components

Every component has a style class called `content` that defines that main table\'s width on each screen.

### Group Context

All the permissions defined by selecting a `group context` are set within `Header.svelte` and shared to the rest of the components through `permissionBadges` store.

The `Groups` screen uses `permissionBadges` store to sync the table with the `group context widget` in the header.

### Tooltips

To create a tooltip you need to add a `<span>Tooltip Message</span>` element with the class `tooltip`.
The `span` is usually initialized with the class `tooltip-hidden`, because the tooltip is hidden by default, and it changes to the class `tooltip` when the mouse enters the element.

Here's an example:

```bash
<span
    id="delete-super-users"
    class="tooltip-hidden"
    style="margin-left: 9.5rem; margin-top: -1.8rem"
    >Select super users to delete
</span>

```

To adjust the position of the message we use vanilla JavaScript to set attributes like this:

```bash
const tooltip = document.querySelector('#delete-super-users');
    setTimeout(() => {
        if (deleteMouseEnter) {
            tooltip.classList.remove('tooltip-hidden');
            tooltip.classList.add('tooltip');
            tooltip.setAttribute('style', 'margin-left:10.2rem; margin-top: -1.8rem');
        }
    }, 1000);
```

## Testing with Cypress.io

To run all test on [`Cypress`](https://docs.cypress.io/guides/overview/why-cypress):

```bash
npx cypress run
```

To open Cypress and run tests individually:

```bash
npx cypress open
```

Videos of the tests can be found within the folder `cypress/videos`.

## Cypress Config

`cypress.config.js` is the configuration file where you can set the base URL for the tests.

## Creating New Tests

Within the folder `cypress/e2e` create a new file with `.cy.js` extension.
Tests in this folder will automatically be performed by Cypress.

Copyright 2023 DDS Permissions Manager Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

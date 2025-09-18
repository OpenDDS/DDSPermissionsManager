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
/* global cy, describe, beforeEach, it */
/// <reference types="Cypress" />

describe('Topics Capabilities', () => {
	beforeEach(() => {
		cy.login('unity-admin', 'password');
		cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
		cy.visit('http://localhost:8080/');
		cy.wait('@tokenInfo');
	});

	it('should add a new topic set to group Alpha.', () => {
		cy.visit('/topic-sets');

		cy.get('[data-cy="group-input"]').type('alpha');

		cy.wait(500);

		cy.get('[data-cy="group-input"]').type('{downArrow}').type('{enter}');

		cy.get('[data-cy="add-topic"]').click();

		cy.get('[data-cy="topic-name"]').type('Test Topic Set Alpha');

		cy.get('[data-cy="button-add-topic"]').click({ force: true });

		cy.get('.header-title').contains('Test Topic Set Alpha');
	});

	it('should search and add a topic to the topic set.', () => {
		cy.visit('/topic-sets');

		cy.get('[data-cy="group-input"]').type('alpha');

		cy.wait(500);

		cy.get('[data-cy="group-input"]').type('{downArrow}').type('{enter}');

		cy.contains('Test Topic Set Alpha').click();

		cy.get('.svelte-select').type('topic');

		cy.wait(1000);

		cy.contains('Test Topic 123').click();

		cy.get('[data-cy="topic"]').contains('Test Topic 123');
	});

	it('should delete the topic from the topic set.', () => {
		cy.visit('/topic-sets');

		cy.get('[data-cy="group-input"]').type('alpha');

		cy.wait(500);

		cy.get('[data-cy="group-input"]').type('{downArrow}').type('{enter}');

		cy.contains('Test Topic Set Alpha').click();
		cy.contains('Test Topic 123').click();

		cy.get('[data-cy="topic"]').contains('Test Topic 123');

		cy.get('[data-cy="delete-topic-icon"]').click();

		cy.get('[data-cy="delete-topic"]').click();

		cy.get('[data-cy="topic"]').should('not.exist');
	});

	it('should delete the topic set.', () => {
		cy.visit('/topic-sets');

		cy.get('[data-cy="group-input"]').type('alpha');

		cy.wait(500);

		cy.get('[data-cy="group-input"]').type('{downArrow}').type('{enter}');

		cy.get('[data-cy="delete-topic-icon"]').last().click();

		cy.get('[data-cy="delete-topic"]').click();
	});
});

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

describe('title should say DDS Permissions Manager', () => {
	beforeEach(() => {
		cy.login('unity-admin', 'password');
		cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
		cy.visit('http://localhost:8080/');
		cy.wait('@tokenInfo');
	});

	it('should have correct titles', () => {
		cy.get('title').invoke('text').should('equal', 'My Groups | DDS Permissions Manager');

		cy.get('h1').invoke('text').should('equal', 'My Groups');

		cy.visit('/users');

		cy.get('[data-cy="users"]').invoke('text').should('equal', 'My Users');

		cy.get('[data-cy="super-users"]').invoke('text').should('equal', 'My Super Users');

		cy.visit('/topics');

		cy.get('[data-cy="topics"]').invoke('text').should('equal', 'My Topics');

		cy.visit('/applications');

		cy.get('[data-cy="applications"]').invoke('text').should('equal', 'My Applications');
	});
});

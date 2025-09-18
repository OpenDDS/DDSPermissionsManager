/* global cy, describe, beforeEach, it */
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
describe('Users capabilities', () => {
	beforeEach(() => {
		cy.login('unity-admin', 'password');
		cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
		cy.visit('http://localhost:8080/');
		cy.wait('@tokenInfo');
	});

	it('should add a new user', () => {
		cy.visit('/users');

		cy.get('[data-cy="group-input"]').type('alpha');

		cy.wait(500);

		cy.get('[data-cy="group-input"]').type('{downArrow}').type('{enter}');

		cy.get('[data-cy="add-user"]').click();

		cy.get('[data-cy="email-input"]').type('user@email.com');

		cy.get('[data-cy="button-add-user"]').click();

		cy.get('td').should('contain.text', 'user@email.com');
	});
});

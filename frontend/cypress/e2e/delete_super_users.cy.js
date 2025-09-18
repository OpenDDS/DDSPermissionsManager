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

describe('Delete super users', () => {
	beforeEach(() => {
		cy.login('unity-admin', 'password');
		cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
		cy.visit('http://localhost:8080/');
		cy.wait('@tokenInfo');
	});

	it('should delete pasumarthis@test.test.com', () => {
		cy.visit('/users');

		cy.wait(500);

		cy.get('td')
			.contains('pasumarthis@test.test.com')
			.siblings()
			.find('[data-cy="delete-super-users-icon"]')
			.click();

		cy.get('[data-cy="delete-super-user"]').click();

		cy.get('td').should('not.eq', 'pasumarthis@test.test.com');
	});
});

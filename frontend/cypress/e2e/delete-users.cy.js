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
describe('Delete Users', () => {
	beforeEach(() => {
		cy.login('unity-admin', 'password');
		cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
		cy.visit('http://localhost:8080/');
		cy.wait('@tokenInfo');
	});

	it('should delete user montesm@test.test.com', () => {
		cy.visit('/users');

		cy.get(
			':nth-child(2) > [style="cursor: pointer; text-align: right; padding-right: 0.25rem; width: 1rem;"] > [data-cy="delete-users-icon"]'
		).click();

		cy.get('[data-cy="delete-user"]').click();

		cy.get('td').should('not.eq', 'montesm@test.test.com');
	});
});

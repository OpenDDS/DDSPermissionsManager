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

describe('should verify the values of all the tables', () => {
	beforeEach(() => {
		cy.visit('http://localhost:8080/api/logout');
		cy.login('unity-admin', 'password');
		cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
		cy.visit('http://localhost:8080/');
		cy.wait('@tokenInfo');
	});

	it('should filter Users correctly', () => {
		cy.visit('/users');
		cy.get('[data-cy="search-users-table"]').type('test');

		cy.get('[data-cy="users-table"] > tbody > tr').should('have.length', 2);
	});

	it('should filter Super Users correctly', () => {
		cy.visit('/users');
		cy.get('[data-cy="search-super-users-table"]').type('belloned');

		cy.get('[data-cy="super-users-table"] > tbody > tr').should('have.length', 1);
	});

	it('should filter Topics correctly', () => {
		cy.visit('/topics');
		cy.get('[data-cy="search-topics-table"]').type('456');

		cy.get('[data-cy="topics-table"] > tbody > tr').should('have.length', 1);
	});

	it('should filter Applications correctly', () => {
		cy.visit('/applications');
		cy.get('[data-cy="search-applications-table"]').type('two');

		cy.get('[data-cy="applications-table"] > tbody > tr').should('have.length', 1);
	});

	it('should filter Groups correctly', () => {
		cy.visit('/groups');
		cy.get('[data-cy="search-groups-table"]').type('delta');

		cy.get('[data-cy="groups-table"] > tbody > tr').should('have.length', 1);
	});
});

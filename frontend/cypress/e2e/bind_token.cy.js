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
/// <reference types="Cypress" />

describe('Bind Token Functionality', () => {
    beforeEach(() => {
        cy.login('unity-admin', 'password');
        cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
        cy.visit('http://localhost:8080/');
        cy.wait('@tokenInfo');
    });

    it('should go into application details and generate a bind token', () => {
        cy.visit('/applications');

        cy.get('td').contains('Application One')
        .click();

        cy.get('[data-cy="generate-bind-token-button"]')
        .click();

        cy.wait(500);

        cy.get('textarea').invoke('val').should('contains', 'eyJh');
        });
});
 


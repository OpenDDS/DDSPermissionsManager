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

describe('Applications Capabilities', () => {
    beforeEach(() => {
        cy.login('unity-admin', 'password');
        cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
        cy.visit('http://localhost:8080/');
        cy.wait('@tokenInfo');
    });
   
    it('should edit the name of the second application', () => {
        cy.visit('/applications');

        cy.get('td').contains('Test Application')
        .click();

        cy.wait(500);
        
        cy.get('[data-cy="edit-application-icon"]')
        .click({force: true});

        cy.get('[data-cy="application-name"]')
        .clear()
        .type("New Application");

        cy.get('[data-cy="save-application"]')
        .click();

        cy.wait(1000);

        cy.visit('/applications');

        cy.get('td').should('not.eq', 'Test Application');

        cy.get('td').contains('New Application', {timeout: 7000});
    });
});

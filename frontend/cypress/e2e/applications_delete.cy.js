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

    it('should delete the new application', () => {
        cy.visit('/applications');

        cy.get('td').contains('New Application').siblings().find('[data-cy="delete-application-icon"]')
        .click(); 

        cy.get('[data-cy="delete-application"]')
        .click();

        cy.get('td').should('not.eq', 'New Application');
        
        });

    // it('should copy the first curl command in application details', () => {
    //     cy.visit('/applications');

    //     cy.get('td').contains('Application One')
    //     .click();

    //     cy.get('[data-cy="curl-command-1-copy"]')
    //     .click();

    //     cy.window().then((win) => {
    //         win.navigator.clipboard.readText().then((text) => {
    //           text.should("eq", "curl -c cookies.txt -H'Content-Type: application\/json' -d\"${json}\" ${DPM_URL}/api/login");
    //         });
    //       });
    // });
});

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

describe('Action Interval Capabilities', () => {
    beforeEach(() => {
        cy.login('unity-admin', 'password');
        cy.intercept('http://localhost:8080/api/token_info').as('tokenInfo');
        cy.visit('http://localhost:8080/');
        cy.wait('@tokenInfo');
    });

    it('should add a new action interval to group Alpha.', () => {
        cy.visit('/action-intervals');

        cy.get('[data-cy="group-input"]')
            .type("alpha")
            .wait(700)
            .type('{downArrow}')
            .type('{enter}');

        cy.get('[data-cy="add-action-interval"]')
            .click();

        cy.get('[data-cy="action-interval-name-input"]').type('First Action Interval');

        cy.get(':nth-child(3) > .date-time-field').type('{selectall}{backspace}').type('2023-10-01').type('{enter}');
        cy.get(':nth-child(4) > .date-time-field').type('{selectall}{backspace}').type('2023-10-11').type('{enter}');

        cy.get('[data-cy="button-add-action-interval"]').click();

        cy.get('[data-cy="action-interval-name"]').contains('First Action Interval');
    });

    it('should delete First Action Interval', () => {
        cy.visit('/action-intervals');

        cy.get('[data-cy="group-input"]')
            .type("alpha");

        cy.wait(500);

        cy.get('[data-cy="delete-action-interval-icon"]').first().click();

        cy.get('[data-cy="delete-topic"]').click();
    });
});

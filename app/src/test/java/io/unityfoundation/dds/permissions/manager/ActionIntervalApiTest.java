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
package io.unityfoundation.dds.permissions.manager;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.exception.DPMErrorResponse;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.ActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.CreateActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.GrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import io.unityfoundation.dds.permissions.manager.testing.util.EntityLifecycleUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "ActionIntervalApiTest")
@MicronautTest
public class ActionIntervalApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    @Client("/api")
    HttpClient client;

    @Inject
    EntityLifecycleUtil entityUtil;

    @Inject
    GroupRepository groupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GroupUserRepository groupUserRepository;

    @Inject
    DbCleanup dbCleanup;

    @Inject
    MockSecurityService mockSecurityService;

    @Inject
    AuthenticationFetcherReplacement mockAuthenticationFetcher;

    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
    }

    @Requires(property = "spec.name", value = "ActionIntervalApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "ActionIntervalApiTest")
    @Replaces(SecurityService.class)
    @Singleton
    static class MockSecurityService extends SecurityServiceReplacement {
    }

    @Nested
    class WhenAsAdmin {
        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        //create
        @Test
        void cannotCreateOnItsOwnWithoutAGroupAssociation() {
            CreateActionIntervalDTO abcDTO = new CreateActionIntervalDTO();
            abcDTO.setName("abc");

            HttpRequest<?> request = HttpRequest.POST("/action_intervals", abcDTO);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request, ActionIntervalDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_INTERVAL_REQUIRES_GROUP_ASSOCIATION.equals(dpmErrorResponse.getCode())));
        }

        @Test
        void canCreateWithGroupAssociation() {
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionInterval = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionInterval.isPresent());
        }

        @Test
        void canCreateWithActionAssociation() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create application
            response = entityUtil.createApplication("Application123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = entityUtil.createGrantDuration("Duration1", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // show grant duration
            request = HttpRequest.GET("/grant_durations/"+durationOptional.get().getId());
            response = blockingClient.exchange(request, GrantDurationDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDurationShowResponse = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationShowResponse.isPresent());
            assertNotNull(grantDurationShowResponse.get().getGrantCount());
            assertEquals(0, grantDurationShowResponse.get().getGrantCount());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grant
            response = entityUtil.createApplicationGrant(applicationGrantToken, theta.getId(), "TempGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());

            // create action interval
            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalOptional.isPresent());
            ActionIntervalDTO xyzActionInterval = actionIntervalOptional.get();

            // show action interval
            request = HttpRequest.GET("/action_intervals/"+xyzActionInterval.getId());
            response = blockingClient.exchange(request, ActionIntervalDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalShowResponse = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalShowResponse.isPresent());
            assertNotNull(actionIntervalShowResponse.get().getId());
            assertNotNull(actionIntervalShowResponse.get().getActionCount());
            assertEquals(0, actionIntervalShowResponse.get().getActionCount());

            // create action with action interval
            entityUtil.createAction(grantDTOOptional.get().getId(), xyzActionInterval.getId());

            // show action interval
            request = HttpRequest.GET("/action_intervals/"+xyzActionInterval.getId());
            response = blockingClient.exchange(request, ActionIntervalDTO.class);
            assertEquals(OK, response.getStatus());
            actionIntervalShowResponse = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalShowResponse.isPresent());
            assertNotNull(actionIntervalShowResponse.get().getId());
            assertNotNull(actionIntervalShowResponse.get().getActionCount());
            assertEquals(1, actionIntervalShowResponse.get().getActionCount());
        }

        @Test
        public void cannotCreateWithNullNorWhitespace() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create action intervals
            CreateActionIntervalDTO actionIntervalDTO = new CreateActionIntervalDTO();
            actionIntervalDTO.setGroupId(theta.getId());

            request = HttpRequest.POST("/action_intervals/", actionIntervalDTO);

            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, ActionIntervalDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_INTERVAL_NAME_CANNOT_BE_BLANK_OR_NULL.equals(dpmErrorResponse.getCode())));

            actionIntervalDTO.setName("     ");
            request = HttpRequest.POST("/action_intervals", actionIntervalDTO);
            HttpRequest<?> finalRequest1 = request;
            HttpClientResponseException exception1 = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, ActionIntervalDTO.class);
            });
            assertEquals(BAD_REQUEST, exception1.getStatus());
            listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            dpmErrorResponses = listOptional.get();
            list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_INTERVAL_NAME_CANNOT_BE_BLANK_OR_NULL.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void cannotCreateWithNameLessThanThreeCharacters() {
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createActionInterval("A", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_INTERVAL_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void createShouldTrimNameWhitespaces() {
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createActionInterval("   Abc123  ", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionInterval = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionInterval.isPresent());
            assertEquals("Abc123", actionInterval.get().getName());
        }

        @Test
        public void cannotUpdateGroupAssociation() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalOptional.isPresent());
            ActionIntervalDTO abcActionInterval = actionIntervalOptional.get();

            // update attempt
            abcActionInterval.setGroupId(zeta.getId());
            request = HttpRequest.PUT("/action_intervals/"+abcActionInterval.getId(), abcActionInterval);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_INTERVAL_CANNOT_UPDATE_GROUP_ASSOCIATION.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void canUpdateNameAndDates() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalOptional.isPresent());
            ActionIntervalDTO savedActionInterval = actionIntervalOptional.get();
            assertEquals("Abc123", savedActionInterval.getName());

            // with different name and dates
            savedActionInterval.setName("NewName123");

            Instant updateStartInstant = Instant.now().plus(2, ChronoUnit.DAYS);
            savedActionInterval.setStartDate(updateStartInstant);

            Instant updateEndInstant = Instant.now().plus(5, ChronoUnit.DAYS);
            savedActionInterval.setEndDate(updateEndInstant);

            request = HttpRequest.PUT("/action_intervals/"+savedActionInterval.getId(), savedActionInterval);
            response = blockingClient.exchange(request, ActionIntervalDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> updatedActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(updatedActionIntervalOptional.isPresent());
            ActionIntervalDTO updatedActionInterval = updatedActionIntervalOptional.get();
            assertEquals("NewName123", updatedActionInterval.getName());
            assertEquals(updateStartInstant, updatedActionInterval.getStartDate());
            assertEquals(updateEndInstant, updatedActionInterval.getEndDate());

            // just date
            updateStartInstant = Instant.now().plus(3, ChronoUnit.DAYS);
            savedActionInterval.setStartDate(updateStartInstant);

            request = HttpRequest.PUT("/action_intervals/"+savedActionInterval.getId(), savedActionInterval);
            response = blockingClient.exchange(request, ActionIntervalDTO.class);
            assertEquals(OK, response.getStatus());
            updatedActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(updatedActionIntervalOptional.isPresent());
            updatedActionInterval = updatedActionIntervalOptional.get();
            assertEquals(updateStartInstant, updatedActionInterval.getStartDate());
        }

        @Test
        public void cannotCreateActionIntervalWithSameNameInGroup() {
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalOptional.isPresent());
            ActionIntervalDTO savedActionInterval = actionIntervalOptional.get();
            assertEquals("Abc123", savedActionInterval.getName());

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createActionInterval("Abc123", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_INTERVAL_ALREADY_EXISTS.equals(dpmErrorResponse.getCode())));
        }

        //show
        @Test
        void canShowActionIntervalAssociatedToAGroup(){
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalOptional.isPresent());
            ActionIntervalDTO xyzActionInterval = actionIntervalOptional.get();

            // show action interval
            request = HttpRequest.GET("/action_intervals/"+xyzActionInterval.getId());
            response = blockingClient.exchange(request, ActionIntervalDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalShowResponse = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalShowResponse.isPresent());
            assertNotNull(actionIntervalShowResponse.get().getId());
            assertNotNull(actionIntervalShowResponse.get().getGroupId());
            assertNotNull(actionIntervalShowResponse.get().getGroupName());
            assertNotNull(actionIntervalShowResponse.get().getStartDate());
            assertNotNull(actionIntervalShowResponse.get().getEndDate());
        }

        // list all action intervals from all groups
        @Test
        void canListAllActionIntervalsAndActionIntervalsWithSameNameCanExistSitewide(){
            // Group - Action Intervals
            // ---
            // Green - Xyz789
            // Yellow - Abc123 & Xyz789

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Green");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> greenOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(greenOptional.isPresent());
            SimpleGroupDTO green = greenOptional.get();

            response = entityUtil.createGroup("Yellow");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> yellowOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(yellowOptional.isPresent());
            SimpleGroupDTO yellow = yellowOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", yellow.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Xyz789", green.getId());
            assertEquals(OK, response.getStatus());

            // site-wide test
            response = entityUtil.createActionInterval("Xyz789", yellow.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/action_intervals");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(3, actionIntervalPage.get().getContent().size());
        }

        @Test
        void canListAllActionIntervalsWithFilter(){
            // Group - Action Intervals
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            // support case-insensitive
            request = HttpRequest.GET("/action_intervals?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/action_intervals?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
        }

        @Test
        void canLisActionIntervalsWithGroupId(){
            // Group - Action Intervals
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> abcOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(abcOptional.isPresent());
            ActionIntervalDTO abcActionInterval = abcOptional.get();

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzOptional.isPresent());
            ActionIntervalDTO xyzActionInterval = xyzOptional.get();

            // can list both action intervals
            request = HttpRequest.GET("/action_intervals?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            Map map = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals(map.get("id"), xyzActionInterval.getId().intValue());

            request = HttpRequest.GET("/action_intervals?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            map = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcActionInterval.getId().intValue());

            // in addition to group, support filter param
            request = HttpRequest.GET("/action_intervals?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            map = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcActionInterval.getId().intValue());
        }

        @Test
        void canListAllActionIntervalsNameInAscendingOrderByDefault(){
            // Group - Action Intervals
            // ---
            // Theta - Xyz789 & Def456
            // Zeta - Abc123 & Def456

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Def456", theta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/action_intervals");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            List<Map> actionIntervals = actionIntervalPage.get().getContent();

            // action interval names sorted
            List<String> actionIntervalNames = actionIntervals.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(actionIntervalNames.stream().sorted().collect(Collectors.toList()), actionIntervalNames);

            // group names should be sorted by action interval
            List<String> defActionIntervals = actionIntervals.stream().filter(map -> {
                String actionIntervalName = (String) map.get("name");
                return actionIntervalName.equals("Def456");
            }).flatMap(map -> Stream.of((String) map.get("groupName"))).collect(Collectors.toList());
            assertEquals(defActionIntervals.stream().sorted().collect(Collectors.toList()), defActionIntervals);
        }

        @Test
        void canListAllActionIntervalsNameInDescendingOrder(){
            // Group - Action Intervals
            // ---
            // Theta - Xyz789
            // Zeta - Abc123 & Def456

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/action_intervals?sort=name,desc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            List<Map> actionIntervals = actionIntervalPage.get().getContent();

            List<String> actionIntervalNames = actionIntervals.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(actionIntervalNames.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList()), actionIntervalNames);
        }

        //delete
    }

    @Nested
    class WhenAsATopicAdmin {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            userRepository.save(new User("jjones@test.test"));
        }

        void loginAsNonAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of("isAdmin", false)
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        // list - same functionality as member
        // show - same functionality as member

        // create
        @Test
        void createActionInterval(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create action interval
            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
        }

        // delete
        @Test
        void canDeleteActionInterval(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create action interval
            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionInterval = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionInterval.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/action_intervals/"+actionInterval.get().getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(NO_CONTENT, response.getStatus());
        }

        @Test
        void cannotDeleteActionIntervalIfAssociatedToAnAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create application
            response = entityUtil.createApplication("Application123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create grant duration
            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDuration = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDuration.isPresent());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create grant
            response = entityUtil.createApplicationGrant(applicationGrantToken, theta.getId(), "TempGrant", grantDuration.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());

            // create action interval
            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionInterval = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionInterval.isPresent());

            response = entityUtil.createAction(grantDTOOptional.get().getId(), actionInterval.get().getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionDTOOptional = response.getBody(ActionDTO.class);
            assertTrue(actionDTOOptional.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/action_intervals/"+actionInterval.get().getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_INTERVAL_HAS_ONE_OR_MORE_ACTION_ASSOCIATIONS.equals(dpmErrorResponse.getCode())));
        }
    }

    @Nested
    class WhenAsAGroupMember {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            userRepository.save(new User("jjones@test.test"));
        }

        void loginAsNonAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of("isAdmin", false)
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        // create
        @Test
        void cannotCreateActionInterval(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create action interval
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createActionInterval("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }

        // delete
        @Test
        void cannotDeleteActionInterval(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create action interval
            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> abcTopiSetcOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            ActionIntervalDTO abcActionInterval = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            HttpRequest<?> request2 = HttpRequest.DELETE("/action_intervals/"+abcActionInterval.getId(), Map.of());
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request2);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }

        // show
        @Test
        void canShowActionIntervalWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Action Intervals - Members
            // ---
            // Theta - Xyz789 - jjones
            // Zeta - Abc123 - None

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create action intervals
            entityUtil.createActionInterval("Abc123", zeta.getId());
            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzActionIntervalOptional.isPresent());
            ActionIntervalDTO xyzActionInterval = xyzActionIntervalOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/action_intervals/"+xyzActionInterval.getId());
            response = blockingClient.exchange(request, ActionIntervalDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalResponseOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalResponseOptional.isPresent());
            assertEquals("Xyz789", actionIntervalResponseOptional.get().getName());
            assertEquals("Theta", actionIntervalResponseOptional.get().getGroupName());
        }

        @Test
        void cannotShowActionIntervalIfActionIntervalBelongsToAGroupIAmNotAMemberOf(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Action Intervals - Members
            // ---
            // Theta - Xyz789 - jjones
            // Omega - Abc123 - None

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();


            response = entityUtil.createGroup("Omega");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> omegaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(omegaOptional.isPresent());
            SimpleGroupDTO omega = omegaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", omega.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> abcActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(abcActionIntervalOptional.isPresent());
            ActionIntervalDTO abcActionInterval = abcActionIntervalOptional.get();

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzActionIntervalOptional.isPresent());
            ActionIntervalDTO xyzActionInterval = xyzActionIntervalOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/action_intervals/"+abcActionInterval.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }

        // list
        @Test
        void canListAllActionIntervalsLimitedToMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Action Intervals
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> abcActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(abcActionIntervalOptional.isPresent());

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzActionIntervalOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/action_intervals");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            Map expectedActionInterval = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedActionInterval.get("name"));
        }

        @Test
        void canListActionIntervalsWithFilterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Action Intervals
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create action intervals
            entityUtil.createActionInterval("Abc123", zeta.getId());
            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzActionIntervalOptional.isPresent());

            loginAsNonAdmin();

            // support case-insensitive
            request = HttpRequest.GET("/action_intervals?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            Map expectedActionInterval = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedActionInterval.get("name"));

            // Negative case
            request = HttpRequest.GET("/action_intervals?filter=abc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(0, actionIntervalPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/action_intervals?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            expectedActionInterval = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedActionInterval.get("name"));
        }

        @Test
        void canListActionIntervalsWithGroupParameterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Action Intervals
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create action intervals
            entityUtil.createActionInterval("Abc123", zeta.getId());
            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzActionIntervalOptional.isPresent());

            loginAsNonAdmin();

            // group search
            request = HttpRequest.GET("/action_intervals?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            Map expectedActionInterval = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedActionInterval.get("name"));

            // filter param support
            request = HttpRequest.GET("/action_intervals?filter=xyz&group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(1, actionIntervalPage.get().getContent().size());
            expectedActionInterval = (Map) actionIntervalPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedActionInterval.get("name"));

            // Negative cases
            request = HttpRequest.GET("/action_intervals?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(0, actionIntervalPage.get().getContent().size());

            request = HttpRequest.GET("/action_intervals?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionIntervalPage = response.getBody(Page.class);
            assertTrue(actionIntervalPage.isPresent());
            assertEquals(0, actionIntervalPage.get().getContent().size());
        }
    }

    @Nested
    class WhenAsANonGroupMember {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            userRepository.save(new User("jjones@test.test"));
        }

        void loginAsNonAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of("isAdmin", false)
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        // create
        @Test
        void cannotCreateActionInterval(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            loginAsNonAdmin();

            // create action intervals
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createActionInterval("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }

        // delete
        @Test
        void cannotDeleteActionInterval(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> abcTopiSetcOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            ActionIntervalDTO abcActionInterval = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/action_intervals/"+abcActionInterval.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }

        // show
        @Test
        void cannotShowActionIntervalWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzTopiSetcOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzTopiSetcOptional.isPresent());
            ActionIntervalDTO xyzActionInterval = xyzTopiSetcOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/action_intervals/"+xyzActionInterval.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }
    }

    @Nested
    class WhenAsAUnauthenticatedUser {
        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
        }

        void loginAsNonAdmin() {
            mockSecurityService.setServerAuthentication(null);
            mockAuthenticationFetcher.setAuthentication(null);
        }

        @Test
        void cannotListAllActionIntervals(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // other group
            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = thetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzActionIntervalOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/action_intervals");
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        void cannotShowAActionIntervalWithGroupAssociation(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // other group
            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = thetaOptional.get();

            // create action intervals
            response = entityUtil.createActionInterval("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> xyzActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(xyzActionIntervalOptional.isPresent());
            ActionIntervalDTO xyzActionInterval = xyzActionIntervalOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/action_intervals/"+xyzActionInterval.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }
    }
}
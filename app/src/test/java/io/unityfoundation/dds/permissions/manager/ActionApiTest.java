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
import io.micronaut.security.utils.SecurityService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.CreateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.ActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.CreateActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.CreateGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.CreateGrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.GrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserResponseDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.CreateTopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.TopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "ActionApiTest")
@MicronautTest
public class ActionApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    @Client("/api")
    HttpClient client;

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

    @Requires(property = "spec.name", value = "ActionApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "ActionApiTest")
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
        void cannotCreateOnItsOwnWithoutAGrantAssociation() {
            HttpResponse<?> response;

            response = createGroup("MyGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> groupDTOOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(groupDTOOptional.isPresent());
            SimpleGroupDTO groupDTO = groupDTOOptional.get();

            response = createActionInterval("MyActionInterval", groupDTO.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            CreateActionDTO create = new CreateActionDTO();
            create.setActionIntervalId(actionInterval.getId());

            // without grant
            HttpRequest<?> request = HttpRequest.POST("/actions", create);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, ActionDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.ACTION_REQUIRES_APPLICATION_GRANT_ASSOCIATION.equals(map.get("code"))));

            // without action interval
            create.setActionIntervalId(null);
            request = HttpRequest.POST("/actions", create);
            HttpRequest<?> finalRequest1 = request;
            exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, ActionDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.ACTION_REQUIRES_INTERVAL_ASSOCIATION.equals(map.get("code"))));
        }

        @Test
        void canCreate() {
            GrantDTO applicationGrant = createGenericApplicationGrant();

            HttpResponse<?> response;
            response = createActionInterval("MyActionInterval", applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();
            assertNotNull(actionDTO.getApplicationGrantId());
            assertNotNull(actionDTO.getApplicationIntervalId());

            // partitions
            response = createAction(applicationGrant.getId(), actionInterval.getId(),
                    null, null, Set.of("p1", "p2"));
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertFalse(actionDTO.getPartitions().isEmpty());

            // topics
            response = createTopic("MyTopic", TopicKind.B, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO topicDTO = topicOptional.get();
            Set<Long> topicIds = Set.of(topicDTO.getId());

            response = createAction(applicationGrant.getId(), actionInterval.getId(), topicIds, null, null);
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertFalse(actionDTO.getTopics().isEmpty());

            // topic sets
            Long topicSetId = createTopicSetWithTopics("MyTopicSet", applicationGrant.getGroupId(), Set.of());
            response = createAction(applicationGrant.getId(), actionInterval.getId(),
                    null, Set.of(topicSetId), null);
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertFalse(actionDTO.getTopicSets().isEmpty());
        }

        // update
        @Test
        public void cannotUpdateApplicationGrantAssociation() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrantOne = createGenericApplicationGrant();

            // build second ApplicationGrant
            response = getApplicationGrantToken(applicationGrantOne.getApplicationId());
            Optional<String> grantTokenOptional = response.getBody(String.class);
            assertTrue(grantTokenOptional.isPresent());

            response = createGrantDuration("MySecondGrantDuration", applicationGrantOne.getGroupId());
            Optional<GrantDurationDTO> grantDurationDTOOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationDTOOptional.isPresent());

            response = createApplicationGrant(grantTokenOptional.get(), applicationGrantOne.getGroupId(), "MySecondGrant", grantDurationDTOOptional.get().getId());
            Optional<GrantDTO> applicationGrantTwoOptional = response.getBody(GrantDTO.class);
            assertTrue(applicationGrantTwoOptional.isPresent());

            // action interval
            response = createActionInterval("MyActionInterval", applicationGrantOne.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // action
            response = createAction(applicationGrantOne.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();
            assertNotNull(actionDTO.getApplicationGrantId());
            assertNotNull(actionDTO.getApplicationIntervalId());

            // update attempt
            Map<String, Long> updatePayload = Map.of("applicationGrantId", applicationGrantTwoOptional.get().getId(), "actionIntervalId", actionInterval.getId());

            request = HttpRequest.PUT("/actions/"+actionDTO.getId(), updatePayload);
            response = blockingClient.exchange(request, ActionDTO.class);
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertNotEquals(updatePayload.get("applicationGrantId"), actionDTO.getApplicationGrantId());
        }

        //show
        @Test
        void canShowAction(){
            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrant = createGenericApplicationGrant();

            response = createActionInterval("MyActionInterval", applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            // show grant duration
            request = HttpRequest.GET("/actions/"+actionDTO.getId());
            response = blockingClient.exchange(request, ActionDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> getActionOptional = response.getBody(ActionDTO.class);
            assertTrue(getActionOptional.isPresent());
            ActionDTO getActionDTO = getActionOptional.get();
            assertNotNull(getActionDTO.getId());
            assertNotNull(getActionDTO.getApplicationGrantId());
            assertNotNull(getActionDTO.getApplicationIntervalId());
            assertNotNull(getActionDTO.getApplicationIntervalName());
        }

        // list all grant durations from all groups
        @Test
        void canListActionsWithByGrantNameFilter(){
            // ApplicationGrant - Action
            // ---
            // MyGrant - x
            // MySecondGrant - x

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrantOne = createGenericApplicationGrant();

            // build second ApplicationGrant
            response = getApplicationGrantToken(applicationGrantOne.getApplicationId());
            Optional<String> grantTokenOptional = response.getBody(String.class);
            assertTrue(grantTokenOptional.isPresent());

            response = createGrantDuration("MySecondGrantDuration", applicationGrantOne.getGroupId());
            Optional<GrantDurationDTO> grantDurationDTOOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationDTOOptional.isPresent());

            response = createApplicationGrant(grantTokenOptional.get(), applicationGrantOne.getGroupId(), "MySecondGrant", grantDurationDTOOptional.get().getId());
            Optional<GrantDTO> applicationGrantTwoOptional = response.getBody(GrantDTO.class);
            assertTrue(applicationGrantTwoOptional.isPresent());

            // action interval
            response = createActionInterval("MyActionInterval", applicationGrantOne.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // create actions
            createAction(applicationGrantOne.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());

            createAction(applicationGrantTwoOptional.get().getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());

            // support case-insensitive
            request = HttpRequest.GET("/actions?filter=second");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertEquals(1, actionPage.get().getContent().size());
        }

        @Test
        void canListActionsWithGranId(){
            // ApplicationGrant - Action
            // ---
            // MyGrant - x
            // MySecondGrant - x

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrantOne = createGenericApplicationGrant();

            // build second ApplicationGrant
            response = getApplicationGrantToken(applicationGrantOne.getApplicationId());
            Optional<String> grantTokenOptional = response.getBody(String.class);
            assertTrue(grantTokenOptional.isPresent());

            response = createGrantDuration("MySecondGrantDuration", applicationGrantOne.getGroupId());
            Optional<GrantDurationDTO> grantDurationDTOOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationDTOOptional.isPresent());

            response = createApplicationGrant(grantTokenOptional.get(), applicationGrantOne.getGroupId(), "MySecondGrant", grantDurationDTOOptional.get().getId());
            Optional<GrantDTO> applicationGrantTwoOptional = response.getBody(GrantDTO.class);
            assertTrue(applicationGrantTwoOptional.isPresent());

            // action interval
            response = createActionInterval("MyActionInterval", applicationGrantOne.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // create actions
            response = createAction(applicationGrantOne.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOneDTOOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOneDTOOptional.isPresent());

            response = createAction(applicationGrantTwoOptional.get().getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionTwoDTOOptional = response.getBody(ActionDTO.class);
            assertTrue(actionTwoDTOOptional.isPresent());

            // can list both grant durations
            request = HttpRequest.GET("/actions?grantId="+applicationGrantTwoOptional.get().getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertEquals(1, actionPage.get().getContent().size());
            Map map = (Map) actionPage.get().getContent().get(0);
            assertEquals(map.get("id"), actionTwoDTOOptional.get().getId().intValue());

            request = HttpRequest.GET("/actions?grantId="+applicationGrantOne.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertEquals(1, actionPage.get().getContent().size());
            map = (Map) actionPage.get().getContent().get(0);
            assertEquals(map.get("id"), actionOneDTOOptional.get().getId().intValue());
        }

        //delete
        @Test
        void canDeleteActions(){
            // ApplicationGrant - Action
            // ---
            // MyGrant - x
            // MySecondGrant - x

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrantOne = createGenericApplicationGrant();

            // action interval
            response = createActionInterval("MyActionInterval", applicationGrantOne.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // create actions
            response = createAction(applicationGrantOne.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOneDTOOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOneDTOOptional.isPresent());

            request = HttpRequest.DELETE("/actions/"+actionOneDTOOptional.get().getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(NO_CONTENT, response.getStatus());

            request = HttpRequest.GET("/actions");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertTrue(actionPage.get().getContent().isEmpty());
        }
    }

//    @Nested
//    class WhenAsATopicAdmin {
//
//        @BeforeEach
//        void setup() {
//            dbCleanup.cleanup();
//            userRepository.save(new User("montesm@test.test.com", true));
//            userRepository.save(new User("jjones@test.test"));
//        }
//
//        void loginAsNonAdmin() {
//            mockSecurityService.setServerAuthentication(new ServerAuthentication(
//                    "jjones@test.test",
//                    Collections.emptyList(),
//                    Map.of("isAdmin", false)
//            ));
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//        }
//
//        // list - same functionality as member
//        // show - same functionality as member
//
//        // create
//        @Test
//        void canCreateGrantDuration(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
//            assertEquals(OK, response.getStatus());
//
//            loginAsNonAdmin();
//
//            // create grant duration
//            response = createGrantDuration("Abc123", theta.getId());
//            assertEquals(OK, response.getStatus());
//        }
//
//        // delete
//        @Test
//        void canDeleteGrantDuration(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
//            assertEquals(OK, response.getStatus());
//
//            // create grant duration
//            response = createGrantDuration("Abc123", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> grantDuration = response.getBody(GrantDurationDTO.class);
//            assertTrue(grantDuration.isPresent());
//
//            loginAsNonAdmin();
//
//            // delete attempt
//            request = HttpRequest.DELETE("/grant_durations/"+grantDuration.get().getId(), Map.of());
//            response = blockingClient.exchange(request);
//            assertEquals(NO_CONTENT, response.getStatus());
//        }
//    }

//    @Nested
//    class WhenAsAGroupMember {
//
//        @BeforeEach
//        void setup() {
//            dbCleanup.cleanup();
//            userRepository.save(new User("montesm@test.test.com", true));
//            userRepository.save(new User("jjones@test.test"));
//        }
//
//        void loginAsNonAdmin() {
//            mockSecurityService.setServerAuthentication(new ServerAuthentication(
//                    "jjones@test.test",
//                    Collections.emptyList(),
//                    Map.of("isAdmin", false)
//            ));
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//        }
//
//        // create
//        @Test
//        void cannotCreateGrantDuration(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            loginAsNonAdmin();
//
//            // create grant duration
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                createGrantDuration("Abc123", theta.getId());
//            });
//            assertEquals(UNAUTHORIZED,exception.getStatus());
//            Optional<List> listOptional = exception.getResponse().getBody(List.class);
//            assertTrue(listOptional.isPresent());
//            List<Map> list = listOptional.get();
//            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
//        }
//
//        // delete
//        @Test
//        void cannotDeleteGrantDuration(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // create grant duration
//            response = createGrantDuration("Abc123", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> abcTopiSetcOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(abcTopiSetcOptional.isPresent());
//            GrantDurationDTO abcGrantDuration = abcTopiSetcOptional.get();
//
//            loginAsNonAdmin();
//
//            // delete attempt
//            HttpRequest<?> request2 = HttpRequest.DELETE("/grant_durations/"+abcGrantDuration.getId(), Map.of());
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                blockingClient.exchange(request2);
//            });
//            assertEquals(UNAUTHORIZED,exception.getStatus());
//            Optional<List> listOptional = exception.getResponse().getBody(List.class);
//            assertTrue(listOptional.isPresent());
//            List<Map> list = listOptional.get();
//            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
//        }
//
//        // show
//        @Test
//        void canShowGrantDurationWithAssociatedGroup(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            // Group - Grant Durations - Members
//            // ---
//            // Theta - Xyz789 - jjones
//            // Zeta - Abc123 - None
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            response = createGroup("Zeta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(zetaOptional.isPresent());
//            SimpleGroupDTO zeta = zetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // create grant durations
//            createGrantDuration("Abc123", zeta.getId());
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzGrantDurationOptional.isPresent());
//            GrantDurationDTO xyzGrantDuration = xyzGrantDurationOptional.get();
//
//            loginAsNonAdmin();
//
//            request = HttpRequest.GET("/grant_durations/"+xyzGrantDuration.getId());
//            response = blockingClient.exchange(request, GrantDurationDTO.class);
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> grantDurationResponseOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(grantDurationResponseOptional.isPresent());
//            assertEquals("Xyz789", grantDurationResponseOptional.get().getName());
//            assertEquals("Theta", grantDurationResponseOptional.get().getGroupName());
//        }
//
//        @Test
//        void cannotShowGrantDurationIfGrantDurationBelongsToAGroupIAmNotAMemberOf(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            // Group - Grant Durations - Members
//            // ---
//            // Theta - Xyz789 - jjones
//            // Omega - Abc123 - None
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//
//            response = createGroup("Omega");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> omegaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(omegaOptional.isPresent());
//            SimpleGroupDTO omega = omegaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // create grant durations
//            response = createGrantDuration("Abc123", omega.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> abcGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(abcGrantDurationOptional.isPresent());
//            GrantDurationDTO abcGrantDuration = abcGrantDurationOptional.get();
//
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzGrantDurationOptional.isPresent());
//            GrantDurationDTO xyzGrantDuration = xyzGrantDurationOptional.get();
//
//            loginAsNonAdmin();
//
//            request = HttpRequest.GET("/grant_durations/"+abcGrantDuration.getId());
//            HttpRequest<?> finalRequest = request;
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                blockingClient.exchange(finalRequest);
//            });
//            assertEquals(UNAUTHORIZED, exception.getStatus());
//            Optional<List> listOptional = exception.getResponse().getBody(List.class);
//            assertTrue(listOptional.isPresent());
//            List<Map> list = listOptional.get();
//            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
//        }
//
//        // list
//        @Test
//        void canListAllGrantDurationsLimitedToMembership(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            // Group - Grant Durations
//            // ---
//            // Theta - Xyz789
//            // Zeta - Abc123
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            response = createGroup("Zeta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(zetaOptional.isPresent());
//            SimpleGroupDTO zeta = zetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // create grant durations
//            response = createGrantDuration("Abc123", zeta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> abcGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(abcGrantDurationOptional.isPresent());
//
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzGrantDurationOptional.isPresent());
//
//            loginAsNonAdmin();
//
//            request = HttpRequest.GET("/grant_durations");
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            Optional<Page> grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(1, grantDurationPage.get().getContent().size());
//            Map expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
//            assertEquals("Xyz789", expectedGrantDuration.get("name"));
//        }
//
//        @Test
//        void canListGrantDurationsWithFilterLimitedToGroupMembership(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            // Group - Grant Durations
//            // ---
//            // Theta - Xyz789
//            // Zeta - Abc123
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            response = createGroup("Zeta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(zetaOptional.isPresent());
//            SimpleGroupDTO zeta = zetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // create grant durations
//            createGrantDuration("Abc123", zeta.getId());
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzGrantDurationOptional.isPresent());
//
//            loginAsNonAdmin();
//
//            // support case-insensitive
//            request = HttpRequest.GET("/grant_durations?filter=xyz");
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            Optional<Page> grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(1, grantDurationPage.get().getContent().size());
//            Map expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
//            assertEquals("Xyz789", expectedGrantDuration.get("name"));
//
//            // Negative case
//            request = HttpRequest.GET("/grant_durations?filter=abc");
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(0, grantDurationPage.get().getContent().size());
//
//            // group search
//            request = HttpRequest.GET("/grant_durations?filter=heta");
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(1, grantDurationPage.get().getContent().size());
//            expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
//            assertEquals("Xyz789", expectedGrantDuration.get("name"));
//        }
//
//        @Test
//        void canListGrantDurationsWithGroupParameterLimitedToGroupMembership(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            // Group - Grant Durations
//            // ---
//            // Theta - Xyz789
//            // Zeta - Abc123
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            response = createGroup("Zeta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(zetaOptional.isPresent());
//            SimpleGroupDTO zeta = zetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // create grant durations
//            createGrantDuration("Abc123", zeta.getId());
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzGrantDurationOptional.isPresent());
//
//            loginAsNonAdmin();
//
//            // group search
//            request = HttpRequest.GET("/grant_durations?group="+theta.getId());
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            Optional<Page> grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(1, grantDurationPage.get().getContent().size());
//            Map expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
//            assertEquals("Xyz789", expectedGrantDuration.get("name"));
//
//            // filter param support
//            request = HttpRequest.GET("/grant_durations?filter=xyz&group="+theta.getId());
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(1, grantDurationPage.get().getContent().size());
//            expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
//            assertEquals("Xyz789", expectedGrantDuration.get("name"));
//
//            // Negative cases
//            request = HttpRequest.GET("/grant_durations?group="+zeta.getId());
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(0, grantDurationPage.get().getContent().size());
//
//            request = HttpRequest.GET("/grant_durations?filter=abc&group="+zeta.getId());
//            response = blockingClient.exchange(request, Page.class);
//            assertEquals(OK, response.getStatus());
//            grantDurationPage = response.getBody(Page.class);
//            assertTrue(grantDurationPage.isPresent());
//            assertEquals(0, grantDurationPage.get().getContent().size());
//        }
//    }

//    @Nested
//    class WhenAsANonGroupMember {
//
//        @BeforeEach
//        void setup() {
//            dbCleanup.cleanup();
//            userRepository.save(new User("montesm@test.test.com", true));
//            userRepository.save(new User("jjones@test.test"));
//        }
//
//        void loginAsNonAdmin() {
//            mockSecurityService.setServerAuthentication(new ServerAuthentication(
//                    "jjones@test.test",
//                    Collections.emptyList(),
//                    Map.of("isAdmin", false)
//            ));
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//        }
//
//        // create
//        @Test
//        void cannotCreateGrantDuration(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            loginAsNonAdmin();
//
//            // create grant durations
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                createGrantDuration("Abc123", theta.getId());
//            });
//            assertEquals(UNAUTHORIZED,exception.getStatus());
//            Optional<List> listOptional = exception.getResponse().getBody(List.class);
//            assertTrue(listOptional.isPresent());
//            List<Map> list = listOptional.get();
//            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
//        }
//
//        // delete
//        @Test
//        void cannotDeleteGrantDuration(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            // create grant durations
//            response = createGrantDuration("Abc123", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> abcTopiSetcOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(abcTopiSetcOptional.isPresent());
//            GrantDurationDTO abcGrantDuration = abcTopiSetcOptional.get();
//
//            loginAsNonAdmin();
//
//            // delete attempt
//            request = HttpRequest.DELETE("/grant_durations/"+abcGrantDuration.getId(), Map.of());
//            HttpRequest<?> finalRequest = request;
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                blockingClient.exchange(finalRequest);
//            });
//            assertEquals(UNAUTHORIZED,exception.getStatus());
//            Optional<List> listOptional = exception.getResponse().getBody(List.class);
//            assertTrue(listOptional.isPresent());
//            List<Map> list = listOptional.get();
//            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
//        }
//
//        // show
//        @Test
//        void cannotShowGrantDurationWithAssociatedGroup(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            response = createGroup("Zeta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(zetaOptional.isPresent());
//            SimpleGroupDTO zeta = zetaOptional.get();
//
//            // create grant durations
//            response = createGrantDuration("Abc123", zeta.getId());
//            assertEquals(OK, response.getStatus());
//
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzTopiSetcOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzTopiSetcOptional.isPresent());
//            GrantDurationDTO xyzGrantDuration = xyzTopiSetcOptional.get();
//
//            loginAsNonAdmin();
//
//            request = HttpRequest.GET("/grant_durations/"+xyzGrantDuration.getId());
//            HttpRequest<?> finalRequest = request;
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                blockingClient.exchange(finalRequest);
//            });
//            assertEquals(UNAUTHORIZED,exception.getStatus());
//            Optional<List> listOptional = exception.getResponse().getBody(List.class);
//            assertTrue(listOptional.isPresent());
//            List<Map> list = listOptional.get();
//            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
//        }
//    }

//    @Nested
//    class WhenAsAUnauthenticatedUser {
//        @BeforeEach
//        void setup() {
//            dbCleanup.cleanup();
//            userRepository.save(new User("montesm@test.test.com", true));
//        }
//
//        void loginAsNonAdmin() {
//            mockSecurityService.setServerAuthentication(null);
//            mockAuthenticationFetcher.setAuthentication(null);
//        }
//
//        @Test
//        void cannotListAllGrantDurations(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // other group
//            response = createGroup("Zeta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(zetaOptional.isPresent());
//            SimpleGroupDTO zeta = thetaOptional.get();
//
//            // create grant durations
//            response = createGrantDuration("Abc123", zeta.getId());
//            assertEquals(OK, response.getStatus());
//
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzGrantDurationOptional.isPresent());
//
//            loginAsNonAdmin();
//
//            request = HttpRequest.GET("/grant_durations");
//            HttpRequest<?> finalRequest = request;
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                blockingClient.exchange(finalRequest);
//            });
//            assertEquals(UNAUTHORIZED, exception.getStatus());
//        }
//
//        @Test
//        void cannotShowAGrantDurationWithGroupAssociation(){
//            mockSecurityService.postConstruct();
//            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
//
//            HttpRequest<?> request;
//            HttpResponse<?> response;
//
//            // create groups
//            response = createGroup("Theta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(thetaOptional.isPresent());
//            SimpleGroupDTO theta = thetaOptional.get();
//
//            // add member to group
//            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
//            assertEquals(OK, response.getStatus());
//
//            // other group
//            response = createGroup("Zeta");
//            assertEquals(OK, response.getStatus());
//            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
//            assertTrue(zetaOptional.isPresent());
//            SimpleGroupDTO zeta = thetaOptional.get();
//
//            // create grant durations
//            response = createGrantDuration("Abc123", zeta.getId());
//            assertEquals(OK, response.getStatus());
//
//            response = createGrantDuration("Xyz789", theta.getId());
//            assertEquals(OK, response.getStatus());
//            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
//            assertTrue(xyzGrantDurationOptional.isPresent());
//            GrantDurationDTO xyzGrantDuration = xyzGrantDurationOptional.get();
//
//            loginAsNonAdmin();
//
//            request = HttpRequest.GET("/grant_durations/"+xyzGrantDuration.getId());
//            HttpRequest<?> finalRequest = request;
//            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
//                blockingClient.exchange(finalRequest);
//            });
//            assertEquals(UNAUTHORIZED, exception.getStatus());
//        }
//    }

    private GrantDTO createGenericApplicationGrant() {
        HttpResponse<?> response;

        response = createGroup("MyGroup");
        assertEquals(OK, response.getStatus());
        Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
        assertTrue(thetaOptional.isPresent());
        SimpleGroupDTO group = thetaOptional.get();

        response = createApplication("MyApplication", group.getId());
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
        assertTrue(applicationOptional.isPresent());
        ApplicationDTO applicationDTO = applicationOptional.get();

        response = getApplicationGrantToken(applicationDTO.getId());
        assertEquals(OK, response.getStatus());
        Optional<String> grantTokenOptional = response.getBody(String.class);
        assertTrue(grantTokenOptional.isPresent());
        String applicationGrantToken = grantTokenOptional.get();

        response = createGrantDuration("MyGrantDuration", group.getId());
        assertEquals(OK, response.getStatus());
        Optional<GrantDurationDTO> grantDuration = response.getBody(GrantDurationDTO.class);
        assertTrue(grantDuration.isPresent());

        response = createApplicationGrant(applicationGrantToken, group.getId(), "MyGrant", grantDuration.get().getId());
        assertEquals(CREATED, response.getStatus());
        Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
        assertTrue(grantDTOOptional.isPresent());
        return grantDTOOptional.get();
    }

    private HttpResponse<?> createGroup(String groupName) {
        SimpleGroupDTO groupDTO = new SimpleGroupDTO();
        groupDTO.setName(groupName);
        HttpRequest<?> request = HttpRequest.POST("/groups/save", groupDTO);
        return blockingClient.exchange(request, SimpleGroupDTO.class);
    }

    private HttpResponse<?> addGroupMembership(Long groupId, String email, boolean isAdmin) {
        GroupUserDTO dto = new GroupUserDTO();
        dto.setPermissionsGroup(groupId);
        dto.setEmail(email);
        dto.setTopicAdmin(isAdmin);

        HttpRequest<?>  request = HttpRequest.POST("/group_membership", dto);
        return blockingClient.exchange(request, GroupUserResponseDTO.class);
    }

    private HttpResponse<?> createApplication(String applicationName, Long groupId) {
        ApplicationDTO applicationDTO = new ApplicationDTO();
        applicationDTO.setName(applicationName);
        applicationDTO.setGroup(groupId);

        HttpRequest<?> request = HttpRequest.POST("/applications/save", applicationDTO);
        return blockingClient.exchange(request, ApplicationDTO.class);
    }

    private HttpResponse<?> getApplicationGrantToken(Long applicationId) {
        HttpRequest<?> request = HttpRequest.GET("/applications/generate_grant_token/" + applicationId);
        return blockingClient.exchange(request, String.class);
    }

    private HttpResponse<?> createTopic(String topicName, TopicKind topicKind, Long groupId) {
        TopicDTO topicDTO = new TopicDTO();
        topicDTO.setName(topicName);
        topicDTO.setGroup(groupId);
        topicDTO.setKind(topicKind);

        HttpRequest<?> request = HttpRequest.POST("/topics/save", topicDTO);
        return blockingClient.exchange(request, TopicDTO.class);
    }

    private HttpResponse<?> createTopicSet(String name, Long groupId) {
        CreateTopicSetDTO abcDTO = new CreateTopicSetDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);

        HttpRequest<?> request = HttpRequest.POST("/topic-sets", abcDTO);
        return blockingClient.exchange(request, TopicSetDTO.class);
    }

    private HttpResponse<?> addTopicToTopicSet(Long topicSetId, Long topicId) {
        HttpRequest<?> request = HttpRequest.POST("/topic-sets/" + topicSetId + "/" + topicId, Map.of());
        return blockingClient.exchange(request, TopicSetDTO.class);
    }

    private Long createTopicSetWithTopics(String name, Long groupId, Set<Long> topicIds) {
        CreateTopicSetDTO abcDTO = new CreateTopicSetDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);

        HttpRequest<?> request = HttpRequest.POST("/topic-sets", abcDTO);
        HttpResponse<?> response = blockingClient.exchange(request, TopicSetDTO.class);
        Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
        assertTrue(topicSetOptional.isPresent());
        TopicSetDTO topicSetDTO = topicSetOptional.get();

        topicIds.forEach(topicId -> addTopicToTopicSet(topicSetDTO.getId(), topicId));

        return topicSetDTO.getId();
    }

    private HttpResponse<?> createGrantDuration(String name, Long groupId) {
        return createGrantDuration(name, groupId, null);
    }

    private HttpResponse<?> createGrantDuration(String name, Long groupId, String durationMetadata) {
        CreateGrantDurationDTO abcDTO = new CreateGrantDurationDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);
        abcDTO.setDurationInMilliseconds(30000L);
        abcDTO.setDurationMetadata(durationMetadata);


        HttpRequest<?> request = HttpRequest.POST("/grant_durations", abcDTO);
        return blockingClient.exchange(request, GrantDurationDTO.class);
    }

    private HttpResponse<?> createApplicationGrant(String applicationGrantToken, Long groupId, String name, Long durationId) {
        CreateGrantDTO createGrantDTO = new CreateGrantDTO();
        createGrantDTO.setGroupId(groupId);
        createGrantDTO.setName(name);
        createGrantDTO.setGrantDurationId(durationId);

        HttpRequest<?> request = HttpRequest.POST("/application_grants/", createGrantDTO)
                .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
        return blockingClient.exchange(request, GrantDTO.class);
    }

    private HttpResponse<?> createActionInterval(String name, Long groupId) {
        CreateActionIntervalDTO abcDTO = new CreateActionIntervalDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);
        abcDTO.setStartDate(Instant.now());
        abcDTO.setEndDate(Instant.now().plus(1, ChronoUnit.DAYS));

        HttpRequest<?> request = HttpRequest.POST("/action_intervals", abcDTO);
        return blockingClient.exchange(request, ActionIntervalDTO.class);
    }

    private HttpResponse<?> createAction(Long grantId, Long intervalId) {
        return createAction(grantId, intervalId, null, null, null);
    }

    private HttpResponse<?> createAction(Long grantId, Long intervalId, Set<Long> topics, Set<Long> topicSets, Set<String> partitions) {
        CreateActionDTO create = new CreateActionDTO();
        create.setApplicationGrantId(grantId);
        create.setActionIntervalId(intervalId);
        create.setTopicIds(topics);
        create.setTopicSetIds(topicSets);
        create.setPartitions(partitions);

        HttpRequest<?> request = HttpRequest.POST("/actions", create);
        return blockingClient.exchange(request, ActionDTO.class);
    }
}
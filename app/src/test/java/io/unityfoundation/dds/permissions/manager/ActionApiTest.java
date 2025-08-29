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
import io.unityfoundation.dds.permissions.manager.model.action.ActionPartition;
import io.unityfoundation.dds.permissions.manager.model.action.ActionPartitionRepository;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.CreateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.UpdateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.ActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.GrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import io.unityfoundation.dds.permissions.manager.testing.util.EntityLifecycleUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    EntityLifecycleUtil entityUtil;

    @Inject
    GroupRepository groupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GroupUserRepository groupUserRepository;

    @Inject
    ActionPartitionRepository actionPartitionRepository;

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

            response = entityUtil.createGroup("MyGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> groupDTOOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(groupDTOOptional.isPresent());
            SimpleGroupDTO groupDTO = groupDTOOptional.get();

            response = entityUtil.createActionInterval("MyActionInterval", groupDTO.getId());
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
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_REQUIRES_APPLICATION_GRANT_ASSOCIATION.equals(dpmErrorResponse.getCode())));

            // without action interval
            create.setActionIntervalId(null);
            request = HttpRequest.POST("/actions", create);
            HttpRequest<?> finalRequest1 = request;
            exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, ActionDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            dpmErrorResponses = listOptional.get();
            list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.ACTION_REQUIRES_INTERVAL_ASSOCIATION.equals(dpmErrorResponse.getCode())));
        }

        @Test
        void canCreate() {
            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();

            HttpResponse<?> response;
            response = entityUtil.createActionInterval("MyActionInterval", applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();
            assertNotNull(actionDTO.getApplicationGrantId());
            assertNotNull(actionDTO.getActionIntervalId());
            assertFalse(actionDTO.getPublishAction());

            // partitions
            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId(), null,
                    null, null, Set.of("p1", "p2"));
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertFalse(actionDTO.getPartitions().isEmpty());

            // topics
            response = entityUtil.createTopic("MyTopic", TopicKind.B, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO topicDTO = topicOptional.get();
            Set<Long> topicIds = Set.of(topicDTO.getId());

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId(), null, topicIds, null, null);
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertFalse(actionDTO.getTopics().isEmpty());

            // topic sets
            Long topicSetId = entityUtil.createTopicSetWithTopics("MyTopicSet", applicationGrant.getGroupId(), Set.of());
            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId(), true,
                    null, Set.of(topicSetId), null);
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertFalse(actionDTO.getTopicSets().isEmpty());
            assertTrue(actionDTO.getPublishAction());

            // show all with publish query
            HttpRequest<?> request = HttpRequest.GET("/actions/?pubsub=PUBLISH");
            response = blockingClient.exchange(request, ActionDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertEquals(1, actionPage.get().getContent().size());
            Map map = (Map) actionPage.get().getContent().get(0);
            assertEquals(map.get("id"), actionDTO.getId().intValue());
        }

        // update
        @Test
        public void cannotUpdateApplicationGrantAssociation() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrantOne = entityUtil.createGenericApplicationGrant();

            // build second ApplicationGrant
            response = entityUtil.getApplicationGrantToken(applicationGrantOne.getApplicationId());
            Optional<String> grantTokenOptional = response.getBody(String.class);
            assertTrue(grantTokenOptional.isPresent());

            response = entityUtil.createGrantDuration("MySecondGrantDuration", applicationGrantOne.getGroupId());
            Optional<GrantDurationDTO> grantDurationDTOOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationDTOOptional.isPresent());

            response = entityUtil.createApplicationGrant(grantTokenOptional.get(), applicationGrantOne.getGroupId(), "MySecondGrant", grantDurationDTOOptional.get().getId());
            Optional<GrantDTO> applicationGrantTwoOptional = response.getBody(GrantDTO.class);
            assertTrue(applicationGrantTwoOptional.isPresent());

            // action interval
            response = entityUtil.createActionInterval("MyActionInterval", applicationGrantOne.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // action
            response = entityUtil.createAction(applicationGrantOne.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();
            assertNotNull(actionDTO.getApplicationGrantId());
            assertNotNull(actionDTO.getActionIntervalId());

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

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();

            response = entityUtil.createActionInterval("MyActionInterval", applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            // show grant action
            request = HttpRequest.GET("/actions/"+actionDTO.getId());
            response = blockingClient.exchange(request, ActionDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> getActionOptional = response.getBody(ActionDTO.class);
            assertTrue(getActionOptional.isPresent());
            ActionDTO getActionDTO = getActionOptional.get();
            assertNotNull(getActionDTO.getId());
            assertNotNull(getActionDTO.getApplicationGrantId());
            assertNotNull(getActionDTO.getActionIntervalId());
            assertNotNull(getActionDTO.getActionIntervalName());
        }

        @Test
        void canListActionsWithByGrantNameFilter(){
            // ApplicationGrant - Action
            // ---
            // MyGrant - x
            // MySecondGrant - x

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrantOne = entityUtil.createGenericApplicationGrant();

            // build second ApplicationGrant
            response = entityUtil.getApplicationGrantToken(applicationGrantOne.getApplicationId());
            Optional<String> grantTokenOptional = response.getBody(String.class);
            assertTrue(grantTokenOptional.isPresent());

            response = entityUtil.createGrantDuration("MySecondGrantDuration", applicationGrantOne.getGroupId());
            Optional<GrantDurationDTO> grantDurationDTOOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationDTOOptional.isPresent());

            response = entityUtil.createApplicationGrant(grantTokenOptional.get(), applicationGrantOne.getGroupId(), "MySecondGrant", grantDurationDTOOptional.get().getId());
            Optional<GrantDTO> applicationGrantTwoOptional = response.getBody(GrantDTO.class);
            assertTrue(applicationGrantTwoOptional.isPresent());

            // action interval
            response = entityUtil.createActionInterval("MyActionInterval", applicationGrantOne.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // create actions
            entityUtil.createAction(applicationGrantOne.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());

            entityUtil.createAction(applicationGrantTwoOptional.get().getId(), actionInterval.getId());
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

            GrantDTO applicationGrantOne = entityUtil.createGenericApplicationGrant();

            // build second ApplicationGrant
            response = entityUtil.getApplicationGrantToken(applicationGrantOne.getApplicationId());
            Optional<String> grantTokenOptional = response.getBody(String.class);
            assertTrue(grantTokenOptional.isPresent());

            response = entityUtil.createGrantDuration("MySecondGrantDuration", applicationGrantOne.getGroupId());
            Optional<GrantDurationDTO> grantDurationDTOOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationDTOOptional.isPresent());

            response = entityUtil.createApplicationGrant(grantTokenOptional.get(), applicationGrantOne.getGroupId(), "MySecondGrant", grantDurationDTOOptional.get().getId());
            Optional<GrantDTO> applicationGrantTwoOptional = response.getBody(GrantDTO.class);
            assertTrue(applicationGrantTwoOptional.isPresent());

            // action interval
            response = entityUtil.createActionInterval("MyActionInterval", applicationGrantOne.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // create actions
            response = entityUtil.createAction(applicationGrantOne.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOneDTOOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOneDTOOptional.isPresent());

            response = entityUtil.createAction(applicationGrantTwoOptional.get().getId(), actionInterval.getId());
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

        @Test
        void deletionOfGrantCascadesToActionsAndPartitions(){
            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();

            // action interval
            response = entityUtil.createActionInterval("MyActionInterval", applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // topic
            response = entityUtil.createTopic("MyTopicB", TopicKind.B, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO topicDTO = topicOptional.get();

            // topic set
            response = entityUtil.createTopic("MyTopicC", TopicKind.C, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());

            Long topicSetId = entityUtil.createTopicSetWithTopics("MyTopicSet", applicationGrant.getGroupId(), Set.of(topicOptional.get().getId()));

            // create action
            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId(), true,
                    Set.of(topicDTO.getId()), Set.of(topicSetId), Set.of("dog", "cat"));
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOneDTOOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOneDTOOptional.isPresent());

            // delete application grant
            request = HttpRequest.DELETE("/application_grants/"+applicationGrant.getId());
            response = blockingClient.exchange(request, HashMap.class);
            assertEquals(NO_CONTENT, response.getStatus());

            // make sure actions and partitions are deleted
            request = HttpRequest.GET("/actions?grantId="+applicationGrant.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertEquals(0, actionPage.get().getContent().size());

            List<ActionPartition> allPartitions = actionPartitionRepository.findAllByActionId(actionOneDTOOptional.get().getId());
            assertTrue(allPartitions.isEmpty());
        }

        @Test
        public void canViewAllApplicationGrantsAndActionsByApplicationId() {
            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();

            HttpResponse<?> response;
            HttpRequest<?> request;

            response = entityUtil.createActionInterval("MyActionInterval", applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // partitions
            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId(), null,
                    null, null, Set.of("p1", "p2"));
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();
            assertNotNull(actionDTO.getApplicationGrantId());
            assertNotNull(actionDTO.getActionIntervalId());
            assertFalse(actionDTO.getPublishAction());
            assertFalse(actionDTO.getPartitions().isEmpty());

            // public permissions with public application
            request = HttpRequest.GET("/application_grants/application/"+applicationGrant.getApplicationId());
            Page page = blockingClient.retrieve(request, Page.class);
            assertFalse(page.isEmpty());
            assertEquals(1, page.getContent().size());
            assertTrue(page.getContent().stream().anyMatch(o -> {
                Map map2 = (Map) o;
                ArrayList actions = (ArrayList) map2.get("actions");
                Map firstAction = (Map) actions.get(0);
                Object partitions = firstAction.get("partitions");
                return partitions != null;
            }));
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

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();

            // action interval
            response = entityUtil.createActionInterval("MyActionInterval", applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createTopic("MyTopicB", TopicKind.B, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO topicDTO = topicOptional.get();

            // topic set
            response = entityUtil.createTopic("MyTopicC", TopicKind.C, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());

            Long topicSetId = entityUtil.createTopicSetWithTopics("MyTopicSet", applicationGrant.getGroupId(), Set.of(topicOptional.get().getId()));

            // create actions
            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId(), true,
                    Set.of(topicDTO.getId()), Set.of(topicSetId), Set.of("dog", "cat"));
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
        void canCreateAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            // add member to group
            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            loginAsNonAdmin();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();
            assertNotNull(actionDTO.getApplicationGrantId());
            assertNotNull(actionDTO.getActionIntervalId());
        }

        // update
        @Test
        void canUpdateAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            HttpRequest<?> request;
            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            // add member to group
            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());

            response = entityUtil.createActionInterval("MySecondActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> secondActionIntervalOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(secondActionIntervalOptional.isPresent());
            ActionIntervalDTO secondActionInterval = secondActionIntervalOptional.get();

            // create topics
            response = entityUtil.createTopic("MyBTopic", TopicKind.B, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicBOptional = response.getBody(TopicDTO.class);
            assertTrue(topicBOptional.isPresent());
            TopicDTO topicBDTO = topicBOptional.get();
            Long topicBId = topicBDTO.getId();

            response = entityUtil.createTopic("MyCTopic", TopicKind.C, applicationGrant.getGroupId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicCOptional = response.getBody(TopicDTO.class);
            assertTrue(topicCOptional.isPresent());
            TopicDTO topicCDTO = topicCOptional.get();
            Long topicCId = topicCDTO.getId();

            loginAsNonAdmin();

            UpdateActionDTO updateActionDTO = new UpdateActionDTO();
            updateActionDTO.setPartitions(Set.of("part1", "part2"));
            updateActionDTO.setActionIntervalId(secondActionInterval.getId());
            updateActionDTO.setTopicIds(Set.of(topicBId));

            request = HttpRequest.PUT("/actions/"+actionOptional.get().getId(), updateActionDTO);
            response = blockingClient.exchange(request, ActionDTO.class);
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();
            assertFalse(actionDTO.getPartitions().isEmpty());
            assertEquals(secondActionInterval.getId(), actionDTO.getActionIntervalId());
            assertEquals(1, actionDTO.getTopics().size());
            assertTrue(actionDTO.getTopics().stream().allMatch(map -> topicBId.intValue() == (int) map.get("id")));

            // with a different set of topics
            updateActionDTO.setTopicIds(Set.of(topicCId));

            request = HttpRequest.PUT("/actions/"+actionOptional.get().getId(), updateActionDTO);
            response = blockingClient.exchange(request, ActionDTO.class);
            assertEquals(OK, response.getStatus());
            actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            actionDTO = actionOptional.get();
            assertEquals(1, actionDTO.getTopics().size());
            assertTrue(actionDTO.getTopics().stream().allMatch(map -> topicCId.intValue() == (int) map.get("id")));
        }

        // delete
        @Test
        void canDeleteAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;
            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            // add member to group
            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            loginAsNonAdmin();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/actions/"+actionDTO.getId());
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
        void cannotCreate(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // add member to group
            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create grant duration
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }

        // update
        @Test
        void cannotUpdate(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            HttpRequest<?> request;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            loginAsNonAdmin();

            UpdateActionDTO updateActionDTO = new UpdateActionDTO();
            updateActionDTO.setActionIntervalId(actionInterval.getId());
            updateActionDTO.setPartitions(Set.of("part1", "part2"));

            request = HttpRequest.PUT("/actions/"+actionOptional.get().getId(), updateActionDTO);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request, ActionDTO.class);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.UNAUTHORIZED.equals(dpmErrorResponse.getCode())));
        }

        // delete
        @Test
        void cannotDelete(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            HttpRequest<?> request;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/actions/"+actionDTO.getId(), Map.of());
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
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
        void canShowAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/actions/"+actionDTO.getId());
            response = blockingClient.exchange(request, ActionDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> getActionOptional = response.getBody(ActionDTO.class);
            assertTrue(getActionOptional.isPresent());
            assertEquals(actionDTO.getId(), getActionOptional.get().getId());
            assertNotNull(getActionOptional.get().getDateCreated());
        }

        @Test
        void cannotShowActionIfItBelongsToAGroupIAmNotAMemberOf(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create Group theta's Grant, Action, etc
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            GrantDTO thetaApplicationGrant = entityUtil.createGenericApplicationGrant(theta.getId());

            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("ThetaActionInterval", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> thetaActionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(thetaActionIntervalDTOOptional.isPresent());
            ActionIntervalDTO thetaActionInterval = thetaActionIntervalDTOOptional.get();

            response = entityUtil.createAction(thetaApplicationGrant.getId(), thetaActionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> thetaActionOptional = response.getBody(ActionDTO.class);
            assertTrue(thetaActionOptional.isPresent());
            ActionDTO thetaActionDTO = thetaActionOptional.get();

            // create Group omega's Grant, Action, etc
            response = entityUtil.createGroup("Omega");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> omegaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(omegaOptional.isPresent());
            SimpleGroupDTO omega = omegaOptional.get();

            GrantDTO omegaApplicationGrant = entityUtil.createGenericApplicationGrant(omega.getId());

            response = entityUtil.createActionInterval("OmegaActionInterval", omega.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> omegaActionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(omegaActionIntervalDTOOptional.isPresent());
            ActionIntervalDTO omegaActionInterval = omegaActionIntervalDTOOptional.get();

            response = entityUtil.createAction(omegaApplicationGrant.getId(), omegaActionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> omegaActionOptional = response.getBody(ActionDTO.class);
            assertTrue(omegaActionOptional.isPresent());
            ActionDTO omegaActionDTO = omegaActionOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/actions/"+omegaActionDTO.getId());
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
        void canListAllActionsLimitedToMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create Group theta's Grant, Action, etc
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            GrantDTO thetaApplicationGrant = entityUtil.createGenericApplicationGrant(theta.getId());

            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("ThetaActionInterval", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> thetaActionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(thetaActionIntervalDTOOptional.isPresent());
            ActionIntervalDTO thetaActionInterval = thetaActionIntervalDTOOptional.get();

            response = entityUtil.createAction(thetaApplicationGrant.getId(), thetaActionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> thetaActionOptional = response.getBody(ActionDTO.class);
            assertTrue(thetaActionOptional.isPresent());
            ActionDTO thetaActionDTO = thetaActionOptional.get();

            // create Group omega's Grant, Action, etc
            response = entityUtil.createGroup("Omega");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> omegaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(omegaOptional.isPresent());
            SimpleGroupDTO omega = omegaOptional.get();

            GrantDTO omegaApplicationGrant = entityUtil.createGenericApplicationGrant(omega.getId());

            response = entityUtil.createActionInterval("OmegaActionInterval", omega.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> omegaActionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(omegaActionIntervalDTOOptional.isPresent());
            ActionIntervalDTO omegaActionInterval = omegaActionIntervalDTOOptional.get();

            response = entityUtil.createAction(omegaApplicationGrant.getId(), omegaActionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> omegaActionOptional = response.getBody(ActionDTO.class);
            assertTrue(omegaActionOptional.isPresent());
            ActionDTO omegaActionDTO = omegaActionOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/actions");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertEquals(1, actionPage.get().getContent().size());
            Map expectedAction = (Map) actionPage.get().getContent().get(0);
            assertEquals(thetaActionDTO.getId().intValue(), (Integer) expectedAction.get("id"));

            // search by Grant name filter
            request = HttpRequest.GET("/actions?filter=mygrant");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            actionPage = response.getBody(Page.class);
            assertTrue(actionPage.isPresent());
            assertEquals(1, actionPage.get().getContent().size());
            expectedAction = (Map) actionPage.get().getContent().get(0);
            assertEquals(thetaActionDTO.getId().intValue(), (Integer) expectedAction.get("id"));
        }

        @Test
        public void canViewApplicationGrantsAndActionsByApplicationId() {
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create Group theta's Grant, Action, etc
            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            GrantDTO thetaApplicationGrant = entityUtil.createGenericApplicationGrant(theta.getId());

            response = entityUtil.addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("ThetaActionInterval", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> thetaActionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(thetaActionIntervalDTOOptional.isPresent());
            ActionIntervalDTO thetaActionInterval = thetaActionIntervalDTOOptional.get();

            response = entityUtil.createAction(thetaApplicationGrant.getId(), thetaActionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> thetaActionOptional = response.getBody(ActionDTO.class);
            assertTrue(thetaActionOptional.isPresent());
            ActionDTO thetaActionDTO = thetaActionOptional.get();

            loginAsNonAdmin();

            Page page;

            // public permissions with public application
            request = HttpRequest.GET("/application_grants/application/"+thetaApplicationGrant.getApplicationId());
            page = blockingClient.retrieve(request, Page.class);
            assertFalse(page.isEmpty());
            assertEquals(1, page.getContent().size());
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
        void cannotCreateAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            loginAsNonAdmin();

            // create action
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
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
        void cannotDeleteAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/actions/"+actionDTO.getId(), Map.of());
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

        // show
        @Test
        void cannotShowAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            assertEquals(OK, response.getStatus());
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionOptional = response.getBody(ActionDTO.class);
            assertTrue(actionOptional.isPresent());
            ActionDTO actionDTO = actionOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/actions/"+actionDTO.getId());
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
        void cannotListAllActions(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;
            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            // add member to group
            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // other group and Grant
            response = entityUtil.createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();
            GrantDTO applicationGrantZeta = entityUtil.createGenericApplicationGrant(zeta.getId());

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            response = entityUtil.createActionInterval("MyZetaActionInterval", zeta.getId());
            Optional<ActionIntervalDTO> zetaActionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(zetaActionIntervalDTOOptional.isPresent());
            ActionIntervalDTO zetaActionIntervalDTO = zetaActionIntervalDTOOptional.get();

            // create grant durations
            response = entityUtil.createAction(applicationGrantZeta.getId(), zetaActionIntervalDTO.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/actions");
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        void cannotShowAnAction(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            GrantDTO applicationGrant = entityUtil.createGenericApplicationGrant();
            Long groupId = applicationGrant.getGroupId();

            // add member to group
            response = entityUtil.addGroupMembership(groupId, "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            response = entityUtil.createActionInterval("MyActionInterval", groupId);
            Optional<ActionIntervalDTO> actionIntervalDTOOptional = response.getBody(ActionIntervalDTO.class);
            assertTrue(actionIntervalDTOOptional.isPresent());
            ActionIntervalDTO actionInterval = actionIntervalDTOOptional.get();

            // create action
            response = entityUtil.createAction(applicationGrant.getId(), actionInterval.getId());
            assertEquals(OK, response.getStatus());
            Optional<ActionDTO> actionDTOOPtional = response.getBody(ActionDTO.class);
            assertTrue(actionDTOOPtional.isPresent());
            ActionDTO actionDTO = actionDTOOPtional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/actions/"+actionDTO.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }
    }
}
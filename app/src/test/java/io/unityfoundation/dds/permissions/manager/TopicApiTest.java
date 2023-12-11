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
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import io.unityfoundation.dds.permissions.manager.testing.util.EntityLifecycleUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "TopicApiTest")
@MicronautTest
public class TopicApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    @Client("/api")
    HttpClient client;

    @Inject
    EntityLifecycleUtil entityUtil;

    @Inject
    TopicRepository topicRepository;

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

    @Requires(property = "spec.name", value = "TopicApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "TopicApiTest")
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
        void cannotCreateOnItsOwnWithoutAGroupAssociation(){
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("testTopic1");
            topicDTO.setKind(TopicKind.B);
            HttpRequest<?> request = HttpRequest.POST("/topics/save", topicDTO);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_REQUIRES_GROUP_ASSOCIATION.equals(map.get("code"))));
        }

        @Test
        void canCreateWithGroupAssociation(){
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topic = response.getBody(TopicDTO.class);
            assertTrue(topic.isPresent());

            // assert expected Canonical Name
            assertEquals("B."+theta.getId()+".Abc123", topic.get().getCanonicalName());
        }

        @Test
        public void cannotCreateGroupWithNullNorWhitespace() {

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);

            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));

            topicDTO.setName("     ");
            request = HttpRequest.POST("/topics/save", topicDTO);
            HttpRequest<?> finalRequest1 = request;
            HttpClientResponseException exception1 = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1);
            });
            assertEquals(BAD_REQUEST, exception1.getStatus());
            bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));
        }

        @Test
        public void cannotCreateWithNameLessThanThreeCharacters() {

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("A");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);

            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS.equals(map.get("code"))));
        }

        @Test
        public void createWithDescriptionAndDenyIfDescriptionIsMoreThanFourThousandChars() {
            HttpResponse<?> response;

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("A Topic Name");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());
            topicDTO.setDescription("My topic description");
            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertNotNull(topicOptional.get().getDescription());
            assertEquals("My topic description", topicOptional.get().getDescription());


            String FourKString = new String(new char[4001]).replace("\0", "s");;
            topicDTO.setDescription(FourKString);
            request = HttpRequest.POST("/topics/save", topicDTO);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, ApplicationDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_DESCRIPTION_CANNOT_BE_MORE_THAN_FOUR_THOUSAND_CHARACTERS.equals(map.get("code"))));
        }

        @Test
        public void createWithDescriptionWithFourThousandChars() {
            HttpResponse<?> response;

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("A Topic Name");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());
            topicDTO.setDescription("My topic description");
            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO savedTopic = topicOptional.get();
            assertNotNull(savedTopic.getDescription());
            assertEquals("My topic description", savedTopic.getDescription());

            String FourKString = new String(new char[4000]).replace("\0", "s");;
            savedTopic.setDescription(FourKString);
            request = HttpRequest.POST("/topics/save", savedTopic);
            response = blockingClient.exchange(request, ApplicationDTO.class);
            assertEquals(OK, response.getStatus());
        }

        @Test
        public void createWithPublicGroup() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create public group
            SimpleGroupDTO group = new SimpleGroupDTO();
            group.setName("Beta");
            group.setDescription("myDescription");
            group.setPublic(true);
            request = HttpRequest.POST("/groups/save", group);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> betaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(betaOptional.isPresent());
            SimpleGroupDTO betaDTO = betaOptional.get();

            // create private topic allowed
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("MyTopic");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(betaDTO.getId());
            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO topic = topicOptional.get();
            assertNotNull(topic.getPublic());
            assertFalse(topic.getPublic());

            // update from private to public allowed
            topic.setPublic(true);
            request = HttpRequest.POST("/topics/save", topic);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> body = response.getBody(TopicDTO.class);
            assertTrue(body.isPresent());
            assertTrue(body.get().getPublic());

            // create public topic allowed
            TopicDTO publicTopicDTO = new TopicDTO();
            publicTopicDTO.setName("MyPublicTopic");
            publicTopicDTO.setKind(TopicKind.B);
            publicTopicDTO.setGroup(betaDTO.getId());
            publicTopicDTO.setPublic(true);
            request = HttpRequest.POST("/topics/save", publicTopicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> publicTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(publicTopicOptional.isPresent());
            TopicDTO savedPublicTopic = publicTopicOptional.get();
            assertNotNull(savedPublicTopic.getPublic());
            assertTrue(savedPublicTopic.getPublic());
        }

        @Test
        public void createWithPrivateGroup() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create private group (null implies false)
            SimpleGroupDTO group = new SimpleGroupDTO();
            group.setName("Beta");
            group.setDescription("myDescription");
            request = HttpRequest.POST("/groups/save", group);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> betaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(betaOptional.isPresent());
            SimpleGroupDTO betaDTO = betaOptional.get();

            // create private topic allowed
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("MyTopic");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(betaDTO.getId());
            topicDTO.setPublic(false);
            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO topic = topicOptional.get();
            assertNotNull(topic.getPublic());
            assertFalse(topic.getPublic());

            // update from private to public not allowed (pub sub-entity not allowed under private Group)
            topic.setPublic(true);
            request = HttpRequest.POST("/topics/save", topic);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(itm -> ResponseStatusCodes.TOPIC_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP.equals(itm.get("code"))));


            // create public topic (not allowed)
            TopicDTO publicTopicDTO = new TopicDTO();
            publicTopicDTO.setName("MyPublicTopic");
            publicTopicDTO.setKind(TopicKind.B);
            publicTopicDTO.setGroup(betaDTO.getId());
            publicTopicDTO.setPublic(true);
            request = HttpRequest.POST("/topics/save", publicTopicDTO);
            HttpRequest<?> finalRequest1 = request;
            exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, TopicDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(itm -> ResponseStatusCodes.TOPIC_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP.equals(itm.get("code"))));
        }

        @Test
        public void createShouldTrimNameWhitespaces() {
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("   Abc123  ");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topic = response.getBody(TopicDTO.class);
            assertTrue(topic.isPresent());
            assertEquals("Abc123", topic.get().getName());
        }

        @Test
        public void cannotUpdateTopicNameNorKind() {
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            String originalName = "Abc123";
            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName(originalName);
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals(originalName, topicOptional.get().getName());

            // update attempt
            topicDTO = topicOptional.get();
            topicDTO.setName("UpdatedTestTopic2");
            request = HttpRequest.POST("/topics/save", topicDTO);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, thrown.getStatus());
            Optional<List> bodyOptional = thrown.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_NAME_UPDATE_NOT_ALLOWED.equals(map.get("code"))));


            topicDTO.setName(originalName);
            topicDTO.setKind(TopicKind.C);
            request = HttpRequest.POST("/topics/save", topicDTO);
            HttpRequest<?> finalRequest1 = request;
            thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1);
            });
            assertEquals(BAD_REQUEST, thrown.getStatus());
            bodyOptional = thrown.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_KIND_UPDATE_NOT_ALLOWED.equals(map.get("code"))));
        }

        @Test
        public void canUpdateTopicDescriptionAndOrPublic() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            Group theta = new Group("Theta");
            request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Abc123", topicOptional.get().getName());

            // with same name different description
            TopicDTO savededTopicDTO = topicOptional.get();
            savededTopicDTO.setDescription("This is a description");
            request = HttpRequest.POST("/topics/save", savededTopicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> updatedTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(updatedTopicOptional.isPresent());
            TopicDTO updatedTopic = updatedTopicOptional.get();
            assertEquals("This is a description", updatedTopic.getDescription());
        }

        @Test
        public void createdLastUpdatedFieldsArePopulatedAndNotEditable() {
            HttpRequest<?> request;
            HttpResponse<?> response;


            SimpleGroupDTO theta = new SimpleGroupDTO();
            theta.setName("Theta");
            request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            TopicDTO createdTopic = topicOptional.get();
            assertEquals("Abc123", createdTopic.getName());
            Instant createdDate = createdTopic.getDateCreated();
            Instant updatedDate = createdTopic.getDateUpdated();
            assertNotNull(createdDate);
            assertNotNull(updatedDate);
            assertEquals(createdDate, updatedDate);

            // with same name different description
            createdTopic.setDescription("This is a description");
            Instant date = Instant.ofEpochMilli(1675899232);
            createdTopic.setDateCreated(date);
            createdTopic.setDateUpdated(date);
            request = HttpRequest.POST("/topics/save", createdTopic);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> updatedTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(updatedTopicOptional.isPresent());
            TopicDTO updatedTopic = updatedTopicOptional.get();
            assertEquals("This is a description", updatedTopic.getDescription());
            assertNotEquals(date, updatedTopic.getDateCreated());
            assertNotEquals(date, updatedTopic.getDateUpdated());
            assertEquals(createdDate, updatedTopic.getDateCreated());
            assertNotEquals(updatedDate, updatedTopic.getDateUpdated());
        }

        @Test
        public void cannotCreateTopicWithSameNameInGroup() {
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topic = response.getBody(TopicDTO.class);
            assertTrue(topic.isPresent());
            assertEquals("Abc123", topic.get().getName());

            // confirm it is topic with kind B and not C
            topicDTO.setKind(TopicKind.C);
            request = HttpRequest.POST("/topics/save", topicDTO);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.TOPIC_ALREADY_EXISTS.equals(group.get("code"))));
        }

        //show
        @Test
        void canShowTopicAssociatedToAGroup(){
            // create group
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topic
            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            // show topic
            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicShowResponse = response.getBody(TopicDTO.class);
            assertTrue(topicShowResponse.isPresent());
            assertNotNull(topicShowResponse.get().getId());
            assertNotNull(topicShowResponse.get().getGroup());
            assertNotNull(topicShowResponse.get().getGroupName());
        }

        @Test
        public void canViewPublic() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            // create group
            SimpleGroupDTO theta = new SimpleGroupDTO();
            theta.setName("Theta");
            theta.setDescription("myDescription");
            theta.setPublic(true);
            request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topic
            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());
            xyzDTO.setPublic(true);

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> savedAppOptional1 = response.getBody(TopicDTO.class);
            assertTrue(savedAppOptional1.isPresent());
        }

        // list all topics from all groups
        @Test
        void canListAllTopicsAndTopicsWithSameNameCanExistSitewide(){
            // Group - Topics
            // ---
            // Green - Xyz789
            // Yellow - Abc123 & Xyz789

            // create groups
            Group green = new Group("Green");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", green);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> greenOptional = response.getBody(Group.class);
            assertTrue(greenOptional.isPresent());
            green = greenOptional.get();

            Group yellow = new Group("Yellow");
            request = HttpRequest.POST("/groups/save", yellow);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> yellowOptional = response.getBody(Group.class);
            assertTrue(yellowOptional.isPresent());
            yellow = yellowOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(yellow.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(green.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // site-wide test
            xyzDTO.setGroup(yellow.getId());
            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/topics");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(3, topicPage.get().getContent().size());
        }

        @Test
        void canListAllTopicsWithFilter(){
            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());
            abcDTO.setDescription("AbcDescription");

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // support case-insensitive
            request = HttpRequest.GET("/topics?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/topics?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());

            // topic description
            request = HttpRequest.GET("/topics?filter=bcdescrip");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
        }

        @Test
        void canLisTopicsWithGroupId(){
            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> abcDtoOptional = response.getBody(TopicDTO.class);
            assertTrue(abcDtoOptional.isPresent());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzDtoOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzDtoOptional.isPresent());

            // can list both topics
            request = HttpRequest.GET("/topics?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            Map map = (Map) topicPage.get().getContent().get(0);
            assertEquals(map.get("id"), xyzDtoOptional.get().getId().intValue());

            request = HttpRequest.GET("/topics?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            map = (Map) topicPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcDtoOptional.get().getId().intValue());

            // in addition to group, support filter param
            request = HttpRequest.GET("/topics?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            map = (Map) topicPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcDtoOptional.get().getId().intValue());
        }

        @Test
        void canListAllTopicsNameInAscendingOrderByDefault(){
            // Group - Topics
            // ---
            // Theta - Xyz789 & Def456
            // Zeta - Abc123 & Def456

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            TopicDTO defDTO = new TopicDTO();
            defDTO.setName("Def456");
            defDTO.setKind(TopicKind.C);
            defDTO.setGroup(zeta.getId());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", defDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            defDTO.setGroup(theta.getId());
            request = HttpRequest.POST("/topics/save", defDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/topics");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            List<Map> topics = topicPage.get().getContent();

            // topic names sorted
            List<String> topicNames = topics.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(topicNames.stream().sorted().collect(Collectors.toList()), topicNames);

            // group names should be sorted by topic
            List<String> defTopics = topics.stream().filter(map -> {
                String topicName = (String) map.get("name");
                return topicName.equals("Def456");
            }).flatMap(map -> Stream.of((String) map.get("groupName"))).collect(Collectors.toList());
            assertEquals(defTopics.stream().sorted().collect(Collectors.toList()), defTopics);
        }

        @Test
        void canListAllTopicsNameInDescendingOrder(){
            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123 & Def456

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            TopicDTO defDTO = new TopicDTO();
            defDTO.setName("Def456");
            defDTO.setKind(TopicKind.C);
            defDTO.setGroup(zeta.getId());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", defDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/topics?sort=name,desc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            List<Map> topics = topicPage.get().getContent();

            List<String> topicNames = topics.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(topicNames.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList()), topicNames);
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
        void canCreateTopic(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
        }

        // delete
        @Test
        void canDeleteTopic(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topic = response.getBody(TopicDTO.class);
            assertTrue(topic.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/topics/"+topic.get().getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
        }

        @Test
        void cannotDeleteAnApplication(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create application
            response = entityUtil.createApplication("ApplicationOne", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOneOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOneOptional.isPresent());
            ApplicationDTO applicationOne = applicationOneOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.DELETE("/applications/"+applicationOne.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
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
        void cannotCreateTopic(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteTopic(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topic = response.getBody(TopicDTO.class);
            assertTrue(topic.isPresent());

            loginAsNonAdmin();

            // delete attempt
            HttpRequest<?> request2 = HttpRequest.DELETE("/topics/"+topic.get().getId(), Map.of());
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request2);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // show
        @Test
        void canShowTopicWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics - Members
            // ---
            // Theta - Xyz789 - jjones
            // Zeta - Abc123 - None

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicResponseOptional = response.getBody(TopicDTO.class);
            assertTrue(topicResponseOptional.isPresent());
            assertEquals("Xyz789", topicResponseOptional.get().getName());
            assertEquals("Theta", topicResponseOptional.get().getGroupName());
        }

        @Test
        void cannotShowTopicIfTopicBelongsToAGroupIAmNotAMemberOf(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics - Members
            // ---
            // Theta - Xyz789 - jjones
            // Omega - Abc123 - None

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group omega = new Group("Omega");
            request = HttpRequest.POST("/groups/save", omega);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> omegaOptional = response.getBody(Group.class);
            assertTrue(omegaOptional.isPresent());
            omega = omegaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(omega.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> abcTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(abcTopicOptional.isPresent());
            TopicDTO abcTopic = abcTopicOptional.get();

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics/show/"+abcTopic.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        @Test
        public void canViewPublic() {

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create group
            SimpleGroupDTO theta = new SimpleGroupDTO();
            theta.setName("Theta");
            theta.setDescription("myDescription");
            theta.setPublic(true);
            request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topic
            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());
            xyzDTO.setPublic(true);

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> savedAppOptional1 = response.getBody(TopicDTO.class);
            assertTrue(savedAppOptional1.isPresent());
        }

        // list
        @Test
        void canListAllTopicsLimitedToMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            Map expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));
        }

        @Test
        void canListTopicsWithFilterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group
            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());
            xyzDTO.setDescription("XyzDescription");

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            // support case-insensitive
            request = HttpRequest.GET("/topics?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            Map expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));

            // Negative case
            request = HttpRequest.GET("/topics?filter=abc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(0, topicPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/topics?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));

            // topic description
            request = HttpRequest.GET("/topics?filter=yzDescrip");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));
        }

        @Test
        void canListTopicsWithGroupParameterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group
            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            // group search
            request = HttpRequest.GET("/topics?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            Map expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));

            // filter param support
            request = HttpRequest.GET("/topics?filter=xyz&group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));

            // Negative cases
            request = HttpRequest.GET("/topics?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(0, topicPage.get().getContent().size());

            request = HttpRequest.GET("/topics?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(0, topicPage.get().getContent().size());
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

        @Test
        public void canViewPublic() {

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create group
            SimpleGroupDTO theta = new SimpleGroupDTO();
            theta.setName("Theta");
            theta.setDescription("myDescription");
            theta.setPublic(true);
            request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topic
            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());
            xyzDTO.setPublic(true);

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> savedAppOptional1 = response.getBody(TopicDTO.class);
            assertTrue(savedAppOptional1.isPresent());
        }

        // create
        @Test
        void cannotCreateTopic(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            loginAsNonAdmin();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteTopic(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topics
            TopicDTO topicDTO = new TopicDTO();
            topicDTO.setName("Abc123");
            topicDTO.setKind(TopicKind.B);
            topicDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", topicDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topic = response.getBody(TopicDTO.class);
            assertTrue(topic.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/topics/"+topic.get().getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // show
        @Test
        void cannotShowTopicWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics - Members
            // ---
            // Theta - Xyz789 - jjones
            // Zeta - Abc123 - None

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
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
        public void canViewPublic() {

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create group
            SimpleGroupDTO theta = new SimpleGroupDTO();
            theta.setName("Theta");
            theta.setDescription("myDescription");
            theta.setPublic(true);
            request = HttpRequest.POST("/groups/save", theta);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // create topic
            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());
            xyzDTO.setPublic(true);

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicDTO.class);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        void cannotListAllTopics(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group
            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics");
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        void cannotShowATopicWithGroupAssociation(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            // create groups
            Group theta = new Group("Theta");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", theta);
            HttpResponse<?> response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> thetaOptional = response.getBody(Group.class);
            assertTrue(thetaOptional.isPresent());
            theta = thetaOptional.get();

            // add member to group
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(theta.getId());
            dto.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group
            Group zeta = new Group("Zeta");
            request = HttpRequest.POST("/groups/save", zeta);
            response = blockingClient.exchange(request, Group.class);
            assertEquals(OK, response.getStatus());
            Optional<Group> zetaOptional = response.getBody(Group.class);
            assertTrue(zetaOptional.isPresent());
            zeta = zetaOptional.get();

            // create topics
            TopicDTO abcDTO = new TopicDTO();
            abcDTO.setName("Abc123");
            abcDTO.setKind(TopicKind.B);
            abcDTO.setGroup(zeta.getId());

            TopicDTO xyzDTO = new TopicDTO();
            xyzDTO.setName("Xyz789");
            xyzDTO.setKind(TopicKind.B);
            xyzDTO.setGroup(theta.getId());

            request = HttpRequest.POST("/topics/save", abcDTO);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/topics/save", xyzDTO);
            response = blockingClient.exchange(request, TopicDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> xyzTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(xyzTopicOptional.isPresent());
            TopicDTO xyzTopic = xyzTopicOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topics/show/"+xyzTopic.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }
    }
}

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
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserResponseDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.CreateTopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.TopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.UpdateTopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "TopicSetApiTest")
@MicronautTest
public class TopicSetApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    @Client("/api")
    HttpClient client;

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

    @Requires(property = "spec.name", value = "TopicSetApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "TopicSetApiTest")
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
            CreateTopicSetDTO abcDTO = new CreateTopicSetDTO();
            abcDTO.setName("abc");

            HttpRequest<?> request = HttpRequest.POST("/topic-sets", abcDTO);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request, CreateTopicSetDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_SET_REQUIRES_GROUP_ASSOCIATION.equals(map.get("code"))));
        }

        @Test
        void canCreateWithGroupAssociation() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSet = response.getBody(TopicSetDTO.class);
            assertTrue(topicSet.isPresent());
        }

        @Test
        public void createdLastUpdatedFieldsArePopulatedAndNotEditable() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());

            TopicSetDTO topicSet = topicSetOptional.get();

            assertEquals("Abc123", topicSet.getName());
            Instant createdDate = topicSet.getDateCreated();
            Instant updatedDate = topicSet.getDateUpdated();
            assertNotNull(createdDate);
            assertNotNull(updatedDate);
            assertEquals(createdDate, updatedDate);

            // different name
            topicSet.setName("Xyz789");
            Instant date = Instant.ofEpochMilli(1675899232);
            topicSet.setDateCreated(date);
            topicSet.setDateUpdated(date);
            request = HttpRequest.PUT("/topic-sets/"+topicSet.getId(), topicSet);
            response = blockingClient.exchange(request, UpdateTopicSetDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> updatedTopicOptional = response.getBody(TopicSetDTO.class);
            assertTrue(updatedTopicOptional.isPresent());
            TopicSetDTO updatedTopicSet = updatedTopicOptional.get();
            assertNotEquals(date, updatedTopicSet.getDateCreated());
            assertNotEquals(date, updatedTopicSet.getDateUpdated());
            assertEquals("Xyz789", updatedTopicSet.getName());
            assertEquals(createdDate, updatedTopicSet.getDateCreated());
            assertNotEquals(updatedDate, updatedTopicSet.getDateUpdated());
        }

        @Test
        public void cannotCreateWithNullNorWhitespace() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topics
            CreateTopicSetDTO topicSetDTO = new CreateTopicSetDTO();
            topicSetDTO.setGroupId(theta.getId());

            request = HttpRequest.POST("/topic-sets/", topicSetDTO);

            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, CreateTopicSetDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_SET_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));

            topicSetDTO.setName("     ");
            request = HttpRequest.POST("/topic-sets", topicSetDTO);
            HttpRequest<?> finalRequest1 = request;
            HttpClientResponseException exception1 = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, CreateTopicSetDTO.class);
            });
            assertEquals(BAD_REQUEST, exception1.getStatus());
            bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_SET_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));
        }

        @Test
        public void cannotCreateWithNameLessThanThreeCharacters() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createTopicSet("A", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_SET_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS.equals(map.get("code"))));
        }

        @Test
        public void createShouldTrimNameWhitespaces() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createTopicSet("   Abc123  ", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSet = response.getBody(TopicSetDTO.class);
            assertTrue(topicSet.isPresent());
            assertEquals("Abc123", topicSet.get().getName());
        }

        @Test
        public void cannotUpdateGroupAssociation() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO abcTopicSet = topicSetOptional.get();

            // update attempt
            abcTopicSet.setGroupId(zeta.getId());
            request = HttpRequest.PUT("/topic-sets/"+abcTopicSet.getId(), abcTopicSet);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, thrown.getStatus());
            Optional<List> bodyOptional = thrown.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_SET_ALREADY_EXISTS.equals(map.get("code"))));
        }

        @Test
        public void canUpdateName() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic sets
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO savedTopicSet = topicSetOptional.get();
            assertEquals("Abc123", savedTopicSet.getName());

            // with different name
            savedTopicSet.setName("NewName123");
            request = HttpRequest.PUT("/topic-sets/"+savedTopicSet.getId(), savedTopicSet);
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> updatedTopicOptional = response.getBody(TopicSetDTO.class);
            assertTrue(updatedTopicOptional.isPresent());
            TopicSetDTO updatedTopicSet = updatedTopicOptional.get();
            assertEquals("NewName123", updatedTopicSet.getName());
        }

        @Test
        public void cannotCreateTopicSetWithSameNameInGroup() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic sets
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO savedTopicSet = topicSetOptional.get();
            assertEquals("Abc123", savedTopicSet.getName());

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createTopicSet("Abc123", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.TOPIC_SET_ALREADY_EXISTS.equals(group.get("code"))));
        }

        //show
        @Test
        void canShowTopicAssociatedToAGroup(){
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO xyzTopicSet = topicSetOptional.get();

            // show topic
            request = HttpRequest.GET("/topic-sets/"+xyzTopicSet.getId());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetShowResponse = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetShowResponse.isPresent());
            assertNotNull(topicSetShowResponse.get().getId());
            assertNotNull(topicSetShowResponse.get().getGroupId());
            assertNotNull(topicSetShowResponse.get().getGroupName());
        }

        // list all topics from all groups
        @Test
        void canListAllTopicSetsAndTopicSetsWithSameNameCanExistSitewide(){
            // Group - Topics
            // ---
            // Green - Xyz789
            // Yellow - Abc123 & Xyz789

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Green");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> greenOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(greenOptional.isPresent());
            SimpleGroupDTO green = greenOptional.get();

            response = createGroup("Yellow");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> yellowOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(yellowOptional.isPresent());
            SimpleGroupDTO yellow = yellowOptional.get();

            // create topic sets
            response = createTopicSet("Abc123", yellow.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Xyz789", green.getId());
            assertEquals(OK, response.getStatus());

            // site-wide test
            response = createTopicSet("Xyz789", yellow.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/topic-sets");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicSetPage = response.getBody(Page.class);
            assertTrue(topicSetPage.isPresent());
            assertEquals(3, topicSetPage.get().getContent().size());
        }

        @Test
        void canListAllTopicSetsWithFilter(){
            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create topics
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            // support case-insensitive
            request = HttpRequest.GET("/topic-sets?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicSetPage = response.getBody(Page.class);
            assertTrue(topicSetPage.isPresent());
            assertEquals(1, topicSetPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/topic-sets?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicSetPage = response.getBody(Page.class);
            assertTrue(topicSetPage.isPresent());
            assertEquals(1, topicSetPage.get().getContent().size());
        }

        @Test
        void canLisTopicSetsWithGroupId(){
            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create topics
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> abcOptional = response.getBody(TopicSetDTO.class);
            assertTrue(abcOptional.isPresent());
            TopicSetDTO abcTopicSet = abcOptional.get();

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzOptional.isPresent());
            TopicSetDTO xyzTopicSet = xyzOptional.get();

            // can list both topics
            request = HttpRequest.GET("/topic-sets?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            Map map = (Map) topicPage.get().getContent().get(0);
            assertEquals(map.get("id"), xyzTopicSet.getId().intValue());

            request = HttpRequest.GET("/topic-sets?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            map = (Map) topicPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcTopicSet.getId().intValue());

            // in addition to group, support filter param
            request = HttpRequest.GET("/topic-sets?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            map = (Map) topicPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcTopicSet.getId().intValue());
        }

        @Test
        void canListAllTopicSetsNameInAscendingOrderByDefault(){
            // Group - Topics
            // ---
            // Theta - Xyz789 & Def456
            // Zeta - Abc123 & Def456

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create topics
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Def456", theta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/topic-sets");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            List<Map> topicSets = topicPage.get().getContent();

            // topic names sorted
            List<String> topicSetNames = topicSets.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(topicSetNames.stream().sorted().collect(Collectors.toList()), topicSetNames);

            // group names should be sorted by topic
            List<String> defTopics = topicSets.stream().filter(map -> {
                String topicName = (String) map.get("name");
                return topicName.equals("Def456");
            }).flatMap(map -> Stream.of((String) map.get("groupName"))).collect(Collectors.toList());
            assertEquals(defTopics.stream().sorted().collect(Collectors.toList()), defTopics);
        }

        @Test
        void canListAllTopicSetsNameInDescendingOrder(){
            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123 & Def456

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create topics
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/topic-sets?sort=name,desc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicSetPage = response.getBody(Page.class);
            assertTrue(topicSetPage.isPresent());
            List<Map> topicSets = topicSetPage.get().getContent();

            List<String> topicNames = topicSets.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(topicNames.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList()), topicNames);
        }

        //delete


        @Test
        void cannotAddTopicToATopicSetWithDifferentAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Alpha");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> alphaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(alphaOptional.isPresent());
            SimpleGroupDTO alpha = alphaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", alpha.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            // add theta topic to alpha topicset
            request = HttpRequest.POST("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicSetDTO.class);
            });
            assertEquals(CONFLICT, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_SET_AND_TOPIC_DOES_NOT_BELONG_TO_SAME_GROUP.equals(map.get("code"))));
        }

        @Test
        void cannotAddTopicToATopicSetIfPreviouslyAdded(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            // add topic
            request = HttpRequest.POST("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(CREATED, response.getStatus());
            topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            topicSet = topicSetOptional.get();
            assertFalse(topicSet.getTopics().isEmpty());

            // add again
            request = HttpRequest.POST("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicSetDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_ALREADY_EXISTS.equals(map.get("code"))));
        }

        @Test
        void cannotRemoveTopicFromATopicSetIfTopicDoesNotExists(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            // remove alpha topic from theta topicSet topics (DNE in topics)
            request = HttpRequest.DELETE("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicSetDTO.class);
            });
            assertEquals(NOT_FOUND, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.TOPIC_DOES_NOT_EXISTS_IN_TOPIC_SET.equals(map.get("code"))));
        }

        @Test
        void canAddSameTopicToTwoDifferentTopicSetsInSameGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetXyzOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetXyzOptional.isPresent());
            TopicSetDTO topicSetXyz = topicSetXyzOptional.get();

            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetAbcOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetAbcOptional.isPresent());
            TopicSetDTO topicSetAbc = topicSetAbcOptional.get();

            // add topic to topic sets Abc123 and Xyz789
            request = HttpRequest.POST("/topic-sets/" + topicSetAbc.getId() + "/" + aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(CREATED, response.getStatus());
            topicSetAbcOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetAbcOptional.isPresent());
            topicSetAbc = topicSetAbcOptional.get();
            assertFalse(topicSetAbc.getTopics().isEmpty());

            request = HttpRequest.POST("/topic-sets/" + topicSetXyz.getId() + "/" + aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(CREATED, response.getStatus());
            topicSetAbcOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetAbcOptional.isPresent());
            topicSetXyz = topicSetAbcOptional.get();
            assertFalse(topicSetXyz.getTopics().isEmpty());
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
        void canCreateTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
        }

        // delete
        @Test
        void canDeleteTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSet = response.getBody(TopicSetDTO.class);
            assertTrue(topicSet.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/topic-sets/"+topicSet.get().getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(NO_CONTENT, response.getStatus());
        }

        @Test
        void canAddTopicToTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            loginAsNonAdmin();

            // add topic
            request = HttpRequest.POST("/topic-sets/"+ topicSet.getId()+"/"+aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(CREATED, response.getStatus());
            topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            topicSet = topicSetOptional.get();
            assertFalse(topicSet.getTopics().isEmpty());
        }

        @Test
        void canRemoveTopicFromTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", true);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            // add topic
            request = HttpRequest.POST("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(CREATED, response.getStatus());
            topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            topicSet = topicSetOptional.get();
            assertFalse(topicSet.getTopics().isEmpty());

            loginAsNonAdmin();

            // remove topic
            request = HttpRequest.DELETE("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(OK, response.getStatus());
            topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            topicSet = topicSetOptional.get();
            assertNull(topicSet.getTopics());
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
        void cannotCreateTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create topic set
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createTopicSet("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> abcTopiSetcOptional = response.getBody(TopicSetDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            TopicSetDTO abcTopicSet = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            HttpRequest<?> request2 = HttpRequest.DELETE("/topic-sets/"+abcTopicSet.getId(), Map.of());
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
        void canShowTopicSetWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics - Members
            // ---
            // Theta - Xyz789 - jjones
            // Zeta - Abc123 - None

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic sets
            createTopicSet("Abc123", zeta.getId());
            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopicSetOptional.isPresent());
            TopicSetDTO xyzTopic = xyzTopicSetOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topic-sets/"+xyzTopic.getId());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetResponseOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetResponseOptional.isPresent());
            assertEquals("Xyz789", topicSetResponseOptional.get().getName());
            assertEquals("Theta", topicSetResponseOptional.get().getGroupName());
        }

        @Test
        void cannotShowTopicSetIfTopicSetBelongsToAGroupIAmNotAMemberOf(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics - Members
            // ---
            // Theta - Xyz789 - jjones
            // Omega - Abc123 - None

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();


            response = createGroup("Omega");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> omegaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(omegaOptional.isPresent());
            SimpleGroupDTO omega = omegaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic sets
            response = createTopicSet("Abc123", omega.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> abcTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(abcTopicOptional.isPresent());
            TopicDTO abcTopic = abcTopicOptional.get();

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopicSetOptional.isPresent());
            TopicSetDTO xyzTopic = xyzTopicSetOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topic-sets/"+abcTopic.getId());
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

        // list
        @Test
        void canListAllTopicsSetsLimitedToMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Topics
            // ---
            // Theta - Xyz789
            // Zeta - Abc123

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic sets
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> abcTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(abcTopicOptional.isPresent());

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopicSetOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/topic-sets");
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

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic sets
            createTopicSet("Abc123", zeta.getId());
            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopicSetOptional.isPresent());

            loginAsNonAdmin();

            // support case-insensitive
            request = HttpRequest.GET("/topic-sets?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            Map expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));

            // Negative case
            request = HttpRequest.GET("/topic-sets?filter=abc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(0, topicPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/topic-sets?filter=heta");
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

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic sets
            createTopicSet("Abc123", zeta.getId());
            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopicSetOptional.isPresent());

            loginAsNonAdmin();

            // group search
            request = HttpRequest.GET("/topic-sets?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            Map expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));

            // filter param support
            request = HttpRequest.GET("/topic-sets?filter=xyz&group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(1, topicPage.get().getContent().size());
            expectedTopic = (Map) topicPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedTopic.get("name"));

            // Negative cases
            request = HttpRequest.GET("/topic-sets?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(0, topicPage.get().getContent().size());

            request = HttpRequest.GET("/topic-sets?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            topicPage = response.getBody(Page.class);
            assertTrue(topicPage.isPresent());
            assertEquals(0, topicPage.get().getContent().size());
        }

        @Test
        void cannotAddTopicToTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            loginAsNonAdmin();

            // add topic attempt
            request = HttpRequest.POST("/topic-sets/"+ topicSet.getId()+"/"+aTopic.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicSetDTO.class);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        @Test
        void cannotRemoveTopicFromTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            // add topic
            request = HttpRequest.POST("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(CREATED, response.getStatus());
            topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            topicSet = topicSetOptional.get();
            assertFalse(topicSet.getTopics().isEmpty());

            loginAsNonAdmin();

            // remove topic attempt
            request = HttpRequest.DELETE("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicSetDTO.class);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
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
        void cannotCreateTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            loginAsNonAdmin();

            // create topic sets
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createTopicSet("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic sets
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> abcTopiSetcOptional = response.getBody(TopicSetDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            TopicSetDTO abcTopicSet = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/topic-sets/"+abcTopicSet.getId(), Map.of());
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
        void cannotShowTopicSetWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = zetaOptional.get();

            // create topic sets
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopiSetcOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopiSetcOptional.isPresent());
            TopicSetDTO xyzTopicSet = xyzTopiSetcOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topic-sets/"+xyzTopicSet.getId());
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

        @Test
        void cannotAddTopicToTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            loginAsNonAdmin();

            // add topic attempt
            request = HttpRequest.POST("/topic-sets/"+ topicSet.getId()+"/"+aTopic.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicSetDTO.class);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        @Test
        void cannotRemoveTopicFromTopicSet(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create topic
            response = createTopic(theta.getId(), "MyTopicA");
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> aTopicOptional = response.getBody(TopicDTO.class);
            assertTrue(aTopicOptional.isPresent());
            TopicDTO aTopic = aTopicOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // create topic set
            response = createTopicSet("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            TopicSetDTO topicSet = topicSetOptional.get();

            // add topic
            request = HttpRequest.POST("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            response = blockingClient.exchange(request, TopicSetDTO.class);
            assertEquals(CREATED, response.getStatus());
            topicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(topicSetOptional.isPresent());
            topicSet = topicSetOptional.get();
            assertFalse(topicSet.getTopics().isEmpty());

            loginAsNonAdmin();

            // remove topic attempt
            request = HttpRequest.DELETE("/topic-sets/" + topicSet.getId() + "/" + aTopic.getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, TopicSetDTO.class);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
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
        void cannotListAllTopicSets(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // other group
            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = thetaOptional.get();

            // create topic sets
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopicSetOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/topic-sets");
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        void cannotShowATopicSetWithGroupAssociation(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest<?> request;
            HttpResponse<?> response;

            // create groups
            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // add member to group
            response = addGroupMembership(theta.getId(), "jjones@test.test", false);
            assertEquals(OK, response.getStatus());

            // other group
            response = createGroup("Zeta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> zetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(zetaOptional.isPresent());
            SimpleGroupDTO zeta = thetaOptional.get();

            // create topic sets
            response = createTopicSet("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createTopicSet("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicSetDTO> xyzTopicSetOptional = response.getBody(TopicSetDTO.class);
            assertTrue(xyzTopicSetOptional.isPresent());
            TopicSetDTO xyzTopicSet = xyzTopicSetOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/topic-sets/"+xyzTopicSet.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }
    }

    private HttpResponse<?> createGroup(String groupName) {
        SimpleGroupDTO groupDTO = new SimpleGroupDTO();
        groupDTO.setName(groupName);
        HttpRequest<?> request = HttpRequest.POST("/groups/save", groupDTO);
        return blockingClient.exchange(request, SimpleGroupDTO.class);
    }

    private HttpResponse<?> createTopic(Long groupId, String name) {
        TopicDTO topicDTO = new TopicDTO();
        topicDTO.setName(name);
        topicDTO.setGroup(groupId);

        HttpRequest<?>  request = HttpRequest.POST("/topics/save", topicDTO);
        return blockingClient.exchange(request, TopicDTO.class);
    }

    private HttpResponse<?> addGroupMembership(Long groupId, String email, boolean isAdmin) {
        GroupUserDTO dto = new GroupUserDTO();
        dto.setPermissionsGroup(groupId);
        dto.setEmail(email);
        dto.setTopicAdmin(isAdmin);

        HttpRequest<?>  request = HttpRequest.POST("/group_membership", dto);
        return blockingClient.exchange(request, GroupUserResponseDTO.class);
    }

    private HttpResponse<?> createTopicSet(String name, Long groupId) {
        CreateTopicSetDTO abcDTO = new CreateTopicSetDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);

        HttpRequest<?> request = HttpRequest.POST("/topic-sets", abcDTO);
        return blockingClient.exchange(request, TopicSetDTO.class);
    }
}

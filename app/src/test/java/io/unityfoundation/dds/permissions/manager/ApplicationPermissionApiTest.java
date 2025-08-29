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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator;
import io.micronaut.security.token.jwt.generator.claims.JWTClaimsSetGenerator;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.exception.DPMErrorResponse;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.*;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUser;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;


@Property(name = "spec.name", value = "ApplicationPermissionApiTest")
@MicronautTest
public class ApplicationPermissionApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    MockSecurityService mockSecurityService;

    @Inject
    GroupRepository groupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GroupUserRepository groupUserRepository;

    @Inject
    ApplicationPermissionRepository applicationPermissionRepository;

    @Inject
    ReadPartitionRepository readPartitionRepository;

    @Inject
    WritePartitionRepository writePartitionRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    TopicRepository topicRepository;

    @Inject
    JWTClaimsSetGenerator jwtClaimsSetGenerator;

    @Inject
    JwtTokenGenerator jwtTokenGenerator;

    @Inject
    DbCleanup dbCleanup;

    @Inject
    @Client("/api")
    HttpClient client;

    @Inject
    AuthenticationFetcherReplacement mockAuthenticationFetcher;

    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
    }

    @Requires(property = "spec.name", value = "ApplicationPermissionApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "ApplicationPermissionApiTest")
    @Replaces(SecurityService.class)
    @Singleton
    static class MockSecurityService extends SecurityServiceReplacement {
    }

    @Nested
    class WhenAsAdmin {

        private Group testGroup;
        private Topic testTopic;
        private Application applicationOne;

        private Group publicGroup;
        private Application privateApplication;
        private Topic publicTopic;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            testGroup = groupRepository.save(new Group("TestGroup"));
            testTopic = topicRepository.save(new Topic("TestTopic", TopicKind.B, testGroup));
            applicationOne = applicationRepository.save(new Application("ApplicationOne", testGroup));

            publicGroup = groupRepository.save(new Group("PublicGroup", "Description", true));
            publicTopic = topicRepository.save(new Topic("PublicTestTopic", TopicKind.B, "topic desc", true, publicGroup));
            privateApplication = applicationRepository.save(new Application("ApplicationOne", publicGroup, "app desc", false));
        }

        @Test
        public void permissionIsDeletedPostTopicDeletion() {

            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // create app permission
            response = createApplicationPermission(applicationGrantToken, topicOptional.get().getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            // topic delete
            request = HttpRequest.DELETE("/topics/"+topicOptional.get().getId(), Map.of());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);

            assertTrue(applicationPermissionRepository.findById(permissionOptional.get().getId()).isEmpty());
        }

        @Test
        public void canListPermissionsNotAMemberOfTopicOrApplicationGroups() {
            Long applicationOneId = applicationOne.getId();
            Long testTopicId = testTopic.getId();
            addReadPermission(applicationOneId, testTopicId);

            HttpRequest<?> request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            Page page = blockingClient.retrieve(request,  Page.class);
            assertFalse(page.isEmpty());

            request = HttpRequest.GET("/application_permissions/topic/" + testTopicId);
            page = blockingClient.retrieve(request,  Page.class);
            assertFalse(page.isEmpty());
        }

        @Test
        public void permissionIsDeletedPostApplicationDeletion() {

            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // create app permission
            response = createApplicationPermission(applicationGrantToken, topicOptional.get().getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            // application delete
            request = HttpRequest.DELETE("/applications/"+applicationOptional.get().getId(), Map.of());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);

            assertTrue(applicationPermissionRepository.findById(permissionOptional.get().getId()).isEmpty());
        }

        @Test
        public void canUpdatePermissions() {
            Long applicationOneId = applicationOne.getId();
            Long testTopicId = testTopic.getId();

            addReadPermission(applicationOneId, testTopicId);

            HttpRequest<?> request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertTrue(content.stream().anyMatch((m) -> (boolean) m.get("read")));
            assertFalse(content.stream().anyMatch((m) -> (boolean) m.get("write")));
            int permissionId = (int) content.get(0).get("id");

            updatePermission(permissionId, false, true);

            request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertFalse(content.stream().anyMatch((m) -> (boolean) m.get("read")));
            assertTrue(content.stream().anyMatch((m) -> (boolean) m.get("write")));

            request = HttpRequest.DELETE("/application_permissions/" + permissionId);
            HttpResponse<Object> response = blockingClient.exchange(request);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatus());

            request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            content = (List<Map>) responseMap.get("content");
            assertTrue(content.isEmpty());
        }

        @Test
        public void canCreatAndUpdatePermissionsWithPartitions() {
            HttpRequest<?> request;
            Long applicationOneId = applicationOne.getId();
            Long testTopicId = testTopic.getId();


            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOneId);
            HttpResponse<String> response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create readPartitions + permissions
            AccessPermissionBodyDTO accessPermissionBodyDTO = new AccessPermissionBodyDTO(Set.of("PartitionA", "partition9"), null);
            accessPermissionBodyDTO.setWrite(true);
            
            request = HttpRequest.POST("/application_permissions/" + testTopicId, accessPermissionBodyDTO)
                    .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
            AccessPermissionDTO accessPermissionDTO = blockingClient.retrieve(request, AccessPermissionDTO.class);

            assertNotNull(accessPermissionDTO);
            assertTrue(accessPermissionDTO.isWrite());
            assertFalse(accessPermissionDTO.isRead());
            assertEquals(2, accessPermissionDTO.getReadPartitions().stream().count());
            assertTrue(accessPermissionDTO.getReadPartitions().stream().allMatch(s -> s.equals("PartitionA") || s.equals("partition9")));

            // get permission
            request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertTrue(content.stream().anyMatch((m) -> (boolean) m.get("write")));
            Map first = content.get(0);
            List partitionList = (List) first.get("readPartitions");
            assertTrue(partitionList.stream().allMatch(s -> s.equals("PartitionA") || s.equals("partition9")));
            int permissionId = (int) first.get("id");


            // update readPartitions
            accessPermissionBodyDTO = new AccessPermissionBodyDTO(Set.of("MyPart123"), Set.of("writePartition456"));
            accessPermissionBodyDTO.setRead(true);

            request = HttpRequest.PUT("/application_permissions/" + permissionId, accessPermissionBodyDTO);
            accessPermissionDTO = blockingClient.retrieve(request, AccessPermissionDTO.class);

            assertNotNull(accessPermissionDTO);
            assertTrue(accessPermissionDTO.isRead());
            assertFalse(accessPermissionDTO.isWrite());
            assertTrue(accessPermissionDTO.getReadPartitions().stream().allMatch(s -> s.equals("MyPart123")));
            assertTrue(accessPermissionDTO.getWritePartitions().stream().allMatch(s -> s.equals("writePartition456")));

            // expect index to respond the same as above
            request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertTrue(content.stream().anyMatch((m) -> (boolean) m.get("read")));
            first = content.get(0);
            partitionList = (List) first.get("readPartitions");
            assertTrue(partitionList.stream().allMatch(s -> s.equals("MyPart123")));
            partitionList = (List) first.get("writePartitions");
            assertTrue(partitionList.stream().allMatch(s -> s.equals("writePartition456")));
        }

        @Test
        public void cannotCreatAndUpdatePermissionsWithDuplicatePartitions() {
            HttpRequest<?> request;
            Long applicationOneId = applicationOne.getId();
            Long testTopicId = testTopic.getId();

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOneId);
            HttpResponse<String> response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create readPartitions + permissions
            Map payload = Map.of(
                    "readPartitions", List.of("cat", "cat"),
                    "write", true
            );

            request = HttpRequest.POST("/application_permissions/" + testTopicId, payload)
                    .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
            AccessPermissionDTO accessPermissionDTO = blockingClient.retrieve(request, AccessPermissionDTO.class);

            assertNotNull(accessPermissionDTO);
            assertTrue(accessPermissionDTO.isWrite());
            assertFalse(accessPermissionDTO.isRead());
            assertEquals(1, accessPermissionDTO.getReadPartitions().stream().count());
            assertTrue(accessPermissionDTO.getReadPartitions().stream().allMatch(s -> s.equals("cat")));

            // get permission
            request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertTrue(content.stream().anyMatch((m) -> (boolean) m.get("write")));
            Map first = content.get(0);
            List partitionList = (List) first.get("readPartitions");
            assertTrue(partitionList.stream().allMatch(s -> s.equals("cat")));
            int permissionId = (int) first.get("id");


            // update readPartitions
            payload = Map.of(
                    "writePartitions", List.of("dog", "dog"),
                    "read", true
            );

            request = HttpRequest.PUT("/application_permissions/" + permissionId, payload);
            accessPermissionDTO = blockingClient.retrieve(request, AccessPermissionDTO.class);

            assertNotNull(accessPermissionDTO);
            assertTrue(accessPermissionDTO.isRead());
            assertFalse(accessPermissionDTO.isWrite());
            assertTrue(accessPermissionDTO.getReadPartitions().stream().allMatch(s -> s.equals("cat")));
            assertTrue(accessPermissionDTO.getWritePartitions().stream().allMatch(s -> s.equals("dog")));

            // expect index to respond the same as above
            request = HttpRequest.GET("/application_permissions/application/" + applicationOneId);
            responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertTrue(content.stream().anyMatch((m) -> (boolean) m.get("read")));
            first = content.get(0);

            partitionList = (List) first.get("readPartitions");
            assertTrue(partitionList.stream().allMatch(s -> s.equals("cat")));
            assertEquals(1, partitionList.stream().count());

            partitionList = (List) first.get("writePartitions");
            assertTrue(partitionList.stream().allMatch(s -> s.equals("dog")));
            assertEquals(1, partitionList.stream().count());
        }

        @Test
        public void attemptToAssociateApplicationWithInvalidApplicationJwtToken() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // invalid jwt token
            Map<String, Object> stringObjectMap = jwtClaimsSetGenerator.generateClaimsSet(Map.of("myKey", "myVal"), 5000);
            Optional<String> s = jwtTokenGenerator.generateToken(stringObjectMap);

            Map<String, Boolean> payload = Map.of("read", true, "write", false);

            request = HttpRequest.POST("/application_permissions/" + topicOptional.get().getId(), payload)
                    .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, s.get());
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request, AccessPermissionDTO.class);
            });
            assertEquals(FORBIDDEN, exception.getStatus());
        }

        @Test
        public void tokenWithInvalidSignatureSecretShouldFail() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // change signature
            applicationGrantToken = applicationGrantToken + "w";

            // create app permission
            String finalApplicationGrantToken = applicationGrantToken;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationPermission(finalApplicationGrantToken, topicOptional.get().getId(), true, false);
            });
            assertEquals(FORBIDDEN, exception.getStatus());
        }

        @Test
        public void canDeleteApplicationPermission() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            Map<String, Boolean> payload = Map.of("read", true, "write", false);

            // create application permission
            request = HttpRequest.POST("/application_permissions/" + topicOptional.get().getId(), payload)
                    .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
            response = blockingClient.exchange(request, AccessPermissionDTO.class);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> accessPermissionDTOOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(accessPermissionDTOOptional.isPresent());

            // application permissions delete
            request = HttpRequest.DELETE("/application_permissions/"+accessPermissionDTOOptional.get().getId());
            response = blockingClient.exchange(request, HashMap.class);
            assertEquals(NO_CONTENT, response.getStatus());
        }

        @Test
        public void cannotCreateDuplicateEntries() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // create app permission
            response = createApplicationPermission(applicationGrantToken, topicOptional.get().getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            // second create attempt
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationPermission(applicationGrantToken, topicOptional.get().getId(), false, true);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.APPLICATION_PERMISSION_ALREADY_EXISTS.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void canViewAllApplicationPermissionsByApplication() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            response = createApplicationPermission(applicationGrantToken, testTopic.getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            request = HttpRequest.GET("/application_permissions/application/"+applicationOne.getId());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertEquals(permissionOptional.get().getId().intValue(), content.get(0).get("id"));
        }

        @Test
        public void canViewAllApplicationPermissionsByApplicationOrderedByTopicName() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            response = createApplicationPermission(applicationGrantToken, testTopic.getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            response = createApplicationPermission(applicationGrantToken, publicTopic.getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional1 = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional1.isPresent());

            request = HttpRequest.GET("/application_permissions/application/"+applicationOne.getId());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);

            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(2, content.size());

            List<String> topicNames = content.stream()
                    .flatMap(map -> Stream.of((String) map.get("topicName")))
                    .collect(Collectors.toList());
            assertEquals(topicNames.stream().sorted().collect(Collectors.toList()), topicNames);
        }

        @Test
        public void canViewAllApplicationPermissionsByTopic() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            response = createApplicationPermission(applicationGrantToken, testTopic.getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            request = HttpRequest.GET("/application_permissions/topic/"+testTopic.getId());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertEquals(permissionOptional.get().getId().intValue(), content.get(0).get("id"));
        }

        @Test
        public void canViewAllApplicationPermissionsByTopicIfPublicTopicPrivateGroup() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + privateApplication.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create permission
            response = createApplicationPermission(applicationGrantToken, publicTopic.getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            request = HttpRequest.GET("/application_permissions/topic/"+publicTopic.getId());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertEquals(permissionOptional.get().getId().intValue(), content.get(0).get("id"));

            // expect canonical name is given
            assertEquals("B."+publicGroup.getId()+".PublicTestTopic", content.get(0).get("topicCanonicalName"));
        }

        @Test
        public void canViewAllApplicationPermissionsByApplicationIfPublicTopicPrivateGroup() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + privateApplication.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create permission
            response = createApplicationPermission(applicationGrantToken, publicTopic.getId(), true, false);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            request = HttpRequest.GET("/application_permissions/application/"+privateApplication.getId());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertEquals(permissionOptional.get().getId().intValue(), content.get(0).get("id"));
        }

        private void addReadWritePermission(Long applicationId, Long topicId) {
            addPermission(applicationId, topicId, true, true);
        }

        private void addReadPermission(Long applicationId, Long topicId) {
            addPermission(applicationId, topicId, true, false);
        }

        private void addPermission(Long applicationId, Long topicId, boolean read, boolean write) {
            HttpRequest<?> request;
            HashMap<String, Object> responseMap;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationId);
            HttpResponse<String> response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            Map<String, Boolean> payload = Map.of("read", read, "write", write);

            request = HttpRequest.POST("/application_permissions/" + topicId, payload)
                    .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
            responseMap = blockingClient.retrieve(request, HashMap.class);

            assertNotNull(responseMap);

            assertEquals(read, responseMap.get("read"));
            assertEquals(write, responseMap.get("write"));
            assertEquals(applicationId.intValue(), responseMap.get("applicationId"));
            assertEquals(topicId.intValue(), responseMap.get("topicId"));
        }

        private void updatePermission(int permissionId, boolean read, boolean write) {
            HttpRequest<?> request;
            HashMap<String, Object> responseMap;
            Map payload = Map.of(
                    "read", read,
                    "write", write
            );

            request = HttpRequest.PUT("/application_permissions/" + permissionId, payload);
            responseMap = blockingClient.retrieve(request, HashMap.class);

            assertNotNull(responseMap);

            assertEquals(permissionId, responseMap.get("id"));
            assertEquals(read, responseMap.get("read"));
            assertEquals(write, responseMap.get("write"));
        }
    }

    @Nested
    class WhenAsAnApplicationAdmin {

        private Group testGroup;
        private Topic testTopic;
        private Application applicationOne;
        private ApplicationPermission applicationPermissionOne;
        private Group testGroupTwo;
        private Topic testTopicTwo;
        private Application applicationTwo;
        private ApplicationPermission applicationPermissionTwo;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            User justin = userRepository.save(new User("jjones@test.test"));

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            testGroup = groupRepository.save(new Group("TestGroup"));
            testTopic = topicRepository.save(new Topic("TestTopic", TopicKind.B, testGroup));
            applicationOne = applicationRepository.save(new Application("ApplicationOne", testGroup));
            applicationPermissionOne = applicationPermissionRepository.save(new ApplicationPermission(applicationOne, testTopic, true, true));
            GroupUser membership = new GroupUser(testGroup, justin);
            membership.setApplicationAdmin(true);
            groupUserRepository.save(membership);

            testGroupTwo = groupRepository.save(new Group("TestGroup1"));
            testTopicTwo = topicRepository.save(new Topic("TestTopic1", TopicKind.C, testGroupTwo));
            applicationTwo = applicationRepository.save(new Application("ApplicationTwo", testGroupTwo));
            applicationPermissionTwo = applicationPermissionRepository.save(new ApplicationPermission(applicationTwo, testTopicTwo, false, true));
        }

        void loginAsApplicationAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of()
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void canDeleteApplicationPermission() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            User justin = userRepository.findByEmail("jjones@test.test").get();
            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setApplicationAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            Map<String, Boolean> payload = Map.of("read", true, "write", false);

            // create application permission
            request = HttpRequest.POST("/application_permissions/" + topicOptional.get().getId(), payload)
                    .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
            response = blockingClient.exchange(request, AccessPermissionDTO.class);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> accessPermissionDTOOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(accessPermissionDTOOptional.isPresent());

            loginAsApplicationAdmin();

            // application permissions delete
            request = HttpRequest.DELETE("/application_permissions/"+accessPermissionDTOOptional.get().getId());
            response = blockingClient.exchange(request, HashMap.class);
            assertEquals(NO_CONTENT, response.getStatus());
        }

        @Test
        public void canOnlyListPermissionsOfGroupsUserIsAMemberOfByApplication() {
            HttpResponse response;
            HttpRequest request;

            loginAsApplicationAdmin();

            request = HttpRequest.GET("/application_permissions/application/"+applicationOne.getId());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertEquals(applicationPermissionOne.getId().intValue(), content.get(0).get("id"));
        }

        @Test
        public void canOnlyListPermissionsOfGroupsUserIsAMemberOfByTopic() {
            HttpResponse response;
            HttpRequest request;

            loginAsApplicationAdmin();

            request = HttpRequest.GET("/application_permissions/topic/"+testTopic.getId());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
            assertEquals(applicationPermissionOne.getId().intValue(), content.get(0).get("id"));
        }
    }

    @Nested
    class WhenAsATopicAdmin {

        private Group testGroup;
        private Topic testTopic;
        private Application applicationOne;
        private ApplicationPermission applicationPermissionOne;
        private Group testGroupTwo;
        private Topic testTopicTwo;
        private Application applicationTwo;
        private ApplicationPermission applicationPermissionTwo;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            User justin = userRepository.save(new User("jjones@test.test"));

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            testGroup = groupRepository.save(new Group("TestGroup"));
            testTopic = topicRepository.save(new Topic("TestTopic", TopicKind.B, testGroup));
            applicationOne = applicationRepository.save(new Application("ApplicationOne", testGroup));
            applicationPermissionOne = applicationPermissionRepository.save(new ApplicationPermission(applicationOne, testTopic, true, true));
            GroupUser membership = new GroupUser(testGroup, justin);
            membership.setTopicAdmin(true);
            groupUserRepository.save(membership);

            testGroupTwo = groupRepository.save(new Group("TestGroup1"));
            testTopicTwo = topicRepository.save(new Topic("TestTopic1", TopicKind.C, testGroupTwo));
            applicationTwo = applicationRepository.save(new Application("ApplicationTwo", testGroupTwo));
            applicationPermissionTwo = applicationPermissionRepository.save(new ApplicationPermission(applicationTwo, testTopicTwo, false, true));
        }

        void loginAsTopicAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of()
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void canDeleteApplicationPermission() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> primaryOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(primaryOptional.isPresent());
            SimpleGroupDTO primaryGroup = primaryOptional.get();

            User justin = userRepository.findByEmail("jjones@test.test").get();
            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create topic
            response = createTopic("Topic123", TopicKind.C, primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
            assertTrue(topicOptional.isPresent());
            assertEquals("Topic123", topicOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            Map<String, Boolean> payload = Map.of("read", true, "write", false);

            // create application permission
            request = HttpRequest.POST("/application_permissions/" + topicOptional.get().getId(), payload)
                    .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
            response = blockingClient.exchange(request, AccessPermissionDTO.class);
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> accessPermissionDTOOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(accessPermissionDTOOptional.isPresent());

            loginAsTopicAdmin();

            // application permissions delete
            request = HttpRequest.DELETE("/application_permissions/"+accessPermissionDTOOptional.get().getId());
            response = blockingClient.exchange(request, HashMap.class);
            assertEquals(NO_CONTENT, response.getStatus());
        }

        @Test
        public void canListPermissionsOfGroupsUserIsAMemberOf() {
            HttpRequest<?> request;

            loginAsTopicAdmin();

            request = HttpRequest.GET("/application_permissions/application/"+applicationOne.getId());
            Page page = blockingClient.retrieve(request,  Page.class);
            assertFalse(page.isEmpty());
            List content = page.getContent();
            assertNotNull(content);
            assertEquals(1, content.size());
            Map m = (Map) content.get(0);
            assertEquals(applicationPermissionOne.getId().intValue(), m.get("id"));
        }
    }

    @Nested
    class WhenAsNonAdmin {

        private ApplicationPermission applicationPermissionOne;
        private Application publicApplication;
        private Topic publicTopic;
        private Application privateApplication;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com"));
            userRepository.save(new User("jjones@test.test"));

            Group group = groupRepository.save(new Group("TestGroup"));
            Topic topic = topicRepository.save(new Topic("TestTopic", TopicKind.B, group));
            Application application = applicationRepository.save(new Application("ApplicationOne", group));
            applicationPermissionOne = applicationPermissionRepository.save(new ApplicationPermission(application, topic, true, true));

            Group publicGroup = groupRepository.save(new Group("TestGroup1", "group desc", true));
            publicTopic = topicRepository.save(new Topic("TestTopic1", TopicKind.C, "topic description", true, publicGroup));
            publicApplication = applicationRepository.save(new Application("ApplicationTwo", publicGroup, "topic description", true));
            ApplicationPermission publicApplicationPermission = applicationPermissionRepository.save(new ApplicationPermission(publicApplication, publicTopic, true, true));

            ReadPartition cat = readPartitionRepository.save(new ReadPartition(publicApplicationPermission, "cat"));
            WritePartition dog = writePartitionRepository.save(new WritePartition(publicApplicationPermission, "dog"));
            publicApplicationPermission.setReadPartitions(Set.of(cat));
            publicApplicationPermission.setWritePartitions(Set.of(dog));
            applicationPermissionRepository.save(publicApplicationPermission);

            privateApplication = applicationRepository.save(new Application("ApplicationThree", group, "application description", false));
            applicationPermissionRepository.save(new ApplicationPermission(privateApplication, publicTopic, true, true));
        }

        void loginAsNonAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of()
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void canViewPublicApplicationPermissions() {
            loginAsNonAdmin();

            HttpRequest<?> request;
            Page page;

            // public permissions with public application
            request = HttpRequest.GET("/application_permissions/application/"+publicApplication.getId());
            page = blockingClient.retrieve(request, Page.class);
            assertFalse(page.isEmpty());
            assertEquals(1, page.getContent().size());
            assertTrue(page.getContent().stream().allMatch(o -> {
                Map map1 = (Map) o;
                String topic = (String) map1.get("topicName");
                String application = (String) map1.get("applicationName");
                return topic.contentEquals("TestTopic1") && application.contentEquals("ApplicationTwo");
            }));

            // public permissions call with private application - should not yield results
            request = HttpRequest.GET("/application_permissions/application/"+privateApplication.getId());
            page = blockingClient.retrieve(request, Page.class);
            assertTrue(page.isEmpty());

            // public permissions call with public topic - should yield one permission (the one with public application association)
            request = HttpRequest.GET("/application_permissions/topic/"+publicTopic.getId());
            page = blockingClient.retrieve(request, Page.class);
            assertFalse(page.isEmpty());
            assertEquals(1, page.getContent().size());
            assertTrue(page.getContent().stream().allMatch(o -> {
                Map map1 = (Map) o;
                String topic = (String) map1.get("topicName");
                String application = (String) map1.get("applicationName");
                return topic.contentEquals("TestTopic1") && application.contentEquals("ApplicationTwo");
            }));
        }

        @Test
        public void cannotViewPublicApplicationPermissionPartitions() {
            loginAsNonAdmin();

            HttpRequest<?> request;
            Page page;

            // public permissions with public application
            request = HttpRequest.GET("/application_permissions/application/"+publicApplication.getId());
            page = blockingClient.retrieve(request, Page.class);
            assertFalse(page.isEmpty());
            List<Map> content = page.getContent();
            assertEquals(1, content.size());

            // read/write Partitions are not included payload for public permissions
            assertNull(content.get(0).get("readPartitions"));
            assertNull(content.get(0).get("writePartitions"));
        }

        @Test
        public void cannotDeleteApplicationPermission() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            loginAsNonAdmin();

            // application permissions delete
            request = HttpRequest.DELETE("/application_permissions/"+applicationPermissionOne.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }
    }

    private HttpResponse<?> createGroup(String groupName) {
        SimpleGroupDTO group = new SimpleGroupDTO();
        group.setName(groupName);
        HttpRequest<?> request = HttpRequest.POST("/groups/save", group);
        return blockingClient.exchange(request, SimpleGroupDTO.class);
    }

    private HttpResponse<?> createApplication(String applicationName, Long groupId) {
        ApplicationDTO applicationDTO = new ApplicationDTO();
        applicationDTO.setName(applicationName);
        applicationDTO.setGroup(groupId);

        HttpRequest<?> request = HttpRequest.POST("/applications/save", applicationDTO);
        return blockingClient.exchange(request, ApplicationDTO.class);
    }

    private HttpResponse<?> createTopic(String topicName, TopicKind topicKind, Long groupId) {
        TopicDTO topicDTO = new TopicDTO();
        topicDTO.setName(topicName);
        topicDTO.setGroup(groupId);
        topicDTO.setKind(topicKind);

        HttpRequest<?> request = HttpRequest.POST("/topics/save", topicDTO);
        return blockingClient.exchange(request, TopicDTO.class);
    }

    private HttpResponse<?> createApplicationPermission(String applicationGrantToken, Long topicId, boolean read, boolean write) {
        Map<String, Boolean> payload = Map.of("read", read, "write", write);
        HttpRequest<?> request = HttpRequest.POST("/application_permissions/" + topicId, payload)
                .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
        return blockingClient.exchange(request, AccessPermissionDTO.class);
    }
}

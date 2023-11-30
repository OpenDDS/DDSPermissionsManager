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
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.CreateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.ActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.CreateActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrantRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.CreateGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.UpdateGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.*;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.CreateGrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.GrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUser;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;


@Property(name = "spec.name", value = "ApplicationGrantApiTest")
@MicronautTest
public class ApplicationGrantApiTest {

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
    ApplicationGrantRepository applicationGrantRepository;

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

    @Inject
    MockSecretSignature mockJwtSecret;

    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
    }

    @Requires(property = "spec.name", value = "ApplicationGrantApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "ApplicationGrantApiTest")
    @Replaces(SecurityService.class)
    @Singleton
    static class MockSecurityService extends SecurityServiceReplacement {
    }

    @Nested
    class WhenAsAdmin {

        private Group testGroup;
        private Application applicationOne;
        private Group publicGroup;
        private Application privateApplication;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            testGroup = groupRepository.save(new Group("TestGroup"));
            applicationOne = applicationRepository.save(new Application("ApplicationOne", testGroup));

            publicGroup = groupRepository.save(new Group("PublicGroup", "Description", true));
            privateApplication = applicationRepository.save(new Application("ApplicationOne", publicGroup, "app desc", false));
        }

        @Test
        public void grantIsDeletedPostApplicationDeletion() {

            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = createGrantDuration("30s Duration", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("30s Duration", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grant
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional = response.getBody(GrantDTO.class);
            assertTrue(grantOptional.isPresent());

            request = HttpRequest.DELETE("/applications/"+applicationOptional.get().getId(), Map.of());
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);

            assertTrue(applicationGrantRepository.findById(grantOptional.get().getId()).isEmpty());
        }

        @Test
        public void canUpdateApplicationGrant() {
            HttpResponse<?> response;
            HttpRequest<?> request;
            Long applicationOneId = applicationOne.getId();
            Long applicationGroupId = applicationOne.getPermissionsGroup().getId();

            // create grant duration
            response = createGrantDuration("30s Duration", applicationGroupId, "WEEKS");
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("30s Duration", durationOptional.get().getName());
            assertEquals("WEEKS", durationOptional.get().getDurationMetadata());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOneId);
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grant
            response = createApplicationGrant(applicationGrantToken, applicationGroupId, "MyGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional = response.getBody(GrantDTO.class);
            assertTrue(grantOptional.isPresent());
            assertEquals(30000, grantOptional.get().getDurationInMilliseconds());
            assertEquals("WEEKS", grantOptional.get().getDurationMetadata());

            // update the grant
            UpdateGrantDTO updateGrantDTO = new UpdateGrantDTO();
            updateGrantDTO.setName("UpdatedGrant");
            updateGrantDTO.setGrantDurationId(durationOptional.get().getId());
            request = HttpRequest.PUT("/application_grants/" + grantOptional.get().getId(), updateGrantDTO);
            response = blockingClient.exchange(request, GrantDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GrantDTO> updatedGrantOptional = response.getBody(GrantDTO.class);
            assertTrue(updatedGrantOptional.isPresent());
            assertEquals("UpdatedGrant", updatedGrantOptional.get().getName());

            // delete the grant
            request = HttpRequest.DELETE("/application_grants/" + updatedGrantOptional.get().getId());
            response = blockingClient.exchange(request);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatus());

            request = HttpRequest.GET("/application_grants");
            HashMap responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertNull(content);
        }

        @Test
        public void attemptToAssociateApplicationWithInvalidApplicationJwtToken() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = createGrantDuration("Duration1", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // invalid jwt token
            Map<String, Object> stringObjectMap = jwtClaimsSetGenerator.generateClaimsSet(Map.of("myKey", "myVal"), 5000);
            Optional<String> s = jwtTokenGenerator.generateToken(stringObjectMap);

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(s.get(), primaryGroup.getId(), "GrantName", durationOptional.get().getId());
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
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

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

            // create grant duration
            response = createGrantDuration("Duration1", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // change signature secret
            mockJwtSecret.setSecret("thisIsASecretThatIsInvalidAndIsMoreThan256BitsLong");

            // create app permission
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "GrantName", durationOptional.get().getId());
            });
            assertEquals(FORBIDDEN, exception.getStatus());
        }

        @Test
        public void canDeleteApplicationGrant() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = createGrantDuration("Duration1", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application permission
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "TempGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());

            // application permissions delete
            request = HttpRequest.DELETE("/application_grants/"+grantDTOOptional.get().getId());
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
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

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

            // create grant duration
            response = createGrantDuration("Duration1", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // create app permission
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<AccessPermissionDTO> permissionOptional = response.getBody(AccessPermissionDTO.class);
            assertTrue(permissionOptional.isPresent());

            // second create attempt
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> body = exception.getResponse().getBody(List.class);
            assertTrue(body.isPresent());
            List<Map> list = body.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_GRANT_ALREADY_EXISTS.equals(group.get("code"))));
        }

        @Test
        public void canViewAllApplicationGrants() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create grant duration
            response = createGrantDuration("Duration1", applicationOne.getPermissionsGroup().getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationnOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationnOptional.isPresent());
            assertEquals("Duration1", durationnOptional.get().getName());

            response = createApplicationGrant(applicationGrantToken, applicationOne.getPermissionsGroup().getId(),
                    "Grant2", durationnOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional = response.getBody(GrantDTO.class);
            assertTrue(grantOptional.isPresent());

            request = HttpRequest.GET("/application_grants");
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);
            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(1, content.size());
        }

        @Test
        public void canViewAllApplicationGrantsOrderedByName() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create grant duration
            response = createGrantDuration("Duration1", applicationOne.getPermissionsGroup().getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            response = createApplicationGrant(applicationGrantToken, applicationOne.getPermissionsGroup().getId(),
                    "AGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> aGrantOptional = response.getBody(GrantDTO.class);
            assertTrue(aGrantOptional.isPresent());

            response = createApplicationGrant(applicationGrantToken, applicationOne.getPermissionsGroup().getId(),
                    "BGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> bGrantOptional = response.getBody(GrantDTO.class);
            assertTrue(bGrantOptional.isPresent());

            request = HttpRequest.GET("/application_grants");
            HashMap<String, Object> responseMap = blockingClient.retrieve(request, HashMap.class);
            assertNotNull(responseMap);

            List<Map> content = (List<Map>) responseMap.get("content");
            assertEquals(2, content.size());

            List<String> grantNames = content.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(grantNames.stream().sorted().collect(Collectors.toList()), grantNames);
        }

        @Test
        public void attemptToCreateWithInvalidData() {
            HttpResponse response;
            HttpRequest request;

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            response = createGrantDuration("Duration1", applicationOne.getPermissionsGroup().getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // null name
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(applicationGrantToken, applicationOne.getPermissionsGroup().getId(),
                        null, durationOptional.get().getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> body = exception.getResponse().getBody(List.class);
            assertTrue(body.isPresent());
            List<Map> list = body.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_GRANT_NAME_CANNOT_BE_BLANK_OR_NULL.equals(group.get("code"))));

            // name less than three characters
            exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(applicationGrantToken, applicationOne.getPermissionsGroup().getId(),
                        "    ", durationOptional.get().getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            body = exception.getResponse().getBody(List.class);
            assertTrue(body.isPresent());
            list = body.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_GRANT_NAME_CANNOT_BE_BLANK_OR_NULL.equals(group.get("code"))));

            // requires group
            exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(applicationGrantToken, null, "MyGrant", durationOptional.get().getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            body = exception.getResponse().getBody(List.class);
            assertTrue(body.isPresent());
            list = body.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_GRANT_REQUIRES_GROUP_ASSOCIATION.equals(group.get("code"))));

            // requires duration
            exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(applicationGrantToken, applicationOne.getPermissionsGroup().getId(), "MyGrant", null);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            body = exception.getResponse().getBody(List.class);
            assertTrue(body.isPresent());
            list = body.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_GRANT_REQUIRES_DURATION_ASSOCIATION.equals(group.get("code"))));
        }

        @Test
        public void cannotCreateWithADurationFromAnotherGroup() {
            HttpResponse response;
            HttpRequest request;

            Long publicGroupId = privateApplication.getPermissionsGroup().getId();

            // create duration under group publicGroup
            response = createGrantDuration("DurationB", publicGroupId);
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());

            // attempt to create grant under testGroup with publicGroup's duration - fail
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            String finalApplicationGrantToken = applicationGrantToken;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(finalApplicationGrantToken, testGroup.getId(), "CreateGrantAttempt-Fail", durationOptional.get().getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> body = exception.getResponse().getBody(List.class);
            assertTrue(body.isPresent());
            List<Map> list = body.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.APPLICATION_GRANT_GRANT_DURATION_DOES_NOT_BELONG_TO_SAME_GROUP.equals(group.get("code"))));

            // attempt to create a grant under publicGroup with group B duration - pass
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOne.getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            applicationGrantToken = optional.get();

            response = createApplicationGrant(applicationGrantToken, publicGroupId, "CreateGrantAttempt-Pass", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());
        }
    }

    @Nested
    class WhenAsATopicAdmin {

        private Group testGroup;
        private Application applicationOne;
        private Group testGroupTwo;
        private Application applicationTwo;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            User justin = userRepository.save(new User("jjones@test.test"));

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            testGroup = groupRepository.save(new Group("TestGroup"));
            applicationOne = applicationRepository.save(new Application("ApplicationOne", testGroup));
            GroupUser membership = new GroupUser(testGroup, justin);
            membership.setTopicAdmin(true);
            groupUserRepository.save(membership);

            testGroupTwo = groupRepository.save(new Group("TestGroup1"));
            applicationTwo = applicationRepository.save(new Application("ApplicationTwo", testGroupTwo));
        }

        void loginAsTopicAdmin() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of()
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        // can create, update,
        @Test
        public void canCreateApplicationGrant() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

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

            // create grant duration
            response = createGrantDuration("Duration1", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            loginAsTopicAdmin();

            // create application grant
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "TopicAdmin-created Grant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());
        }

        @Test
        public void canUpdateApplicationGrant() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

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

            // create grant duration
            response = createGrantDuration("30s Duration", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("30s Duration", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grant
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional = response.getBody(GrantDTO.class);
            assertTrue(grantOptional.isPresent());

            loginAsTopicAdmin();

            // update the grant
            UpdateGrantDTO updateGrantDTO = new UpdateGrantDTO();
            updateGrantDTO.setName("UpdatedGrant");
            updateGrantDTO.setGrantDurationId(durationOptional.get().getId());
            request = HttpRequest.PUT("/application_grants/" + grantOptional.get().getId(), updateGrantDTO);
            response = blockingClient.exchange(request, GrantDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GrantDTO> updatedGrantOptional = response.getBody(GrantDTO.class);
            assertTrue(updatedGrantOptional.isPresent());
            assertEquals("UpdatedGrant", updatedGrantOptional.get().getName());
        }

        @Test
        public void canDeleteApplicationGrant() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

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

            // create grant duration
            response = createGrantDuration("Duration1", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application permission
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "TempGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());

            loginAsTopicAdmin();

            // application permissions delete
            request = HttpRequest.DELETE("/application_grants/"+grantDTOOptional.get().getId());
            response = blockingClient.exchange(request, HashMap.class);
            assertEquals(NO_CONTENT, response.getStatus());
        }
    }

    @Nested
    class WhenAsAnApplicationAdmin {

        private Group testGroup;
        private Application applicationOne;
        private Group testGroupTwo;
        private Application applicationTwo;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            User justin = userRepository.save(new User("jjones@test.test"));

            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            testGroup = groupRepository.save(new Group("TestGroup"));
            applicationOne = applicationRepository.save(new Application("ApplicationOne", testGroup));
            GroupUser membership = new GroupUser(testGroup, justin);
            membership.setApplicationAdmin(true);
            groupUserRepository.save(membership);

            testGroupTwo = groupRepository.save(new Group("TestGroup1"));
            applicationTwo = applicationRepository.save(new Application("ApplicationTwo", testGroupTwo));
        }

        void login() {
            mockSecurityService.setServerAuthentication(new ServerAuthentication(
                    "jjones@test.test",
                    Collections.emptyList(),
                    Map.of()
            ));
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void canDeleteApplicationGrant() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            response = createGroup("SecondaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> secondaryGroupOptional = response.getBody(Group.class);
            assertTrue(secondaryGroupOptional.isPresent());
            Group secondaryGroup = secondaryGroupOptional.get();

            User justin = userRepository.findByEmail("jjones@test.test").get();
            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(secondaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setApplicationAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create application
            response = createApplication("Application123", secondaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = createGrantDuration("Duration1", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("Duration1", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grant
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "TempGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());

            login();

            // application grant delete
            request = HttpRequest.DELETE("/application_grants/"+grantDTOOptional.get().getId());
            response = blockingClient.exchange(request, HashMap.class);
            assertEquals(NO_CONTENT, response.getStatus());
        }
    }

    @Nested
    class WhenAsNonAdminGroupMember {

        private Application publicApplication;
        private Application privateApplication;

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com", true));
            userRepository.save(new User("jjones@test.test"));

            Group group = groupRepository.save(new Group("TestGroup"));
            Application application = applicationRepository.save(new Application("ApplicationOne", group));

            Group publicGroup = groupRepository.save(new Group("TestGroup1", "group desc", true));
            publicApplication = applicationRepository.save(new Application("ApplicationTwo", publicGroup, "topic description", true));

            privateApplication = applicationRepository.save(new Application("ApplicationThree", group, "application description", false));
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
        public void canViewApplicationGrants() {
            mockSecurityService.postConstruct(); 
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

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

            // create grant duration
            response = createGrantDuration("30s Duration", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("30s Duration", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grants
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional = response.getBody(GrantDTO.class);
            assertTrue(grantOptional.isPresent());

            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MySecondGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional1 = response.getBody(GrantDTO.class);
            assertTrue(grantOptional1.isPresent());

            loginAsNonAdmin();

            Page page;

            // public permissions with public application
            request = HttpRequest.GET("/application_grants");
            page = blockingClient.retrieve(request, Page.class);
            assertFalse(page.isEmpty());
            assertEquals(2, page.getContent().size());
            assertTrue(page.getContent().stream().anyMatch(o -> {
                Map map1 = (Map) o;
                String name = (String) map1.get("name");
                String application = (String) map1.get("applicationName");
                return name.contentEquals("MySecondGrant") && application.contentEquals("Application123");
            }));
        }

        @Test
        public void cannotCreateApplicationGrant() {
            mockSecurityService.postConstruct(); 
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            User justin = userRepository.findByEmail("jjones@test.test").get();
            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = createGrantDuration("30s Duration", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("30s Duration", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            loginAsNonAdmin();

            // attempt to create application grant
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        public void cannotUpdateApplicationGrant() {
            mockSecurityService.postConstruct(); 
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            User justin = userRepository.findByEmail("jjones@test.test").get();
            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = createGrantDuration("30s Duration", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("30s Duration", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grants
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional = response.getBody(GrantDTO.class);
            assertTrue(grantOptional.isPresent());

            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MySecondGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional1 = response.getBody(GrantDTO.class);
            assertTrue(grantOptional1.isPresent());

            loginAsNonAdmin();

            UpdateGrantDTO updateGrantDTO = new UpdateGrantDTO();
            updateGrantDTO.setName("UpdatedGrant");
            updateGrantDTO.setGrantDurationId(durationOptional.get().getId());

            request = HttpRequest.PUT("/application_grants/" + grantOptional.get().getId(), updateGrantDTO);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, GrantDTO.class);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        public void cannotDeleteApplicationGrant() {
            mockSecurityService.postConstruct(); 
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpResponse<?> response;
            HttpRequest<?> request;

            // create groups
            response = createGroup("PrimaryGroup");
            assertEquals(OK, response.getStatus());
            Optional<Group> primaryOptional = response.getBody(Group.class);
            assertTrue(primaryOptional.isPresent());
            Group primaryGroup = primaryOptional.get();

            User justin = userRepository.findByEmail("jjones@test.test").get();
            // add user to group as an application admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create application
            response = createApplication("Application123", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
            assertTrue(applicationOptional.isPresent());
            assertEquals("Application123", applicationOptional.get().getName());

            // create grant duration
            response = createGrantDuration("30s Duration", primaryGroup.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> durationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(durationOptional.isPresent());
            assertEquals("30s Duration", durationOptional.get().getName());

            // generate grant token for application
            request = HttpRequest.GET("/applications/generate_grant_token/" + applicationOptional.get().getId());
            response = blockingClient.exchange(request, String.class);
            assertEquals(OK, response.getStatus());
            Optional<String> optional = response.getBody(String.class);
            assertTrue(optional.isPresent());
            String applicationGrantToken = optional.get();

            // create application grants
            response = createApplicationGrant(applicationGrantToken, primaryGroup.getId(), "MyGrant", durationOptional.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantOptional = response.getBody(GrantDTO.class);
            assertTrue(grantOptional.isPresent());

            loginAsNonAdmin();

            // application permissions delete
            request = HttpRequest.DELETE("/application_grants/"+grantOptional.get().getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }
    }

    private HttpResponse<?> createGroup(String groupName) {
        Group group = new Group(groupName);
        HttpRequest<?> request = HttpRequest.POST("/groups/save", group);
        return blockingClient.exchange(request, Group.class);
    }

    private HttpResponse<?> createApplication(String applicationName, Long groupId) {
        ApplicationDTO applicationDTO = new ApplicationDTO();
        applicationDTO.setName(applicationName);
        applicationDTO.setGroup(groupId);

        HttpRequest<?> request = HttpRequest.POST("/applications/save", applicationDTO);
        return blockingClient.exchange(request, ApplicationDTO.class);
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
}

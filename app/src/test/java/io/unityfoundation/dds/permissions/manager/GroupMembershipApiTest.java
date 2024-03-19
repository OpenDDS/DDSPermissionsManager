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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.ServerAuthentication;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.exception.DPMErrorResponse;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserResponseDTO;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "GroupMembershipApiTest")
@Property(name = "micronaut.security.filter.enabled", value = StringUtils.FALSE)
@MicronautTest
public class GroupMembershipApiTest {
    private BlockingHttpClient blockingClient;

    @Inject
    MockSecurityService mockSecurityService;

    @Inject
    MockDPMIntrospectionController mockDPMIntrospectionController;

    @Inject
    GroupRepository groupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    DbCleanup dbCleanup;

    @Inject
    @Client("/api")
    HttpClient client;

    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
    }

    @Requires(property = "spec.name", value = "GroupMembershipApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "GroupMembershipApiTest")
    @Replaces(SecurityService.class)
    @Singleton
    static class MockSecurityService extends SecurityServiceReplacement {
    }

    @Nested
    class WhenAsAdmin {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User( "montesm@test.test.com", true));
            userRepository.save(new User("jjones@test.test"));
            mockSecurityService.postConstruct();
            mockDPMIntrospectionController.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        // create
        @Test
        public void canCreate() {
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
        }

        @Test
        public void canCreateWithSameEmailDifferentGroup() {
            // group creation
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            // perform test
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            dto.setPermissionsGroup(secondaryGroup.getId());
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
        }

        @Test
        public void cannotCreateWithInvalidEmailFormat() {
            // group creation
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            // perform test
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("pparker@.test.test");
            request = HttpRequest.POST("/group_membership", dto);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.INVALID_EMAIL_FORMAT.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void cannotCreateWithSameEmailAndGroupAsExisting() {
            // group creation
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.POST("/group_membership", dto);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.GROUP_MEMBERSHIP_ALREADY_EXISTS.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void cannotCreateIfGroupSpecifiedDoesNotExist() {
            GroupUserDTO dto = new GroupUserDTO();
            dto.setEmail("bob.builder@test.test.com");
            dto.setPermissionsGroup(100l);
            dto.setTopicAdmin(true);
            HttpRequest<?> request = HttpRequest.POST("/group_membership", dto);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
            });
            assertEquals(NOT_FOUND, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.GROUP_NOT_FOUND.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void cannotCreateWithoutGroupSpecified() {
            User bob = userRepository.save(new User( "bob.builder@test.test.com"));

            GroupUserDTO dto = new GroupUserDTO();
            dto.setEmail(bob.getEmail());
            dto.setTopicAdmin(true);
            HttpRequest<?> request = HttpRequest.POST("/group_membership", dto);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.GROUP_MEMBERSHIP_REQUIRES_GROUP_ASSOCIATION.equals(dpmErrorResponse.getCode())));
        }

        // list
        @Test
        public void canSeeAllMemberships() {
            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/group_membership");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(2, page.getContent().size());
        }

        @Test
        public void membershipsOrderedByEmail() {
            // Groups intentionally created in an order that
            // is not consistent with their sort order
            String firstGroupCreatedName = "ZZZ Group"; // bob and angie and zack
            String secondGroupCreatedName = "MMM Group"; // bob and angie and jill
            String thirdGroupCreatedName = "AAA Group"; // jack and angie

            String jill = "jill@test.test";
            String jack = "jack@test.test";
            String angie = "angie@test.test";
            String bob = "bob@test.test";
            String zack = "zack@test.test";

            createGroupAndMemberships(firstGroupCreatedName, bob, angie, zack);
            createGroupAndMemberships(secondGroupCreatedName, bob, jill, angie);
            createGroupAndMemberships(thirdGroupCreatedName, jack, angie);

            HttpRequest<?> request = HttpRequest.GET("/group_membership");
            HttpResponse<?> response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            List content = page.getContent();
            assertEquals(8, content.size());

            assertExpectedEmailAndGroupName(content, 0, angie, thirdGroupCreatedName);
            assertExpectedEmailAndGroupName(content, 1, angie, secondGroupCreatedName);
            assertExpectedEmailAndGroupName(content, 2, angie, firstGroupCreatedName);

            assertExpectedEmailAndGroupName(content, 3, bob, secondGroupCreatedName);
            assertExpectedEmailAndGroupName(content, 4, bob, firstGroupCreatedName);

            assertExpectedEmailAndGroupName(content, 5, jack, thirdGroupCreatedName);

            assertExpectedEmailAndGroupName(content, 6, jill, secondGroupCreatedName);

            assertExpectedEmailAndGroupName(content, 7, zack, firstGroupCreatedName);
        }

        private void createMemberships(SimpleGroupDTO group, String... emails) {
            for(String email: emails) {
                GroupUserDTO dto = new GroupUserDTO();
                dto.setPermissionsGroup(group.getId());
                dto.setEmail(email);
                HttpRequest<?> request = HttpRequest.POST("/group_membership", dto);
                HttpResponse<?> response = blockingClient.exchange(request);
                assertEquals(OK, response.getStatus());
            }
        }

        private SimpleGroupDTO createGroup(String groupName) {
            SimpleGroupDTO group = new SimpleGroupDTO();
            group.setName(groupName);
            HttpRequest<?> request = HttpRequest.POST("/groups/save", group);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            return response.getBody(SimpleGroupDTO.class).get();
        }

        private void createGroupAndMemberships(String groupName, String... emails) {
            SimpleGroupDTO group = createGroup(groupName);
            createMemberships(group, emails);
        }

        void assertExpectedEmailAndGroupName(List content, int index, String expectedEmail, String expectedGroup) {
            Map membership = (Map) content.get(index);

            String email = (String) membership.get("permissionsUserEmail");
            assertEquals(expectedEmail, email);

            String groupName = (String) membership.get("permissionsGroupName");
            assertEquals(expectedGroup, groupName);
        }

        @Test
        public void canSeeAllMembershipsFilteredByGroupNameCaseInsensitive() {
            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/group_membership?filter=secondary");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(1, page.getContent().size());
        }

        @Test
        public void canSeeAllMembershipsFilteredByUserEmailCaseInsensitive() {
            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/group_membership?filter=Bob.Builder@test");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(1, page.getContent().size());
        }

        @Test
        public void canSeeAllMembershipsFilteredByGroup() {
            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GroupUserDTO> bobOptional = response.getBody(GroupUserDTO.class);
            assertTrue(bobOptional.isPresent());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request, GroupUserDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GroupUserDTO> robertOptional = response.getBody(GroupUserDTO.class);
            assertTrue(robertOptional.isPresent());

            // access both members via groups they belong to
            request = HttpRequest.GET("/group_membership?group="+primaryGroup.getId());
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(1, page.getContent().size());
            Map map = (Map) page.getContent().get(0);
            assertEquals(map.get("id"), bobOptional.get().getId().intValue());

            request = HttpRequest.GET("/group_membership?group="+secondaryGroup.getId());
            response = blockingClient.exchange(request, Page.class);
            page = response.getBody(Page.class).get();
            assertEquals(1, page.getContent().size());
            map = (Map) page.getContent().get(0);
            assertEquals(map.get("id"), robertOptional.get().getId().intValue());

            // support filter
            request = HttpRequest.GET("/group_membership?filter=Bob.Builder@Test&group="+primaryGroup.getId());
            response = blockingClient.exchange(request, Page.class);
            page = response.getBody(Page.class).get();
            assertEquals(1, page.getContent().size());
            map = (Map) page.getContent().get(0);
            assertEquals(map.get("id"), bobOptional.get().getId().intValue());

            // negative (no results)
            request = HttpRequest.GET("/group_membership?group="+-1);
            response = blockingClient.exchange(request, Page.class);
            page = response.getBody(Page.class).get();
            assertEquals(0, page.getContent().size());
        }

        // update
        @Test
        public void canUpdate() {
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            dto.setId(groupUser.getId());
            dto.setGroupAdmin(true);
            dto.setTopicAdmin(true);
            request = HttpRequest.PUT("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            groupUser = response.getBody(GroupUserResponseDTO.class).get();
            assertTrue(groupUser.isGroupAdmin());
            assertTrue(groupUser.isTopicAdmin());
        }

        @Test
        public void cannotAttemptToSaveNewWithUpdateEndpoint() {
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());

            request = HttpRequest.PUT("/group_membership", dto);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.GROUP_MEMBERSHIP_CANNOT_CREATE_WITH_UPDATE.equals(dpmErrorResponse.getCode())));
        }

        // delete
        @Test
        public void canDelete() {
            // group creation
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            // delete
            request = HttpRequest.DELETE("/group_membership", Map.of("id", groupUser.getId()));
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
        }

        @Test
        public void shouldBeConsideredValid() {
            HttpRequest request = HttpRequest.GET("/token_info");
            HttpResponse response = blockingClient.exchange(request, Map.class);
            assertEquals(OK, response.getStatus());
            Optional<Map> mapOptional = response.getBody(Map.class);
            assertTrue(mapOptional.isPresent());
            Map map = mapOptional.get();
            assertTrue((Boolean) map.get("isAdmin"));
        }
    }

    @Nested
    class WhenAsAGroupAdmin {

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
        }

        @Test
        public void canCreate() {
            mockSecurityService.postConstruct();

            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as a group admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setGroupAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create member with above group
            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(GroupUserResponseDTO.class).isPresent());
        }

        @Test
        public void cannotCreateIfNotMemberOfGroup() {
            mockSecurityService.postConstruct();

            // save group without members
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            loginAsNonAdmin();

            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
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

        @Test
        public void cannotCreateIfNonGroupAdminMemberOfGroup() {
            mockSecurityService.postConstruct();

            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as a group admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // create application with above group
            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
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

        @Test
        public void canUpdate() {
            mockSecurityService.postConstruct();

            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            GroupUserResponseDTO primaryGroupResponse = response.getBody(GroupUserResponseDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroupResponse.getId());
            dto.setEmail(justin.getEmail());
            dto.setGroupAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            loginAsNonAdmin();

            dto.setId(groupUser.getId());
            dto.setGroupAdmin(true);
            dto.setTopicAdmin(true);
            request = HttpRequest.PUT("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            groupUser = response.getBody(GroupUserResponseDTO.class).get();
            assertTrue(groupUser.isGroupAdmin());
            assertTrue(groupUser.isTopicAdmin());
        }

        @Test
        public void cannotUpdateIfNonGroupAdminMemberOfGroup() {
            mockSecurityService.postConstruct();

            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            loginAsNonAdmin();

            dto.setId(groupUser.getId());
            dto.setGroupAdmin(true);
            dto.setTopicAdmin(true);
            request = HttpRequest.PUT("/group_membership", dto);
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

        @Test
        public void canDeleteFromGroup() {
            mockSecurityService.postConstruct();
            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as a group admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setGroupAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create member with above group
            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(GroupUserResponseDTO.class).isPresent());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            loginAsNonAdmin();

            // delete
            request = HttpRequest.DELETE("/group_membership", Map.of("id", groupUser.getId()));
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
        }

        @Test
        public void deleteUserIfNonAdminAndNoGroupMemberships() {
            mockSecurityService.postConstruct();
            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as a group admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setGroupAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create member with above group
            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(GroupUserResponseDTO.class).isPresent());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            request = HttpRequest.GET("/group_membership/user_exists/"+groupUser.getPermissionsUser());
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // delete
            request = HttpRequest.DELETE("/group_membership", Map.of("id", groupUser.getId()));
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/group_membership/user_exists/"+groupUser.getPermissionsUser());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(NOT_FOUND, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.USER_NOT_FOUND.equals(dpmErrorResponse.getCode())));
        }

        @Test
        public void cannotDeleteIfNotMemberOfGroup() {
            mockSecurityService.postConstruct();

            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            // create member with above group
            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(GroupUserResponseDTO.class).isPresent());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            loginAsNonAdmin();

            // delete
            request = HttpRequest.DELETE("/group_membership", Map.of("id", groupUser.getId()));
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

        @Test
        public void cannotDeleteIfNonGroupAdminMemberOfGroup() {
            mockSecurityService.postConstruct();

            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            User justin = userRepository.findByEmail("jjones@test.test").get();

            // add user to group as a group admin
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(justin.getEmail());
            dto.setTopicAdmin(true);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // create member with above group
            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(GroupUserResponseDTO.class).isPresent());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            loginAsNonAdmin();

            // delete
            request = HttpRequest.DELETE("/group_membership", Map.of("id", groupUser.getId()));
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
            mockDPMIntrospectionController.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void canSeeMembershipsOfGroupsIAmAMemberOf() {
            mockSecurityService.postConstruct();

            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/group_membership");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(2, page.getContent().size());
        }

        @Test
        public void cannotSeeAllMemberships() {
            mockSecurityService.postConstruct();

            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/group_membership");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(2, page.getContent().size());
        }

        @Test
        public void canSeeCommonGroupMembershipsFilteredByGroupNameCaseInsensitive() {
            mockSecurityService.postConstruct();

            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/group_membership?filter=secondary");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(2, page.getContent().size());
        }

        @Test
        public void canSeeCommonGroupMembershipsFilteredByUserEmailCaseInsensitive() {
            mockSecurityService.postConstruct();

            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/group_membership?filter=The.GeneralContractor@Test");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(1, page.getContent().size());
        }

        @Test
        public void canSeeCommonGroupMembershipsFilteredByGroup() {
            mockSecurityService.postConstruct();

            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            // group search
            request = HttpRequest.GET("/group_membership?group="+secondaryGroup.getId());
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(2, page.getContent().size());

            // filter param support
            request = HttpRequest.GET("/group_membership?filter=The.GeneralContractor@test&group="+secondaryGroup.getId());
            response = blockingClient.exchange(request, Page.class);
            page = response.getBody(Page.class).get();
            assertEquals(1, page.getContent().size());

            // Negative cases
            request = HttpRequest.GET("/group_membership?group="+primaryGroup.getId());
            response = blockingClient.exchange(request, Page.class);
            page = response.getBody(Page.class).get();
            assertTrue(page.getContent().isEmpty());

            request = HttpRequest.GET("/group_membership?filter=bob.builder@unityfoundation&group="+primaryGroup.getId());
            response = blockingClient.exchange(request, Page.class);
            page = response.getBody(Page.class).get();
            assertTrue(page.getContent().isEmpty());
        }


        @Test
        public void cannotSeeOtherGroupMembershipsFilteredByGroupName() {
            mockSecurityService.postConstruct();

            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/group_membership?filter=Primary");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(0, page.getContent().size());
        }

        @Test
        public void cannotSeeOtherGroupMembershipsFilteredByUserEmail() {
            mockSecurityService.postConstruct();

            // first group and member
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/group_membership?filter=builder@unityfoundation");
            response = blockingClient.exchange(request, Page.class);
            Page page = response.getBody(Page.class).get();
            assertEquals(0, page.getContent().size());
        }

        @Test
        public void shouldBeConsideredValid() {
            mockSecurityService.postConstruct();
            mockDPMIntrospectionController.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest request;
            HttpResponse response;

            // other group and member
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = (SimpleGroupDTO) response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            loginAsNonAdmin();

            request = HttpRequest.GET("/token_info");
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
        }

        @Test
        public void shouldHaveGroupPermissionsUpdatedIfAdminUpdatesMembership() {
            mockSecurityService.postConstruct();
            mockDPMIntrospectionController.setAuthentication(mockSecurityService.getAuthentication().get());

            HttpRequest request;
            HttpResponse response;

            // group
            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = (SimpleGroupDTO) response.getBody(SimpleGroupDTO.class).get();

            // memberships
            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(secondaryGroup.getId());
            dto1.setEmail("robert.the.generalcontractor@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // add non-admin test user
            dto1.setEmail("jjones@test.test");
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GroupUserResponseDTO> membershipOptional = response.getBody(GroupUserResponseDTO.class);
            assertTrue(membershipOptional.isPresent());
            GroupUserResponseDTO membership = membershipOptional.get();
            assertFalse(membership.isGroupAdmin());
            assertFalse(membership.isApplicationAdmin());
            assertFalse(membership.isTopicAdmin());

            loginAsNonAdmin();

            // check value is initialized and what is retrieved is value in db
            request = HttpRequest.GET("/token_info");
            response = blockingClient.exchange(request, Map.class);
            assertEquals(OK, response.getStatus());
            Optional<Map> mapOptional = response.getBody(Map.class);
            assertTrue(mapOptional.isPresent());
            Map map = mapOptional.get();
            Long permissionsLastUpdated = (Long) map.get("permissionsLastUpdated");
            assertNotNull(permissionsLastUpdated);

            request = HttpRequest.GET("/token_info");
            response = blockingClient.exchange(request, Map.class);
            assertEquals(OK, response.getStatus());
            mapOptional = response.getBody(Map.class);
            assertTrue(mapOptional.isPresent());
            map = mapOptional.get();
            Long permissionsLastUpdatedDupRequest = (Long) map.get("permissionsLastUpdated");
            assertEquals(permissionsLastUpdated, permissionsLastUpdatedDupRequest);

            // assert permissions are indeed false from /group_membership/user_validity endpoint
            ArrayList<Map> permissionsByGroup = (ArrayList<Map>) map.get("permissionsByGroup");
            assertEquals(1, permissionsByGroup.size());
            Map userValidityPermissions = permissionsByGroup.get(0);
            assertFalse((Boolean) userValidityPermissions.get("isGroupAdmin"));
            assertFalse((Boolean) userValidityPermissions.get("isTopicAdmin"));
            assertFalse((Boolean) userValidityPermissions.get("isApplicationAdmin"));

            // update permissions
            mockSecurityService.postConstruct();
            dto1.setId(membership.getId());
            dto1.setGroupAdmin(true);
            dto1.setTopicAdmin(true);
            request = HttpRequest.PUT("/group_membership", dto1);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GroupUserResponseDTO> updatedMembershipOptional = response.getBody(GroupUserResponseDTO.class);
            assertTrue(updatedMembershipOptional.isPresent());
            GroupUserResponseDTO updatedMembership = updatedMembershipOptional.get();
            assertTrue(updatedMembership.isGroupAdmin());
            assertTrue(updatedMembership.isTopicAdmin());
            assertFalse(updatedMembership.isApplicationAdmin());

            // check permissions as affected user and respective updated permissions
            loginAsNonAdmin();

            request = HttpRequest.GET("/token_info");
            response = blockingClient.exchange(request, Map.class);
            assertEquals(OK, response.getStatus());
            mapOptional = response.getBody(Map.class);
            assertTrue(mapOptional.isPresent());
            map = mapOptional.get();
            Long permissionsLastUpdatedChanged = (Long) map.get("permissionsLastUpdated");
            assertNotNull(permissionsLastUpdated);
            assertNotEquals(permissionsLastUpdated, permissionsLastUpdatedChanged);

            permissionsByGroup = (ArrayList<Map>) map.get("permissionsByGroup");
            assertEquals(1, permissionsByGroup.size());
            userValidityPermissions = permissionsByGroup.get(0);
            assertTrue((Boolean) userValidityPermissions.get("isGroupAdmin"));
            assertTrue((Boolean) userValidityPermissions.get("isTopicAdmin"));
            assertFalse((Boolean) userValidityPermissions.get("isApplicationAdmin"));
        }

        @Test
        public void shouldHaveGroupPermissionsUpdatedIfAdminDeletesMembership() {
            mockSecurityService.postConstruct();

            HttpRequest request;
            HttpResponse response;

            // group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            request = HttpRequest.POST("/groups/save", primaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = (SimpleGroupDTO) response.getBody(SimpleGroupDTO.class).get();

            SimpleGroupDTO secondaryGroup = new SimpleGroupDTO();
            secondaryGroup.setName("SecondaryGroup");
            request = HttpRequest.POST("/groups/save", secondaryGroup);
            response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            secondaryGroup = (SimpleGroupDTO) response.getBody(SimpleGroupDTO.class).get();

            // memberships
            GroupUserDTO dto1 = new GroupUserDTO();
            dto1.setPermissionsGroup(primaryGroup.getId());
            dto1.setEmail("jjones@test.test");

            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            dto1.setPermissionsGroup(secondaryGroup.getId());
            request = HttpRequest.POST("/group_membership", dto1);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GroupUserResponseDTO> membershipOptional = response.getBody(GroupUserResponseDTO.class);
            assertTrue(membershipOptional.isPresent());
            GroupUserResponseDTO membership = membershipOptional.get();

            // check validity payload
            loginAsNonAdmin();

            request = HttpRequest.GET("/token_info");
            response = blockingClient.exchange(request, Map.class);
            assertEquals(OK, response.getStatus());
            Optional<Map> mapOptional = response.getBody(Map.class);
            assertTrue(mapOptional.isPresent());
            Map map = mapOptional.get();
            Long permissionsLastUpdated = (Long) map.get("permissionsLastUpdated");
            assertNotNull(permissionsLastUpdated);
            ArrayList<Map> permissionsByGroup = (ArrayList<Map>) map.get("permissionsByGroup");
            assertEquals(2, permissionsByGroup.size());

            // delete membership
            mockSecurityService.postConstruct();
            request = HttpRequest.DELETE("/group_membership", Map.of("id", membership.getId()));
            response = blockingClient.exchange(request);
            assertEquals(HttpStatus.OK, response.getStatus());

            // check permissions as affected user and respective timestamp
            loginAsNonAdmin();

            request = HttpRequest.GET("/token_info");
            response = blockingClient.exchange(request, Map.class);
            assertEquals(OK, response.getStatus());
            mapOptional = response.getBody(Map.class);
            assertTrue(mapOptional.isPresent());
            map = mapOptional.get();
            Long permissionsLastUpdatedChanged = (Long) map.get("permissionsLastUpdated");
            assertNotNull(permissionsLastUpdated);
            assertNotEquals(permissionsLastUpdated, permissionsLastUpdatedChanged);

            permissionsByGroup = (ArrayList<Map>) map.get("permissionsByGroup");
            assertEquals(1, permissionsByGroup.size());
        }

    }

    @Nested
    class WhenAsANonAdmin {

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
            mockDPMIntrospectionController.setAuthentication(mockSecurityService.getAuthentication().get());
        }

        @Test
        public void cannotCreate() {
            mockSecurityService.postConstruct();

            // save group without members
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            loginAsNonAdmin();

            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
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

        @Test
        public void cannotUpdate() {
            mockSecurityService.postConstruct();

            // save group without members
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            loginAsNonAdmin();

            dto.setId(groupUser.getId());
            dto.setGroupAdmin(true);
            dto.setTopicAdmin(true);
            request = HttpRequest.PUT("/group_membership", dto);
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

        @Test
        public void cannotDeleteFromGroup() {
            mockSecurityService.postConstruct();

            // create group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            // create member with above group
            GroupUserDTO dtoNewUser = new GroupUserDTO();
            dtoNewUser.setPermissionsGroup(primaryGroup.getId());
            dtoNewUser.setEmail("bob.builder@test.test");
            request = HttpRequest.POST("/group_membership", dtoNewUser);
            response = blockingClient.exchange(request, GroupUserResponseDTO.class);
            assertEquals(OK, response.getStatus());
            assertTrue(response.getBody(GroupUserResponseDTO.class).isPresent());
            GroupUserResponseDTO groupUser = response.getBody(GroupUserResponseDTO.class).get();

            loginAsNonAdmin();

            // delete
            request = HttpRequest.DELETE("/group_membership", Map.of("id", groupUser.getId()));
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

        @Test
        public void shouldBeConsideredInvalid() {
            loginAsNonAdmin();
            HttpRequest request = HttpRequest.GET("/token_info");
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(NOT_FOUND, exception.getStatus());
            Optional<DPMErrorResponse[]> listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            DPMErrorResponse[] dpmErrorResponses = listOptional.get();
            List<DPMErrorResponse> list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.USER_IS_NOT_VALID.equals(dpmErrorResponse.getCode())));
        }
    }
}

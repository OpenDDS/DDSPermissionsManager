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
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.user.AdminDTO;
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

@Property(name = "spec.name", value = "AdminApiTest")
@MicronautTest
public class AdminApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    @Client("/api")
    HttpClient client;

    @Inject
    UserRepository userRepository;

    @Inject
    MockSecurityService mockSecurityService;

    @Inject
    AuthenticationFetcherReplacement mockAuthenticationFetcher;

    @Inject
    DbCleanup dbCleanup;

    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
    }

    @Requires(property = "spec.name", value = "AdminApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "AdminApiTest")
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

        // create
        @Test
        void canAddAdmin(){
            AdminDTO justin = new AdminDTO("jjones@foobar.com");
            HttpRequest<?> request = HttpRequest.POST("/admins/save", justin);
            HttpResponse<?> response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());
        }

        @Test
        void canEscalateExistingMembersPrivilegeToAdmin(){
            // group
            SimpleGroupDTO primaryGroup = new SimpleGroupDTO();
            primaryGroup.setName("PrimaryGroup");
            HttpRequest<?> request = HttpRequest.POST("/groups/save", primaryGroup);
            HttpResponse<?> response = blockingClient.exchange(request, SimpleGroupDTO.class);
            assertEquals(OK, response.getStatus());
            primaryGroup = response.getBody(SimpleGroupDTO.class).get();

            String bobEmail = "bob.builder@test.test";

            // membership
            GroupUserDTO dto = new GroupUserDTO();
            dto.setPermissionsGroup(primaryGroup.getId());
            dto.setEmail(bobEmail);
            request = HttpRequest.POST("/group_membership", dto);
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());

            // escalate
            AdminDTO bobAdmin = new AdminDTO(bobEmail);
            request = HttpRequest.POST("/admins/save", bobAdmin);
            response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());

            // assert
            request = HttpRequest.GET("/admins");
            Page<Map> responsePage = blockingClient.retrieve(request, Page.class);
            List<Map> admins = responsePage.getContent();
            List<String> adminEmails = admins.stream().map(map -> (String) map.get("email")).collect(Collectors.toList());
            assertTrue(adminEmails.contains(bobEmail));
        }

        @Test
        public void userWithInvalidEmailFormatShallNotPersist() {
            HttpRequest<?> request = HttpRequest.POST("/admins/save", new AdminDTO("pparker@.test.test"));
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

            request = HttpRequest.POST("/admins/save", new AdminDTO("pparker@unityfoundation"));
            HttpRequest<?> finalRequest1 = request;
            exception = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            listOptional = exception.getResponse().getBody(DPMErrorResponse[].class);
            assertTrue(listOptional.isPresent());
            dpmErrorResponses = listOptional.get();
            list = List.of(dpmErrorResponses);
            assertTrue(list.stream().anyMatch(dpmErrorResponse -> ResponseStatusCodes.INVALID_EMAIL_FORMAT.equals(dpmErrorResponse.getCode())));
        }

        // update
        // list
        @Test
        public void shouldBeAbleToFilterByEmail() {

            AdminDTO homer = new AdminDTO("hsimpson@foobar.com");
            HttpRequest<?> request = HttpRequest.POST("/admins/save", homer);
            HttpResponse<?> response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());

            AdminDTO bart = new AdminDTO("bsimpson@foobar.com");
            request = HttpRequest.POST("/admins/save", bart);
            response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/admins?filter=hsimpson");
            Page<Map> responsePage = blockingClient.retrieve(request, Page.class);
            List<Map> admins = responsePage.getContent();
            assertEquals("hsimpson@foobar.com", admins.get(0).get("email"));
            assertEquals(1, admins.size());
        }

        @Test
        public void shouldSeeAdminsInAscendingOrderByDefault() {

            AdminDTO homer = new AdminDTO("hsimpson@foobar.com");
            HttpRequest<?> request = HttpRequest.POST("/admins/save", homer);
            HttpResponse<?> response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());

            AdminDTO bart = new AdminDTO("bsimpson@foobar.com");
            request = HttpRequest.POST("/admins/save", bart);
            response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/admins");
            Page<Map> responsePage = blockingClient.retrieve(request, Page.class);
            List<Map> admins = responsePage.getContent();
            List<String> adminEmails = admins.stream().map(map -> (String) map.get("email")).collect(Collectors.toList());
            assertEquals(adminEmails.stream().sorted().collect(Collectors.toList()), adminEmails);
        }

        @Test
        public void shouldRespectAdminEmailsInDescendingOrder() {

            AdminDTO bart = new AdminDTO("bsimpson@foobar.com");
            HttpRequest<?> request = HttpRequest.POST("/admins/save", bart);
            HttpResponse<?> response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());

            AdminDTO homer = new AdminDTO("hsimpson@foobar.com");
            request = HttpRequest.POST("/admins/save", homer);
            response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/admins?sort=email,desc");
            Page<Map> responsePage = blockingClient.retrieve(request, Page.class);
            List<Map> admins = responsePage.getContent();
            List<String> groupNames = admins.stream().flatMap(map -> Stream.of((String) map.get("email"))).collect(Collectors.toList());
            assertEquals(groupNames.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList()), groupNames);
        }

        // delete
        @Test
        void canRemoveAdmin(){
            AdminDTO justin = new AdminDTO("jjones@foobar.com");
            HttpRequest<?> request = HttpRequest.POST("/admins/save", justin);
            HttpResponse<?> response = blockingClient.exchange(request, AdminDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<AdminDTO> jjones = response.getBody(AdminDTO.class);
            assertTrue(jjones.isPresent());

            request = HttpRequest.PUT("/admins/remove_admin/"+jjones.get().getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(OK, response.getStatus());
            assertTrue(userRepository.findById(jjones.get().getId()).isEmpty());
        }
    }

    @Nested
    class WhenAsNonAdmin {

        @BeforeEach
        void setup() {
            dbCleanup.cleanup();
            userRepository.save(new User("montesm@test.test.com"));
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
        void cannotAddAdmin(){
            loginAsNonAdmin();
            AdminDTO justin = new AdminDTO("jjones@foobar.com");
            HttpRequest<?> request = HttpRequest.POST("/admins/save", justin);
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
            });
            assertEquals(UNAUTHORIZED, thrown.getStatus());
        }

        // create
        // update
        // list
        @Test
        void cannotListAdmins(){
            loginAsNonAdmin();
            HttpRequest<?>request = HttpRequest.GET("/admins");
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
            });
            assertEquals(UNAUTHORIZED, thrown.getStatus());
        }

        // delete
        @Test
        void cannotRemoveAdmins(){
            loginAsNonAdmin();
            HttpRequest<?>request = HttpRequest.POST("/admins/remove_admin/1", Map.of());
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request);
            });
            assertEquals(FORBIDDEN, thrown.getStatus());
        }
    }
}

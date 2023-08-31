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
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.dto.CreateExpirationPolicyDTO;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.dto.ExpirationPolicyDTO;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserResponseDTO;
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

@Property(name = "spec.name", value = "ExpirationPolicyApiTest")
@MicronautTest
public class ExpirationPolicyApiTest {

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

    @Requires(property = "spec.name", value = "ExpirationPolicyApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "ExpirationPolicyApiTest")
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
            CreateExpirationPolicyDTO abcDTO = new CreateExpirationPolicyDTO();
            abcDTO.setName("abc");

            HttpRequest<?> request = HttpRequest.POST("/expiration_policies", abcDTO);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request, ExpirationPolicyDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.EXPIRATION_POLICY_REQUIRES_GROUP_ASSOCIATION.equals(map.get("code"))));
        }

        @Test
        void canCreateWithGroupAssociation() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicy = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicy.isPresent());
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

            // create expiration policies
            CreateExpirationPolicyDTO expirationPolicyDTO = new CreateExpirationPolicyDTO();
            expirationPolicyDTO.setGroupId(theta.getId());

            request = HttpRequest.POST("/expiration_policies/", expirationPolicyDTO);

            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, ExpirationPolicyDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.EXPIRATION_POLICY_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));

            expirationPolicyDTO.setName("     ");
            request = HttpRequest.POST("/expiration_policies", expirationPolicyDTO);
            HttpRequest<?> finalRequest1 = request;
            HttpClientResponseException exception1 = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, ExpirationPolicyDTO.class);
            });
            assertEquals(BAD_REQUEST, exception1.getStatus());
            bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.EXPIRATION_POLICY_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));
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
                createExpirationPolicy("A", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.EXPIRATION_POLICY_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS.equals(map.get("code"))));
        }

        @Test
        public void createShouldTrimNameWhitespaces() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createExpirationPolicy("   Abc123  ", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicy = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicy.isPresent());
            assertEquals("Abc123", expirationPolicy.get().getName());
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

            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicyOptional.isPresent());
            ExpirationPolicyDTO abcExpirationPolicy = expirationPolicyOptional.get();

            // update attempt
            abcExpirationPolicy.setGroupId(zeta.getId());
            request = HttpRequest.PUT("/expiration_policies/"+abcExpirationPolicy.getId(), abcExpirationPolicy);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, thrown.getStatus());
            Optional<List> bodyOptional = thrown.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.EXPIRATION_POLICY_CANNOT_UPDATE_GROUP_ASSOCIATION.equals(map.get("code"))));
        }

        @Test
        public void canUpdateNameAndRefreshDate() {
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create expiration policies
            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicyOptional.isPresent());
            ExpirationPolicyDTO savedExpirationPolicy = expirationPolicyOptional.get();
            assertEquals("Abc123", savedExpirationPolicy.getName());

            // with different name
            savedExpirationPolicy.setName("NewName123");
            Instant newDateTime = Instant.ofEpochMilli(1675899232);
            savedExpirationPolicy.setRefreshDate(newDateTime);
            request = HttpRequest.PUT("/expiration_policies/"+savedExpirationPolicy.getId(), savedExpirationPolicy);
            response = blockingClient.exchange(request, ExpirationPolicyDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> updatedExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(updatedExpirationPolicyOptional.isPresent());
            ExpirationPolicyDTO updatedExpirationPolicy = updatedExpirationPolicyOptional.get();
            assertEquals("NewName123", updatedExpirationPolicy.getName());
            assertEquals(newDateTime, updatedExpirationPolicy.getRefreshDate());
        }

        @Test
        public void cannotCreateExpirationPolicyWithSameNameInGroup() {
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create expiration policies
            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicyOptional.isPresent());
            ExpirationPolicyDTO savedExpirationPolicy = expirationPolicyOptional.get();
            assertEquals("Abc123", savedExpirationPolicy.getName());

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createExpirationPolicy("Abc123", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.EXPIRATION_POLICY_ALREADY_EXISTS.equals(group.get("code"))));
        }

        //show
        @Test
        void canShowExpirationPolicyAssociatedToAGroup(){
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicyOptional.isPresent());
            ExpirationPolicyDTO xyzExpirationPolicy = expirationPolicyOptional.get();

            // show expiration policy
            request = HttpRequest.GET("/expiration_policies/"+xyzExpirationPolicy.getId());
            response = blockingClient.exchange(request, ExpirationPolicyDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicyShowResponse = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicyShowResponse.isPresent());
            assertNotNull(expirationPolicyShowResponse.get().getId());
            assertNotNull(expirationPolicyShowResponse.get().getGroupId());
            assertNotNull(expirationPolicyShowResponse.get().getGroupName());
            assertNotNull(expirationPolicyShowResponse.get().getRefreshDate());
        }

        // list all expiration policies from all groups
        @Test
        void canListAllExpirationPoliciesAndExpirationPoliciesWithSameNameCanExistSitewide(){
            // Group - Expiration Policiess
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", yellow.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Xyz789", green.getId());
            assertEquals(OK, response.getStatus());

            // site-wide test
            response = createExpirationPolicy("Xyz789", yellow.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/expiration_policies");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(3, expirationPolicyPage.get().getContent().size());
        }

        @Test
        void canListAllExpirationPoliciesWithFilter(){
            // Group - Expiration Policies
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            // support case-insensitive
            request = HttpRequest.GET("/expiration_policies?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/expiration_policies?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
        }

        @Test
        void canLisExpirationPoliciesWithGroupId(){
            // Group - Expiration Policies
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> abcOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(abcOptional.isPresent());
            ExpirationPolicyDTO abcExpirationPolicy = abcOptional.get();

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzOptional.isPresent());
            ExpirationPolicyDTO xyzExpirationPolicy = xyzOptional.get();

            // can list both expiration policies
            request = HttpRequest.GET("/expiration_policies?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            Map map = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals(map.get("id"), xyzExpirationPolicy.getId().intValue());

            request = HttpRequest.GET("/expiration_policies?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            map = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcExpirationPolicy.getId().intValue());

            // in addition to group, support filter param
            request = HttpRequest.GET("/expiration_policies?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            map = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcExpirationPolicy.getId().intValue());
        }

        @Test
        void canListAllExpirationPoliciesNameInAscendingOrderByDefault(){
            // Group - Expiration Policies
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Def456", theta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/expiration_policies");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            List<Map> expirationPolicies = expirationPolicyPage.get().getContent();

            // expiration policy names sorted
            List<String> expirationPolicyNames = expirationPolicies.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(expirationPolicyNames.stream().sorted().collect(Collectors.toList()), expirationPolicyNames);

            // group names should be sorted by expiration policy
            List<String> defExpirationPolicies = expirationPolicies.stream().filter(map -> {
                String expirationPolicyName = (String) map.get("name");
                return expirationPolicyName.equals("Def456");
            }).flatMap(map -> Stream.of((String) map.get("groupName"))).collect(Collectors.toList());
            assertEquals(defExpirationPolicies.stream().sorted().collect(Collectors.toList()), defExpirationPolicies);
        }

        @Test
        void canListAllExpirationPoliciesNameInDescendingOrder(){
            // Group - Expiration Policies
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/expiration_policies?sort=name,desc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            List<Map> expirationPolicies = expirationPolicyPage.get().getContent();

            List<String> expirationPolicyNames = expirationPolicies.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(expirationPolicyNames.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList()), expirationPolicyNames);
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
        void canCreateExpirationPolicy(){
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

            // create expiration policy
            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
        }

        // delete
        @Test
        void canDeleteExpirationPolicy(){
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

            // create expiration policy
            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicy = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicy.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/expiration_policies/"+expirationPolicy.get().getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(NO_CONTENT, response.getStatus());
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
        void cannotCreateExpirationPolicy(){
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

            // create expiration policy
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createExpirationPolicy("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteExpirationPolicy(){
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

            // create expiration policy
            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> abcTopiSetcOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            ExpirationPolicyDTO abcExpirationPolicy = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            HttpRequest<?> request2 = HttpRequest.DELETE("/expiration_policies/"+abcExpirationPolicy.getId(), Map.of());
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
        void canShowExpirationPolicyWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Expiration Policies - Members
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

            // create expiration policies
            createExpirationPolicy("Abc123", zeta.getId());
            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzExpirationPolicyOptional.isPresent());
            ExpirationPolicyDTO xyzExpirationPolicy = xyzExpirationPolicyOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/expiration_policies/"+xyzExpirationPolicy.getId());
            response = blockingClient.exchange(request, ExpirationPolicyDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> expirationPolicyResponseOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(expirationPolicyResponseOptional.isPresent());
            assertEquals("Xyz789", expirationPolicyResponseOptional.get().getName());
            assertEquals("Theta", expirationPolicyResponseOptional.get().getGroupName());
        }

        @Test
        void cannotShowExpirationPolicyIfExpirationPolicyBelongsToAGroupIAmNotAMemberOf(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Expiration Policies - Members
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", omega.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> abcExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(abcExpirationPolicyOptional.isPresent());
            ExpirationPolicyDTO abcExpirationPolicy = abcExpirationPolicyOptional.get();

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzExpirationPolicyOptional.isPresent());
            ExpirationPolicyDTO xyzExpirationPolicy = xyzExpirationPolicyOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/expiration_policies/"+abcExpirationPolicy.getId());
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
        void canListAllExpirationPoliciesLimitedToMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Expiration Policies
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> abcExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(abcExpirationPolicyOptional.isPresent());

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzExpirationPolicyOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/expiration_policies");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            Map expectedExpirationPolicy = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedExpirationPolicy.get("name"));
        }

        @Test
        void canListExpirationPoliciesWithFilterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Expiration Policies
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

            // create expiration policies
            createExpirationPolicy("Abc123", zeta.getId());
            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzExpirationPolicyOptional.isPresent());

            loginAsNonAdmin();

            // support case-insensitive
            request = HttpRequest.GET("/expiration_policies?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            Map expectedExpirationPolicy = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedExpirationPolicy.get("name"));

            // Negative case
            request = HttpRequest.GET("/expiration_policies?filter=abc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(0, expirationPolicyPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/expiration_policies?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            expectedExpirationPolicy = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedExpirationPolicy.get("name"));
        }

        @Test
        void canListExpirationPoliciesWithGroupParameterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Expiration Policies
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

            // create expiration policies
            createExpirationPolicy("Abc123", zeta.getId());
            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzExpirationPolicyOptional.isPresent());

            loginAsNonAdmin();

            // group search
            request = HttpRequest.GET("/expiration_policies?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            Map expectedExpirationPolicy = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedExpirationPolicy.get("name"));

            // filter param support
            request = HttpRequest.GET("/expiration_policies?filter=xyz&group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(1, expirationPolicyPage.get().getContent().size());
            expectedExpirationPolicy = (Map) expirationPolicyPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedExpirationPolicy.get("name"));

            // Negative cases
            request = HttpRequest.GET("/expiration_policies?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(0, expirationPolicyPage.get().getContent().size());

            request = HttpRequest.GET("/expiration_policies?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            expirationPolicyPage = response.getBody(Page.class);
            assertTrue(expirationPolicyPage.isPresent());
            assertEquals(0, expirationPolicyPage.get().getContent().size());
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
        void cannotCreateExpirationPolicy(){
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

            // create expiration policies
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                createExpirationPolicy("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteExpirationPolicy(){
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> abcTopiSetcOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            ExpirationPolicyDTO abcExpirationPolicy = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/expiration_policies/"+abcExpirationPolicy.getId(), Map.of());
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
        void cannotShowExpirationPolicyWithAssociatedGroup(){
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzTopiSetcOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzTopiSetcOptional.isPresent());
            ExpirationPolicyDTO xyzExpirationPolicy = xyzTopiSetcOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/expiration_policies/"+xyzExpirationPolicy.getId());
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
        void cannotListAllExpirationPolicies(){
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzExpirationPolicyOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/expiration_policies");
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        void cannotShowAExpirationPolicyWithGroupAssociation(){
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

            // create expiration policies
            response = createExpirationPolicy("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = createExpirationPolicy("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<ExpirationPolicyDTO> xyzExpirationPolicyOptional = response.getBody(ExpirationPolicyDTO.class);
            assertTrue(xyzExpirationPolicyOptional.isPresent());
            ExpirationPolicyDTO xyzExpirationPolicy = xyzExpirationPolicyOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/expiration_policies/"+xyzExpirationPolicy.getId());
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


    private HttpResponse<?> addGroupMembership(Long groupId, String email, boolean isAdmin) {
        GroupUserDTO dto = new GroupUserDTO();
        dto.setPermissionsGroup(groupId);
        dto.setEmail(email);
        dto.setTopicAdmin(isAdmin);

        HttpRequest<?>  request = HttpRequest.POST("/group_membership", dto);
        return blockingClient.exchange(request, GroupUserResponseDTO.class);
    }

    private HttpResponse<?> createExpirationPolicy(String name, Long groupId) {
        CreateExpirationPolicyDTO abcDTO = new CreateExpirationPolicyDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);
        abcDTO.setRefreshDate(Instant.now());

        HttpRequest<?> request = HttpRequest.POST("/expiration_policies", abcDTO);
        return blockingClient.exchange(request, ExpirationPolicyDTO.class);
    }
}
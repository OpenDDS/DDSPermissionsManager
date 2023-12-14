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
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.CreateGrantDurationDTO;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "GrantDurationApiTest")
@MicronautTest
public class GrantDurationApiTest {

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

    @Requires(property = "spec.name", value = "GrantDurationApiTest")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "GrantDurationApiTest")
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
            CreateGrantDurationDTO abcDTO = new CreateGrantDurationDTO();
            abcDTO.setName("abc");

            HttpRequest<?> request = HttpRequest.POST("/grant_durations", abcDTO);
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(request, GrantDurationDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_REQUIRES_GROUP_ASSOCIATION.equals(map.get("code"))));
        }

        @Test
        void canCreateWithGroupAssociation() {
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDuration = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDuration.isPresent());
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

            // create grant durations
            CreateGrantDurationDTO grantDurationDTO = new CreateGrantDurationDTO();
            grantDurationDTO.setGroupId(theta.getId());

            request = HttpRequest.POST("/grant_durations/", grantDurationDTO);

            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, GrantDurationDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));

            grantDurationDTO.setName("     ");
            request = HttpRequest.POST("/grant_durations", grantDurationDTO);
            HttpRequest<?> finalRequest1 = request;
            HttpClientResponseException exception1 = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, GrantDurationDTO.class);
            });
            assertEquals(BAD_REQUEST, exception1.getStatus());
            bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_NAME_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));
        }

        @Test
        public void cannotCreateWithWithAValueLessThanZeroOrNull() {
            HttpResponse<?> response;
            HttpRequest<?> request;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create grant durations
            CreateGrantDurationDTO grantDurationDTO = new CreateGrantDurationDTO();
            grantDurationDTO.setGroupId(theta.getId());
            grantDurationDTO.setName("MyDuration");

            request = HttpRequest.POST("/grant_durations/", grantDurationDTO);

            // null case
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest, GrantDurationDTO.class);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_DURATION_CANNOT_BE_BLANK_OR_NULL.equals(map.get("code"))));

            // negative case
            grantDurationDTO.setDurationInMilliseconds(-2000L);
            request = HttpRequest.POST("/grant_durations", grantDurationDTO);
            HttpRequest<?> finalRequest1 = request;
            HttpClientResponseException exception1 = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest1, GrantDurationDTO.class);
            });
            assertEquals(BAD_REQUEST, exception1.getStatus());
            Optional<List> negativeOptional = exception1.getResponse().getBody(List.class);
            assertTrue(negativeOptional.isPresent());
            List<Map> negativeList = negativeOptional.get();
            assertTrue(negativeList.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_DURATION_CANNOT_BE_A_NEGATIVE_VALUE.equals(map.get("code"))));
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
                entityUtil.createGrantDuration("A", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_NAME_CANNOT_BE_LESS_THAN_THREE_CHARACTERS.equals(map.get("code"))));
        }

        @Test
        public void createShouldTrimNameWhitespaces() {
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGrantDuration("   Abc123  ", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDuration = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDuration.isPresent());
            assertEquals("Abc123", grantDuration.get().getName());
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

            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationOptional.isPresent());
            GrantDurationDTO abcGrantDuration = grantDurationOptional.get();

            // update attempt
            abcGrantDuration.setGroupId(zeta.getId());
            request = HttpRequest.PUT("/grant_durations/"+abcGrantDuration.getId(), abcGrantDuration);
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, thrown.getStatus());
            Optional<List> bodyOptional = thrown.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_CANNOT_UPDATE_GROUP_ASSOCIATION.equals(map.get("code"))));
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", theta.getId(), "MONTHS");
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationOptional.isPresent());
            GrantDurationDTO savedGrantDuration = grantDurationOptional.get();
            assertEquals("Abc123", savedGrantDuration.getName());
            assertEquals("MONTHS", savedGrantDuration.getDurationMetadata());


            // with different name, dates, duration metadata
            savedGrantDuration.setName("NewName123");

            Long updateDuration = 5000L;
            savedGrantDuration.setDurationInMilliseconds(updateDuration);

            savedGrantDuration.setDurationMetadata("WEEKS");

            request = HttpRequest.PUT("/grant_durations/"+savedGrantDuration.getId(), savedGrantDuration);
            response = blockingClient.exchange(request, GrantDurationDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> updatedGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(updatedGrantDurationOptional.isPresent());
            GrantDurationDTO updatedGrantDuration = updatedGrantDurationOptional.get();
            assertEquals("NewName123", updatedGrantDuration.getName());
            assertEquals(updateDuration, updatedGrantDuration.getDurationInMilliseconds());
            assertEquals("WEEKS", updatedGrantDuration.getDurationMetadata());

            // just duration
            updateDuration = 7000L;
            savedGrantDuration.setDurationInMilliseconds(updateDuration);

            request = HttpRequest.PUT("/grant_durations/"+savedGrantDuration.getId(), savedGrantDuration);
            response = blockingClient.exchange(request, GrantDurationDTO.class);
            assertEquals(OK, response.getStatus());
            updatedGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(updatedGrantDurationOptional.isPresent());
            updatedGrantDuration = updatedGrantDurationOptional.get();
            assertEquals(updateDuration, updatedGrantDuration.getDurationInMilliseconds());
        }

        @Test
        public void cannotCreateGrantDurationWithSameNameInGroup() {
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationOptional.isPresent());
            GrantDurationDTO savedGrantDuration = grantDurationOptional.get();
            assertEquals("Abc123", savedGrantDuration.getName());

            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createGrantDuration("Abc123", theta.getId());
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(group -> ResponseStatusCodes.GRANT_DURATION_ALREADY_EXISTS.equals(group.get("code"))));
        }

        //show
        @Test
        void canShowGrantDurationAssociatedToAGroup(){
            HttpRequest<?> request;
            HttpResponse<?> response;

            response = entityUtil.createGroup("Theta");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO theta = thetaOptional.get();

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationOptional.isPresent());
            GrantDurationDTO xyzGrantDuration = grantDurationOptional.get();

            // show grant duration
            request = HttpRequest.GET("/grant_durations/"+xyzGrantDuration.getId());
            response = blockingClient.exchange(request, GrantDurationDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDurationShowResponse = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationShowResponse.isPresent());
            assertNotNull(grantDurationShowResponse.get().getId());
            assertNotNull(grantDurationShowResponse.get().getGroupId());
            assertNotNull(grantDurationShowResponse.get().getGroupName());
            assertNotNull(grantDurationShowResponse.get().getDurationInMilliseconds());
        }

        // list all grant durations from all groups
        @Test
        void canListAllGrantDurationsAndGrantDurationsWithSameNameCanExistSitewide(){
            // Group - Grant Durations
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", yellow.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Xyz789", green.getId());
            assertEquals(OK, response.getStatus());

            // site-wide test
            response = entityUtil.createGrantDuration("Xyz789", yellow.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/grant_durations");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(3, grantDurationPage.get().getContent().size());
        }

        @Test
        void canListAllGrantDurationsWithFilter(){
            // Group - Grant Durations
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            // support case-insensitive
            request = HttpRequest.GET("/grant_durations?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/grant_durations?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
        }

        @Test
        void canLisGrantDurationsWithGroupId(){
            // Group - Grant Durations
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> abcOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(abcOptional.isPresent());
            GrantDurationDTO abcGrantDuration = abcOptional.get();

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzOptional.isPresent());
            GrantDurationDTO xyzGrantDuration = xyzOptional.get();

            // can list both grant durations
            request = HttpRequest.GET("/grant_durations?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            Map map = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals(map.get("id"), xyzGrantDuration.getId().intValue());

            request = HttpRequest.GET("/grant_durations?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            map = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcGrantDuration.getId().intValue());

            // in addition to group, support filter param
            request = HttpRequest.GET("/grant_durations?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            map = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals(map.get("id"), abcGrantDuration.getId().intValue());
        }

        @Test
        void canListAllGrantDurationsNameInAscendingOrderByDefault(){
            // Group - Grant Durations
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Def456", theta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/grant_durations");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            List<Map> grantDurations = grantDurationPage.get().getContent();

            // grant duration names sorted
            List<String> grantDurationNames = grantDurations.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(grantDurationNames.stream().sorted().collect(Collectors.toList()), grantDurationNames);

            // group names should be sorted by grant duration
            List<String> defGrantDurations = grantDurations.stream().filter(map -> {
                String grantDurationName = (String) map.get("name");
                return grantDurationName.equals("Def456");
            }).flatMap(map -> Stream.of((String) map.get("groupName"))).collect(Collectors.toList());
            assertEquals(defGrantDurations.stream().sorted().collect(Collectors.toList()), defGrantDurations);
        }

        @Test
        void canListAllGrantDurationsNameInDescendingOrder(){
            // Group - Grant Durations
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Def456", zeta.getId());
            assertEquals(OK, response.getStatus());

            request = HttpRequest.GET("/grant_durations?sort=name,desc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            List<Map> grantDurations = grantDurationPage.get().getContent();

            List<String> grantDurationNames = grantDurations.stream()
                    .flatMap(map -> Stream.of((String) map.get("name")))
                    .collect(Collectors.toList());
            assertEquals(grantDurationNames.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList()), grantDurationNames);
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
        void canCreateGrantDuration(){
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

            // create grant duration
            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
        }

        // delete
        @Test
        void canDeleteGrantDuration(){
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

            // create grant duration
            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDuration = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDuration.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/grant_durations/"+grantDuration.get().getId(), Map.of());
            response = blockingClient.exchange(request);
            assertEquals(NO_CONTENT, response.getStatus());
        }

        @Test
        void cannotDeleteGrantDurationIfAssociatedToAGrant(){
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

            // create application permission
            response = entityUtil.createApplicationGrant(applicationGrantToken, theta.getId(), "TempGrant", grantDuration.get().getId());
            assertEquals(CREATED, response.getStatus());
            Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
            assertTrue(grantDTOOptional.isPresent());

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/grant_durations/"+grantDuration.get().getId(), Map.of());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(BAD_REQUEST, exception.getStatus());
            Optional<List> bodyOptional = exception.getResponse().getBody(List.class);
            assertTrue(bodyOptional.isPresent());
            List<Map> list = bodyOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.GRANT_DURATION_HAS_ONE_OR_MORE_GRANT_ASSOCIATIONS.equals(map.get("code"))));
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
        void cannotCreateGrantDuration(){
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

            // create grant duration
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createGrantDuration("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteGrantDuration(){
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

            // create grant duration
            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> abcTopiSetcOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            GrantDurationDTO abcGrantDuration = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            HttpRequest<?> request2 = HttpRequest.DELETE("/grant_durations/"+abcGrantDuration.getId(), Map.of());
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
        void canShowGrantDurationWithAssociatedGroup(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Grant Durations - Members
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

            // create grant durations
            entityUtil.createGrantDuration("Abc123", zeta.getId());
            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzGrantDurationOptional.isPresent());
            GrantDurationDTO xyzGrantDuration = xyzGrantDurationOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/grant_durations/"+xyzGrantDuration.getId());
            response = blockingClient.exchange(request, GrantDurationDTO.class);
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> grantDurationResponseOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(grantDurationResponseOptional.isPresent());
            assertEquals("Xyz789", grantDurationResponseOptional.get().getName());
            assertEquals("Theta", grantDurationResponseOptional.get().getGroupName());
        }

        @Test
        void cannotShowGrantDurationIfGrantDurationBelongsToAGroupIAmNotAMemberOf(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Grant Durations - Members
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", omega.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> abcGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(abcGrantDurationOptional.isPresent());
            GrantDurationDTO abcGrantDuration = abcGrantDurationOptional.get();

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzGrantDurationOptional.isPresent());
            GrantDurationDTO xyzGrantDuration = xyzGrantDurationOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/grant_durations/"+abcGrantDuration.getId());
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
        void canListAllGrantDurationsLimitedToMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Grant Durations
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> abcGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(abcGrantDurationOptional.isPresent());

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzGrantDurationOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/grant_durations");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            Map expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedGrantDuration.get("name"));
        }

        @Test
        void canListGrantDurationsWithFilterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Grant Durations
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

            // create grant durations
            entityUtil.createGrantDuration("Abc123", zeta.getId());
            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzGrantDurationOptional.isPresent());

            loginAsNonAdmin();

            // support case-insensitive
            request = HttpRequest.GET("/grant_durations?filter=xyz");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            Map expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedGrantDuration.get("name"));

            // Negative case
            request = HttpRequest.GET("/grant_durations?filter=abc");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(0, grantDurationPage.get().getContent().size());

            // group search
            request = HttpRequest.GET("/grant_durations?filter=heta");
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedGrantDuration.get("name"));
        }

        @Test
        void canListGrantDurationsWithGroupParameterLimitedToGroupMembership(){
            mockSecurityService.postConstruct();
            mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());

            // Group - Grant Durations
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

            // create grant durations
            entityUtil.createGrantDuration("Abc123", zeta.getId());
            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzGrantDurationOptional.isPresent());

            loginAsNonAdmin();

            // group search
            request = HttpRequest.GET("/grant_durations?group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            Optional<Page> grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            Map expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedGrantDuration.get("name"));

            // filter param support
            request = HttpRequest.GET("/grant_durations?filter=xyz&group="+theta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(1, grantDurationPage.get().getContent().size());
            expectedGrantDuration = (Map) grantDurationPage.get().getContent().get(0);
            assertEquals("Xyz789", expectedGrantDuration.get("name"));

            // Negative cases
            request = HttpRequest.GET("/grant_durations?group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(0, grantDurationPage.get().getContent().size());

            request = HttpRequest.GET("/grant_durations?filter=abc&group="+zeta.getId());
            response = blockingClient.exchange(request, Page.class);
            assertEquals(OK, response.getStatus());
            grantDurationPage = response.getBody(Page.class);
            assertTrue(grantDurationPage.isPresent());
            assertEquals(0, grantDurationPage.get().getContent().size());
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
        void cannotCreateGrantDuration(){
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

            // create grant durations
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                entityUtil.createGrantDuration("Abc123", theta.getId());
            });
            assertEquals(UNAUTHORIZED,exception.getStatus());
            Optional<List> listOptional = exception.getResponse().getBody(List.class);
            assertTrue(listOptional.isPresent());
            List<Map> list = listOptional.get();
            assertTrue(list.stream().anyMatch(map -> ResponseStatusCodes.UNAUTHORIZED.equals(map.get("code"))));
        }

        // delete
        @Test
        void cannotDeleteGrantDuration(){
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> abcTopiSetcOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(abcTopiSetcOptional.isPresent());
            GrantDurationDTO abcGrantDuration = abcTopiSetcOptional.get();

            loginAsNonAdmin();

            // delete attempt
            request = HttpRequest.DELETE("/grant_durations/"+abcGrantDuration.getId(), Map.of());
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
        void cannotShowGrantDurationWithAssociatedGroup(){
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzTopiSetcOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzTopiSetcOptional.isPresent());
            GrantDurationDTO xyzGrantDuration = xyzTopiSetcOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/grant_durations/"+xyzGrantDuration.getId());
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
        void cannotListAllGrantDurations(){
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzGrantDurationOptional.isPresent());

            loginAsNonAdmin();

            request = HttpRequest.GET("/grant_durations");
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }

        @Test
        void cannotShowAGrantDurationWithGroupAssociation(){
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

            // create grant durations
            response = entityUtil.createGrantDuration("Abc123", zeta.getId());
            assertEquals(OK, response.getStatus());

            response = entityUtil.createGrantDuration("Xyz789", theta.getId());
            assertEquals(OK, response.getStatus());
            Optional<GrantDurationDTO> xyzGrantDurationOptional = response.getBody(GrantDurationDTO.class);
            assertTrue(xyzGrantDurationOptional.isPresent());
            GrantDurationDTO xyzGrantDuration = xyzGrantDurationOptional.get();

            loginAsNonAdmin();

            request = HttpRequest.GET("/grant_durations/"+xyzGrantDuration.getId());
            HttpRequest<?> finalRequest = request;
            HttpClientResponseException exception = assertThrowsExactly(HttpClientResponseException.class, () -> {
                blockingClient.exchange(finalRequest);
            });
            assertEquals(UNAUTHORIZED, exception.getStatus());
        }
    }
}
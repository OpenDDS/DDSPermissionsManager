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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.unityfoundation.dds.permissions.manager.model.DPMEntity;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermission;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionRepository;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUser;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.http.HttpStatus.OK;
import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "UniversalSearchApiTest")
@MicronautTest
class UniversalSearchApiTest {

    private BlockingHttpClient blockingClient;

    @Inject
    GroupRepository groupRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    ApplicationPermissionRepository applicationPermissionRepository;

    @Inject
    GroupUserRepository groupUserRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    TopicRepository topicRepository;

    @Inject
    DbCleanup dbCleanup;

    @Inject
    @Client("/api")
    HttpClient client;

    @Requires(property = "spec.name", value = "UniversalSearchApiTest")
    @Singleton
    static class MockAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {
        @Override
        public Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
            return Publishers.just(Authentication.build("montesm@test.test.com"));
        }
    }

    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
        dbCleanup.cleanup();

        userRepository.save(new User("montesm@test.test.com", true));
        User jjones = userRepository.save(new User("jjones@test.test"));
        User eclair = userRepository.save(new User("eclair@test.test"));

        Group groupOne = groupRepository.save(new Group("GroupOne", "GroupOnex", true));
        Topic topicOne = topicRepository.save(new Topic("TopicOne", TopicKind.B, "TopicOne3", true, groupOne));
        Topic topicOne1 = topicRepository.save(new Topic("TopicOne1", TopicKind.C, "TopicOneY", true, groupOne));
        Application applicationOne = applicationRepository.save(new Application("ApplicationOnez3", groupOne, "ApplicationOne", true));
        applicationPermissionRepository.save(new ApplicationPermission(applicationOne, topicOne, true, true));
        groupUserRepository.save(new GroupUser(groupOne, jjones));
        groupUserRepository.save(new GroupUser(groupOne, eclair));

        Group groupTwo = groupRepository.save(new Group("GroupTwo", "GroupTwo", true));
        Topic topicTwo = topicRepository.save(new Topic("TopicTwo", TopicKind.C, "TopicTwo", true, groupTwo));
        Application applicationTwo = applicationRepository.save(new Application("ApplicationTwo", groupTwo, "ApplicationTwo", true));
        Application applicationTwo1 = applicationRepository.save(new Application("ApplicationTwo1", groupTwo, "ApplicationTwo1", true));
        applicationPermissionRepository.save(new ApplicationPermission(applicationTwo, topicTwo, true, true));
        groupUserRepository.save(new GroupUser(groupTwo, eclair));
    }

    @Test
    void canQuery() {
        HttpRequest request;
        HttpResponse response;

        // string s = "o"
        request = HttpRequest.GET("/search?query=o");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> searchPage = response.getBody(Page.class);
        assertTrue(searchPage.isPresent());
        List<Map> searchResults = searchPage.get().getContent();
        assertFalse(searchResults.isEmpty());
        assertTrue(searchResults.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name()) ||
                    type.equals(DPMEntity.APPLICATION.name()) ||
                    type.equals(DPMEntity.TOPIC.name());
        }));

        // group
        request = HttpRequest.GET("/search?query=o&groups=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> groupsPage = response.getBody(Page.class);
        assertTrue(groupsPage.isPresent());
        List<Map> groups = groupsPage.get().getContent();
        assertFalse(groups.isEmpty());
        assertTrue(groups.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name());
        }));

        // topics and applications
        request = HttpRequest.GET("/search?query=o&topics=true&applications=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> appTopicsPage = response.getBody(Page.class);
        assertTrue(appTopicsPage.isPresent());
        List<Map> appsTopics = appTopicsPage.get().getContent();
        assertFalse(appsTopics.isEmpty());
        assertTrue(appsTopics.stream().noneMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name());
        }));

        // all
        request = HttpRequest.GET("/search?query=o&topics=true&applications=true&groups=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> allPage = response.getBody(Page.class);
        assertTrue(allPage.isPresent());
        List<Map> all = allPage.get().getContent();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name()) ||
                    type.equals(DPMEntity.APPLICATION.name()) ||
                    type.equals(DPMEntity.TOPIC.name());
        }));
    }



    @Test
    void canQueryDescription() {
        HttpRequest request;
        HttpResponse response;

        request = HttpRequest.GET("/search?query=y");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> searchPage = response.getBody(Page.class);
        assertTrue(searchPage.isPresent());
        List<Map> searchResults = searchPage.get().getContent();
        assertFalse(searchResults.isEmpty());
        assertTrue(searchResults.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.TOPIC.name()); // topicOne1
        }));

        // group
        request = HttpRequest.GET("/search?query=X&groups=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> groupsPage = response.getBody(Page.class);
        assertTrue(groupsPage.isPresent());
        List<Map> groups = groupsPage.get().getContent();
        assertFalse(groups.isEmpty());
        assertTrue(groups.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name());
        }));

        // topics and applications
        request = HttpRequest.GET("/search?query=3&topics=true&applications=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> appTopicsPage = response.getBody(Page.class);
        assertTrue(appTopicsPage.isPresent());
        List<Map> appsTopics = appTopicsPage.get().getContent();
        assertFalse(appsTopics.isEmpty());
        assertTrue(appsTopics.stream().noneMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name());
        }));

        // all
        request = HttpRequest.GET("/search?query=o&topics=true&applications=true&groups=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> allPage = response.getBody(Page.class);
        assertTrue(allPage.isPresent());
        List<Map> all = allPage.get().getContent();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name()) ||
                    type.equals(DPMEntity.APPLICATION.name()) ||
                    type.equals(DPMEntity.TOPIC.name());
        }));
    }

    @Test
    void emptyOrNullQueryShouldReturnAll() {
        HttpRequest request;
        HttpResponse response;

        // none
        request = HttpRequest.GET("/search");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> allPage = response.getBody(Page.class);
        assertTrue(allPage.isPresent());
        List<Map> all = allPage.get().getContent();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name()) ||
                    type.equals(DPMEntity.APPLICATION.name()) ||
                    type.equals(DPMEntity.TOPIC.name());
        }));

        // groups
        request = HttpRequest.GET("/search?groups=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> groupsPage = response.getBody(Page.class);
        assertTrue(groupsPage.isPresent());
        List<Map> groups = groupsPage.get().getContent();
        assertFalse(groups.isEmpty());
        assertTrue(groups.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name());
        }));

        // topics
        request = HttpRequest.GET("/search?topics=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> topicsPage = response.getBody(Page.class);
        assertTrue(topicsPage.isPresent());
        List<Map> topics = topicsPage.get().getContent();
        assertFalse(topics.isEmpty());
        assertTrue(topics.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.TOPIC.name());
        }));

        // applications
        request = HttpRequest.GET("/search?applications=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> appsPage = response.getBody(Page.class);
        assertTrue(appsPage.isPresent());
        List<Map> apps = appsPage.get().getContent();
        assertFalse(apps.isEmpty());
        assertTrue(apps.stream().allMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.APPLICATION.name());
        }));
    }

    @Test
    void canPairEntityQueries() {
        HttpRequest request;
        HttpResponse response;

        // groups and topics
        request = HttpRequest.GET("/search?query=o&groups=true&topics=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> groupsTopicsPage = response.getBody(Page.class);
        assertTrue(groupsTopicsPage.isPresent());
        List<Map> groupsTopics = groupsTopicsPage.get().getContent();
        assertFalse(groupsTopics.isEmpty());
        assertTrue(groupsTopics.stream().noneMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.APPLICATION.name());
        }));

        // topics and applications
        request = HttpRequest.GET("/search?query=o&topics=true&applications=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> appTopicsPage = response.getBody(Page.class);
        assertTrue(appTopicsPage.isPresent());
        List<Map> appsTopics = appTopicsPage.get().getContent();
        assertFalse(appsTopics.isEmpty());
        assertTrue(appsTopics.stream().noneMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.GROUP.name());
        }));

        // groups and applications
        request = HttpRequest.GET("/search?query=o&groups=true&applications=true");
        response = blockingClient.exchange(request, Page.class);
        assertEquals(OK, response.getStatus());
        Optional<Page> groupsAppsPage = response.getBody(Page.class);
        assertTrue(groupsAppsPage.isPresent());
        List<Map> groupsApps = groupsAppsPage.get().getContent();
        assertFalse(groupsApps.isEmpty());
        assertTrue(groupsApps.stream().noneMatch(map -> {
            String type = (String) map.get("type");
            return type.equals(DPMEntity.TOPIC.name());
        }));
    }
}

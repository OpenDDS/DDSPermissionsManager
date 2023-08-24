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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import io.unityfoundation.dds.permissions.manager.testing.util.DbCleanup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.http.HttpStatus.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "WebSocketTests")
@MicronautTest
public class WebSocketTests {
    private BlockingHttpClient blockingClient;

    @Inject
    @Client("/api")
    HttpClient client;

    @Inject
    @Client("ws://localhost")
    WebSocketClient webSocketClient;

    @Inject
    ApplicationContext ctx;

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    DbCleanup dbCleanup;

    @Inject
    UserRepository userRepository;

    @Inject
    MockSecurityService mockSecurityService;

    @Inject
    AuthenticationFetcherReplacement mockAuthenticationFetcher;

    @Requires(property = "spec.name", value = "WebSocketTests")
    @Singleton
    static class MockAuthenticationFetcher extends AuthenticationFetcherReplacement {
    }

    @Requires(property = "spec.name", value = "WebSocketTests")
    @Replaces(SecurityService.class)
    @Singleton
    static class MockSecurityService extends SecurityServiceReplacement {
    }


    @BeforeEach
    void setup() {
        blockingClient = client.toBlocking();
        dbCleanup.cleanup();
        userRepository.save(new User("montesm@test.test.com", true));
        mockSecurityService.postConstruct();
        mockAuthenticationFetcher.setAuthentication(mockSecurityService.getAuthentication().get());
    }

    private TestWebSocketClient createWebSocketClient(int port, String resource, Long id) {
        WebSocketClient webSocketClient = ctx.getBean(WebSocketClient.class);
        URI uri = UriBuilder.of("ws://localhost")
                .port(port)
                .path("ws")
                .path("{resource}")
                .path("{id}")
                .expand(CollectionUtils.mapOf("id", id, "resource", resource));

        Publisher<TestWebSocketClient> client = webSocketClient.connect(TestWebSocketClient.class, uri);

        AtomicReference<TestWebSocketClient> result = new AtomicReference<>();

        client.subscribe(new Subscriber<TestWebSocketClient>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                subscription.request(1);
            }

            @Override
            public void onNext(TestWebSocketClient testWebSocketClient) {
                result.set(testWebSocketClient);
                subscription.cancel();
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });

        while (result.get() == null) {

        }

        return result.get();
    }

    @ClientWebSocket
    static abstract class TestWebSocketClient implements AutoCloseable {

        final Collection<String> replies = new ConcurrentLinkedDeque<>();

        @OnOpen
        void onOpen() { }

        @OnMessage
        void onMessage(String message) {
            replies.add(message);
        }

        @OnClose
        void onClose() { }

        abstract void send(String message);

    }

    @Test
    void websocketEmitMessageOnTopicUpdate(){
        HttpRequest<?> request;
        HttpResponse<?> response;


        // create groups
        response = createGroup("Theta");
        assertEquals(OK, response.getStatus());
        Optional<Group> thetaOptional = response.getBody(Group.class);
        assertTrue(thetaOptional.isPresent());
        Group theta = thetaOptional.get();

        // create topic
        response = createTopic("Abc123", TopicKind.B, theta.getId());
        assertEquals(OK, response.getStatus());
        Optional<TopicDTO> topicOptional = response.getBody(TopicDTO.class);
        assertTrue(topicOptional.isPresent());
        assertEquals("Abc123", topicOptional.get().getName());

        // connect client
        TestWebSocketClient topicWsClient = createWebSocketClient(embeddedServer.getPort(), "topics", topicOptional.get().getId());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(topicWsClient.replies.contains("topic_updated"));

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

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertTrue(topicWsClient.replies.contains("topic_updated"));
    }

    @Test
    void websocketEmitMessageOnApplicationUpdate(){
        HttpRequest<?> request;
        HttpResponse<?> response;

        // create groups
        response = createGroup("Theta");
        assertEquals(OK, response.getStatus());
        Optional<Group> thetaOptional = response.getBody(Group.class);
        assertTrue(thetaOptional.isPresent());
        Group theta = thetaOptional.get();

        // create applications
        response = createApplication("TestApplication", theta.getId());
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
        assertTrue(applicationOptional.isPresent());
        ApplicationDTO application = applicationOptional.get();


        TestWebSocketClient applicationsWsClient = createWebSocketClient(embeddedServer.getPort(), "applications", application.getId());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertFalse(applicationsWsClient.replies.contains("application_updated"));

        // with same name different description
        application.setDescription("This is a description");
        request = HttpRequest.POST("/applications/save", application);
        response = blockingClient.exchange(request, ApplicationDTO.class);
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> updatedApplicationOptional = response.getBody(ApplicationDTO.class);
        assertTrue(updatedApplicationOptional.isPresent());
        ApplicationDTO updatedApplication = updatedApplicationOptional.get();
        assertEquals("This is a description", updatedApplication.getDescription());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertTrue(applicationsWsClient.replies.contains("application_updated"));
    }

    private HttpResponse<?> createGroup(String groupName) {
        Group group = new Group(groupName);
        HttpRequest<?> request = HttpRequest.POST("/groups/save", group);
        return blockingClient.exchange(request, Group.class);
    }

    private HttpResponse<?> createApplication(String applicationName, Long groupId) {
        return createApplication(applicationName, groupId, null);
    }

    private HttpResponse<?> createApplication(String applicationName, Long groupId, String description) {
        ApplicationDTO applicationDTO = new ApplicationDTO();
        applicationDTO.setName(applicationName);
        applicationDTO.setGroup(groupId);
        applicationDTO.setDescription(description);

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

}

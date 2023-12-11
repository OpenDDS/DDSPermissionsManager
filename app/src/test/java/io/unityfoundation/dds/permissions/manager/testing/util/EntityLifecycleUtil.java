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
package io.unityfoundation.dds.permissions.manager.testing.util;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.CreateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.ActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.CreateActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.CreateGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.AccessPermissionDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.CreateGrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.GrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.group.SimpleGroupDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserDTO;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserResponseDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicDTO;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.CreateTopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.TopicSetDTO;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.http.HttpStatus.CREATED;
import static io.micronaut.http.HttpStatus.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class EntityLifecycleUtil {
    
    @Inject
    @Client("/api")
    HttpClient client;
    
    public GrantDTO createGenericApplicationGrant() {
        return createGenericApplicationGrant(null);
    }
    public GrantDTO createGenericApplicationGrant(Long groupId) {
        HttpResponse<?> response;

        if (groupId == null) {
            response = createGroup("MyGroup");
            assertEquals(OK, response.getStatus());
            Optional<SimpleGroupDTO> thetaOptional = response.getBody(SimpleGroupDTO.class);
            assertTrue(thetaOptional.isPresent());
            SimpleGroupDTO group = thetaOptional.get();
            groupId = group.getId();
        }

        response = createApplication("MyApplication", groupId);
        assertEquals(OK, response.getStatus());
        Optional<ApplicationDTO> applicationOptional = response.getBody(ApplicationDTO.class);
        assertTrue(applicationOptional.isPresent());
        ApplicationDTO applicationDTO = applicationOptional.get();

        response = getApplicationGrantToken(applicationDTO.getId());
        assertEquals(OK, response.getStatus());
        Optional<String> grantTokenOptional = response.getBody(String.class);
        assertTrue(grantTokenOptional.isPresent());
        String applicationGrantToken = grantTokenOptional.get();

        response = createGrantDuration("MyGrantDuration", groupId);
        assertEquals(OK, response.getStatus());
        Optional<GrantDurationDTO> grantDuration = response.getBody(GrantDurationDTO.class);
        assertTrue(grantDuration.isPresent());

        response = createApplicationGrant(applicationGrantToken, groupId, "MyGrant", grantDuration.get().getId());
        assertEquals(CREATED, response.getStatus());
        Optional<GrantDTO> grantDTOOptional = response.getBody(GrantDTO.class);
        assertTrue(grantDTOOptional.isPresent());
        return grantDTOOptional.get();
    }

    public HttpResponse<?> createGroup(String groupName) {
        return createGroup(groupName, null);
    }

    public HttpResponse<?> createGroup(String groupName, String description) {
        SimpleGroupDTO group = new SimpleGroupDTO();
        group.setName(groupName);
        group.setDescription(description);
        HttpRequest<?> request = HttpRequest.POST("/groups/save", group);
        return client.toBlocking().exchange(request, SimpleGroupDTO.class);
    }

    public HttpResponse<?> addGroupMembership(Long groupId, String email, boolean isAdmin) {
        GroupUserDTO dto = new GroupUserDTO();
        dto.setPermissionsGroup(groupId);
        dto.setEmail(email);
        dto.setTopicAdmin(isAdmin);

        HttpRequest<?>  request = HttpRequest.POST("/group_membership", dto);
        return client.toBlocking().exchange(request, GroupUserResponseDTO.class);
    }

    public HttpResponse<?> createApplication(String applicationName, Long groupId) {
        return createApplication(applicationName, groupId, null);
    }

    public HttpResponse<?> createApplication(String applicationName, Long groupId, String description) {
        ApplicationDTO applicationDTO = new ApplicationDTO();
        applicationDTO.setName(applicationName);
        applicationDTO.setGroup(groupId);
        applicationDTO.setDescription(description);

        HttpRequest<?> request = HttpRequest.POST("/applications/save", applicationDTO);
        return client.toBlocking().exchange(request, ApplicationDTO.class);
    }

    public HttpResponse<?> getApplicationGrantToken(Long applicationId) {
        HttpRequest<?> request = HttpRequest.GET("/applications/generate_grant_token/" + applicationId);
        return client.toBlocking().exchange(request, String.class);
    }

    public HttpResponse<?> createTopic(Long groupId, String name) {
        TopicDTO topicDTO = new TopicDTO();
        topicDTO.setName(name);
        topicDTO.setGroup(groupId);

        HttpRequest<?>  request = HttpRequest.POST("/topics/save", topicDTO);
        return client.toBlocking().exchange(request, TopicDTO.class);
    }

    public HttpResponse<?> createTopic(String topicName, TopicKind topicKind, Long groupId) {
        TopicDTO topicDTO = new TopicDTO();
        topicDTO.setName(topicName);
        topicDTO.setGroup(groupId);
        topicDTO.setKind(topicKind);

        HttpRequest<?> request = HttpRequest.POST("/topics/save", topicDTO);
        return client.toBlocking().exchange(request, TopicDTO.class);
    }

    public HttpResponse<?> createTopicSet(String name, Long groupId) {
        CreateTopicSetDTO abcDTO = new CreateTopicSetDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);

        HttpRequest<?> request = HttpRequest.POST("/topic-sets", abcDTO);
        return client.toBlocking().exchange(request, TopicSetDTO.class);
    }

    public HttpResponse<?> addTopicToTopicSet(Long topicSetId, Long topicId) {
        HttpRequest<?> request = HttpRequest.POST("/topic-sets/" + topicSetId + "/" + topicId, Map.of());
        return client.toBlocking().exchange(request, TopicSetDTO.class);
    }

    public Long createTopicSetWithTopics(String name, Long groupId, Set<Long> topicIds) {
        CreateTopicSetDTO abcDTO = new CreateTopicSetDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);

        HttpRequest<?> request = HttpRequest.POST("/topic-sets", abcDTO);
        HttpResponse<?> response = client.toBlocking().exchange(request, TopicSetDTO.class);
        Optional<TopicSetDTO> topicSetOptional = response.getBody(TopicSetDTO.class);
        assertTrue(topicSetOptional.isPresent());
        TopicSetDTO topicSetDTO = topicSetOptional.get();

        topicIds.forEach(topicId -> addTopicToTopicSet(topicSetDTO.getId(), topicId));

        return topicSetDTO.getId();
    }

    public HttpResponse<?> createGrantDuration(String name, Long groupId) {
        return createGrantDuration(name, groupId, null);
    }

    public HttpResponse<?> createGrantDuration(String name, Long groupId, String durationMetadata) {
        CreateGrantDurationDTO abcDTO = new CreateGrantDurationDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);
        abcDTO.setDurationInMilliseconds(30000L);
        abcDTO.setDurationMetadata(durationMetadata);


        HttpRequest<?> request = HttpRequest.POST("/grant_durations", abcDTO);
        return client.toBlocking().exchange(request, GrantDurationDTO.class);
    }
    public HttpResponse<?> createApplicationGrant(String applicationGrantToken, Long groupId, String name, Long durationId) {
        CreateGrantDTO createGrantDTO = new CreateGrantDTO();
        createGrantDTO.setGroupId(groupId);
        createGrantDTO.setName(name);
        createGrantDTO.setGrantDurationId(durationId);

        HttpRequest<?> request = HttpRequest.POST("/application_grants/", createGrantDTO)
                .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
        return client.toBlocking().exchange(request, GrantDTO.class);
    }

    public HttpResponse<?> createActionInterval(String name, Long groupId) {
        CreateActionIntervalDTO abcDTO = new CreateActionIntervalDTO();
        abcDTO.setName(name);
        abcDTO.setGroupId(groupId);
        abcDTO.setStartDate(Instant.now());
        abcDTO.setEndDate(Instant.now().plus(1, ChronoUnit.DAYS));

        HttpRequest<?> request = HttpRequest.POST("/action_intervals", abcDTO);
        return client.toBlocking().exchange(request, ActionIntervalDTO.class);
    }

    public HttpResponse<?> createAction(Long grantId, Long intervalId) {
        return createAction(grantId, intervalId, null, null, null, null);
    }

    public HttpResponse<?> createAction(Long grantId, Long intervalId, Boolean isPublishAction) {
        return createAction(grantId, intervalId, isPublishAction, null, null, null);
    }

    public HttpResponse<?> createAction(Long grantId, Long intervalId, Boolean isPublishAction, Set<Long> topics, Set<Long> topicSets, Set<String> partitions) {
        CreateActionDTO create = new CreateActionDTO();
        create.setPublishAction(isPublishAction);
        create.setApplicationGrantId(grantId);
        create.setActionIntervalId(intervalId);
        create.setTopicIds(topics);
        create.setTopicSetIds(topicSets);
        create.setPartitions(partitions);

        HttpRequest<?> request = HttpRequest.POST("/actions", create);
        return client.toBlocking().exchange(request, ActionDTO.class);
    }

    public HttpResponse<?> createApplicationPermission(Long applicationId, Long topicId, boolean read, boolean write) {
        HttpRequest<?> request;

        // generate grant token for application
        request = HttpRequest.GET("/applications/generate_grant_token/" + applicationId);
        HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
        assertEquals(OK, response.getStatus());
        Optional<String> optional = response.getBody(String.class);
        assertTrue(optional.isPresent());
        String applicationGrantToken = optional.get();

        Map<String, Boolean> payload = Map.of("read", read, "write", write);

        request = HttpRequest.POST("/application_permissions/" + topicId, payload)
                .header(ApplicationPermissionService.APPLICATION_GRANT_TOKEN, applicationGrantToken);
        return client.toBlocking().exchange(request, AccessPermissionDTO.class);
    }
}

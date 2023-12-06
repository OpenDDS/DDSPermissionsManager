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
package io.unityfoundation.dds.permissions.manager.model.topic;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.authentication.AuthenticationException;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.model.actiontopic.ActionTopicRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionService;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topicsettopic.TopicSetTopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Singleton
public class TopicService {

    private final TopicRepository topicRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;
    private final GroupRepository groupRepository;
    private final ActionTopicRepository actionTopicRepository;
    private final TopicSetTopicRepository topicSetTopicRepository;
    private final ApplicationPermissionService applicationPermissionService;
    private final OnUpdateTopicWebSocket onUpdateTopicWebSocket;

    public TopicService(TopicRepository topicRepository, SecurityUtil securityUtil, GroupUserService groupUserService, GroupRepository groupRepository, ActionTopicRepository actionTopicRepository, TopicSetTopicRepository topicSetTopicRepository, ApplicationPermissionService applicationPermissionService, OnUpdateTopicWebSocket onUpdateTopicWebSocket) {
        this.topicRepository = topicRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
        this.groupRepository = groupRepository;
        this.actionTopicRepository = actionTopicRepository;
        this.topicSetTopicRepository = topicSetTopicRepository;
        this.applicationPermissionService = applicationPermissionService;
        this.onUpdateTopicWebSocket = onUpdateTopicWebSocket;
    }

    public Page<TopicDTO> findAll(Pageable pageable, String filter, Long groupId) {
        return getTopicPage(pageable, filter, groupId).map(TopicDTO::new);
    }

    private Page<Topic> getTopicPage(Pageable pageable, String filter, Long groupId) {

        if(!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (groupId == null) {
                    return topicRepository.findAll(pageable);
                }
                return topicRepository.findAllByPermissionsGroupIdIn(List.of(groupId), pageable);
            }

            if (groupId == null) {
                return topicRepository.findAllByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, filter, pageable);
            }

            all = topicRepository.findIdByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, filter);

            return topicRepository.findAllByIdInAndPermissionsGroupIdIn(all, List.of(groupId), pageable);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            List<Long> groups = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());

            if (groups.isEmpty() || (groupId != null && !groups.contains(groupId))) {
                return Page.empty();
            }

            if (groupId != null) {
                // implies groupId exists in member's groups
                groups = List.of(groupId);
            }

            if (filter == null) {
                return topicRepository.findAllByPermissionsGroupIdIn(groups, pageable);
            }

            all = topicRepository.findIdByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return topicRepository.findAllByIdInAndPermissionsGroupIdIn(all, groups, pageable);
        }
    }

    public MutableHttpResponse<?> save(TopicDTO topicDTO) {

        Optional<Group> groupOptional = groupRepository.findById(topicDTO.getGroup());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        if (!securityUtil.isCurrentUserAdmin() && !isUserTopicAdminOfGroup(groupOptional.get())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        boolean isPublic = Boolean.TRUE.equals(topicDTO.getPublic());

        Topic topic;
        if (topicDTO.getId() != null) {
            Optional<Topic> optionalTopic = topicRepository.findById(topicDTO.getId());
            if (optionalTopic.isEmpty()) {
                throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            Topic savedTopic = optionalTopic.get();
            if (!topicDTO.getName().equals(savedTopic.getName())) {
                throw new DPMException(ResponseStatusCodes.TOPIC_NAME_UPDATE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
            } else if (!topicDTO.getKind().equals(savedTopic.getKind())) {
                throw new DPMException(ResponseStatusCodes.TOPIC_KIND_UPDATE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
            } else if (!topicDTO.getGroup().equals(savedTopic.getPermissionsGroup().getId())) {
                throw new DPMException(ResponseStatusCodes.TOPIC_CANNOT_UPDATE_GROUP_ASSOCIATION);
            } else if (!groupOptional.get().getMakePublic() && isPublic) {
                throw new DPMException(ResponseStatusCodes.TOPIC_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP);
            }

            savedTopic.setDescription(topicDTO.getDescription());
            savedTopic.setMakePublic(isPublic);

            topic = topicRepository.update(savedTopic);
            onUpdateTopicWebSocket.broadcastResourceEvent(OnUpdateTopicWebSocket.TOPIC_UPDATED, topic.getId());
        } else {
            // save
            Optional<Topic> searchTopicByNameAndGroup = topicRepository.findByNameAndPermissionsGroup(
                    topicDTO.getName().trim(), groupOptional.get());

            if (searchTopicByNameAndGroup.isPresent()) {
                throw new DPMException(ResponseStatusCodes.TOPIC_ALREADY_EXISTS);
            } else if (!groupOptional.get().getMakePublic() && isPublic) {
                throw new DPMException(ResponseStatusCodes.TOPIC_CANNOT_CREATE_NOR_UPDATE_UNDER_PRIVATE_GROUP);
            }

            Topic newTopic = new Topic(topicDTO.getName(), topicDTO.getKind(), topicDTO.getDescription(), isPublic);
            newTopic.setPermissionsGroup(groupOptional.get());

            topic = topicRepository.save(newTopic);
        }

        TopicDTO responseTopicDTO = new TopicDTO(topic);
        return HttpResponse.ok(responseTopicDTO);
    }

    public HttpResponse deleteById(Long id) throws AuthenticationException {
        Optional<Topic> optionalTopic = topicRepository.findById(id);
        if (optionalTopic.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Topic topic = optionalTopic.get();
        if (!securityUtil.isCurrentUserAdmin() && !isUserTopicAdminOfGroup(topic.getPermissionsGroup())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        // TODO - Need to investigate cascade management to eliminate this
        applicationPermissionService.deleteAllByTopic(topic);
        removeFromAssociatedActions(topic);

        topicRepository.deleteById(id);
        onUpdateTopicWebSocket.broadcastResourceEvent(OnUpdateTopicWebSocket.TOPIC_DELETED, topic.getId());
        return HttpResponse.seeOther(URI.create("/api/topics"));
    }

    private void removeFromAssociatedActions(Topic topic) {
        actionTopicRepository.deleteByPermissionsTopicId(topic.getId());
        topicSetTopicRepository.deleteByPermissionsTopicId(topic.getId());
    }

    public HttpResponse show(Long id) {
        Optional<Topic> topicOptional = topicRepository.findById(id);
        if (topicOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Topic topic = topicOptional.get();
        if (!topic.getMakePublic() &&
                !securityUtil.isCurrentUserAdmin() &&
                !(groupUserService.isCurrentUserMemberOfGroup(topic.getPermissionsGroup().getId()))
        ) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        TopicDTO topicResponseDTO = new TopicDTO(topic);

        return HttpResponse.ok(topicResponseDTO);
    }

    private boolean isUserTopicAdminOfGroup(Group group) {
        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        return groupUserService.isUserTopicAdminOfGroup(group.getId(), user.getId());
    }

    public Optional<Topic> findById(Long topicId) {
        return topicRepository.findById(topicId);
    }
}

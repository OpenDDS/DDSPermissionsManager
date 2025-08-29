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
package io.unityfoundation.dds.permissions.manager.model.topicset;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.CreateTopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.TopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.dto.UpdateTopicSetDTO;
import io.unityfoundation.dds.permissions.manager.model.topicsettopic.TopicSetTopic;
import io.unityfoundation.dds.permissions.manager.model.topicsettopic.TopicSetTopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class TopicSetService {

    private final TopicSetRepository topicSetRepository;
    private final TopicRepository topicRepository;
    private final TopicSetTopicRepository topicSetTopicRepository;
    private final GroupRepository groupRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;

    public TopicSetService(TopicSetRepository topicSetRepository, TopicRepository topicRepository, TopicSetTopicRepository topicSetTopicRepository, GroupRepository groupRepository, SecurityUtil securityUtil, GroupUserService groupUserService) {
        this.topicSetRepository = topicSetRepository;
        this.topicRepository = topicRepository;
        this.topicSetTopicRepository = topicSetTopicRepository;
        this.groupRepository = groupRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<TopicSetDTO> findAll(Pageable pageable, String filter, Long groupId) {
        return getTopicSetDTOPage(getTopicSetPage(pageable, filter, groupId));
    }

    private Page<TopicSet> getTopicSetPage(Pageable pageable, String filter, Long groupId) {

        if(!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (groupId == null) {
                    return topicSetRepository.findAll(pageable);
                }
                return topicSetRepository.findAllByPermissionsGroupIdIn(List.of(groupId), pageable);
            }

            if (groupId == null) {
                return topicSetRepository.findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, pageable);
            }

            all = topicSetRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);

            return topicSetRepository.findAllByIdInAndPermissionsGroupIdIn(all, List.of(groupId), pageable);
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
                return topicSetRepository.findAllByPermissionsGroupIdIn(groups, pageable);
            }

            all = topicSetRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return topicSetRepository.findAllByIdInAndPermissionsGroupIdIn(all, groups, pageable);
        }
    }

    private Page<TopicSetDTO> getTopicSetDTOPage(Page<TopicSet> page) {
        return page.map(this::createDTO);
    }

    public TopicSetDTO findById(Long topicSetId) {
        Optional<TopicSet> topicSetOptional = topicSetRepository.findById(topicSetId);

        checkExistenceAndAuthorization(topicSetOptional);

        TopicSetDTO topicSetDTO = createDTO(topicSetOptional.get());
        List<String> admins = groupUserService.getAllTopicAdminsOfGroup(topicSetOptional.get().getId());
        topicSetDTO.setAdmins(admins);

        return topicSetDTO;
    }

    // POST /topic-set
    public MutableHttpResponse<?> create(CreateTopicSetDTO topicSetDTO) {

        Optional<Group> groupOptional = groupRepository.findById(topicSetDTO.getGroupId());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_SET_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        if (!securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isUserTopicAdminOfGroup(groupOptional.get().getId(), user.getId())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        Optional<TopicSet> searchTopicSetByNameAndGroup = topicSetRepository.findByNameAndPermissionsGroup(
                topicSetDTO.getName().trim(), groupOptional.get());

        if (searchTopicSetByNameAndGroup.isPresent()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_SET_ALREADY_EXISTS);
        }

        TopicSet newTopicSet = new TopicSet(topicSetDTO.getName(), groupOptional.get());

        TopicSetDTO responseTopicDTO = createDTO(topicSetRepository.save(newTopicSet));
        return HttpResponse.ok(responseTopicDTO);
    }

    public MutableHttpResponse<?> update(@NotNull Long topicSetId, UpdateTopicSetDTO topicSetDTO) {

        Optional<TopicSet> topicSetOptional = topicSetRepository.findById(topicSetId);

        checkExistenceAndAdminAuthorization(topicSetOptional);

        Optional<TopicSet> searchTopicSetByNameAndGroup = topicSetRepository.findByNameAndPermissionsGroup(
                topicSetDTO.getName().trim(), topicSetOptional.get().getPermissionsGroup());

        if (searchTopicSetByNameAndGroup.isPresent()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_SET_ALREADY_EXISTS);
        }

        TopicSet topicSet = topicSetOptional.get();
        topicSet.setName(topicSetDTO.getName().trim());

        TopicSetDTO dto = createDTO(topicSetRepository.update(topicSet));
        return HttpResponse.ok(dto);
    }

    public HttpResponse deleteById(Long topicSetId) {

        Optional<TopicSet> topicSetOptional = topicSetRepository.findById(topicSetId);

        checkExistenceAndAdminAuthorization(topicSetOptional);

        topicSetRepository.delete(topicSetOptional.get());
        return HttpResponse.noContent();
    }

    public HttpResponse<TopicSetDTO> addTopic(Long topicSetId, Long topicId) {

        Optional<TopicSet> topicSetOptional = topicSetRepository.findById(topicSetId);

        checkExistenceAndAdminAuthorization(topicSetOptional);

        Optional<Topic> topicById = topicRepository.findById(topicId);
        if (topicById.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Topic topic = topicById.get();
        TopicSet topicSet = topicSetOptional.get();

        if (!topicSet.getPermissionsGroup().getId().equals(topic.getPermissionsGroup().getId())) {
            throw new DPMException(ResponseStatusCodes.TOPIC_SET_AND_TOPIC_DOES_NOT_BELONG_TO_SAME_GROUP, HttpStatus.CONFLICT);
        } else if (doesTopicSetContainTopic(topicSet, topic)) {
            throw new DPMException(ResponseStatusCodes.TOPIC_ALREADY_EXISTS);
        }

        topicSetTopicRepository.save(new TopicSetTopic(topicSet, topic));
        topicSet.setDateUpdated(Instant.now());
        return HttpResponse.created(createDTO(topicSetRepository.update(topicSet)));
    }

    public HttpResponse<TopicSetDTO> removeTopic(Long topicSetId, Long topicId) {

        Optional<TopicSet> topicSetOptional = topicSetRepository.findById(topicSetId);

        checkExistenceAndAdminAuthorization(topicSetOptional);

        Optional<Topic> topicById = topicRepository.findById(topicId);
        if (topicById.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Topic topic = topicById.get();
        TopicSet topicSet = topicSetOptional.get();

        if (!topicSet.getPermissionsGroup().getId().equals(topic.getPermissionsGroup().getId())) {
            throw new DPMException(ResponseStatusCodes.TOPIC_SET_AND_TOPIC_DOES_NOT_BELONG_TO_SAME_GROUP, HttpStatus.CONFLICT);
        } else if (!doesTopicSetContainTopic(topicSet, topic)) {
            throw new DPMException(ResponseStatusCodes.TOPIC_DOES_NOT_EXISTS_IN_TOPIC_SET, HttpStatus.NOT_FOUND);
        }

        topicSetTopicRepository.deleteByPermissionsTopicSetAndPermissionsTopic(topicSet, topic);
        topicSet.setDateUpdated(Instant.now());
        return HttpResponse.ok(createDTO(topicSetRepository.update(topicSet)));
    }

    public boolean doesTopicSetContainTopic(TopicSet topicSet, Topic topic) {
        return topicSetTopicRepository.existsByPermissionsTopicSetAndPermissionsTopic(topicSet, topic);
    }

    public TopicSetDTO createDTO(TopicSet topicSet) {
        List<Topic> topics = topicSetTopicRepository.findPermissionsTopicByPermissionsTopicSet(topicSet);
        return new TopicSetDTO(
                topicSet.getId(),
                topicSet.getName(),
                topicSet.getPermissionsGroup().getId(),
                topicSet.getPermissionsGroup().getName(),
                topics.stream()
                        .map(topic -> Map.of(topic.getId(), topic.getName()))
                        .collect(Collectors.toSet()),
                topicSet.getDateCreated(),
                topicSet.getDateUpdated()
        );
    }

    private void checkExistenceAndAdminAuthorization(Optional<TopicSet> topicSetOptional) {
        if (topicSetOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_SET_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserTopicAdminOfGroup(topicSetOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private void checkExistenceAndAuthorization(Optional<TopicSet> topicSetOptional) {
        if (topicSetOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.TOPIC_SET_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserMemberOfGroup(topicSetOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }
}

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
package io.unityfoundation.dds.permissions.manager.model.action;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.CreateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.UpdateActionDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionInterval;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionIntervalRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrant;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrantRepository;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSet;
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSetRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class ActionService {

    private final ActionRepository actionRepository;
    private final ApplicationGrantRepository applicationGrantRepository;
    private final ActionIntervalRepository actionIntervalRepository;
    private final TopicRepository topicRepository;
    private final TopicSetRepository topicSetRepository;
    private final ActionPartitionRepository actionPartitionRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;

    public ActionService(ActionRepository actionRepository, ApplicationGrantRepository applicationGrantRepository, ActionIntervalRepository actionIntervalRepository, TopicRepository topicRepository, TopicSetRepository topicSetRepository, ActionPartitionRepository actionPartitionRepository, GroupRepository groupRepository, SecurityUtil securityUtil, GroupUserService groupUserService) {
        this.actionRepository = actionRepository;
        this.applicationGrantRepository = applicationGrantRepository;
        this.actionIntervalRepository = actionIntervalRepository;
        this.topicRepository = topicRepository;
        this.topicSetRepository = topicSetRepository;
        this.actionPartitionRepository = actionPartitionRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<ActionDTO> findAll(Pageable pageable, String filter, Long grantId) {
        return getGrantDurationDTOPage(getActionPage(pageable, filter, grantId));
    }

    private Page<Action> getActionPage(Pageable pageable, String filter, Long grantId) {

        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (grantId == null) {
                    return actionRepository.findAll(pageable);
                }
                return actionRepository.findAllByApplicationGrantIdIn(List.of(grantId), pageable);
            }

            if (grantId == null) {
                return actionRepository.findAllByApplicationGrantNameContainsIgnoreCase(filter, pageable);
            }

            all = actionRepository.findIdByApplicationGrantNameContainsIgnoreCase(filter);

            return actionRepository.findAllByIdInAndApplicationGrantIdIn(all, List.of(grantId), pageable);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            List<Long> groups = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());
            if (groups.isEmpty()) {
                return Page.empty();
            }

            List<Long> userAccessibleGrants = applicationGrantRepository.findIdByPermissionsGroupIdIn(groups);
            if (userAccessibleGrants.isEmpty()) {
                return Page.empty();
            }

            if (groups.isEmpty() || (grantId != null && !userAccessibleGrants.contains(grantId))) {
                return Page.empty();
            }

            if (grantId != null) {
                // implies grantId exists in member's accessible grants
                userAccessibleGrants = List.of(grantId);
            }

            if (filter == null) {
                return actionRepository.findAllByApplicationGrantIdIn(userAccessibleGrants, pageable);
            }

            all = actionRepository.findIdByApplicationGrantNameContainsIgnoreCase(filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return actionRepository.findAllByIdInAndApplicationGrantIdIn(all, userAccessibleGrants, pageable);
        }
    }

    private Page<ActionDTO> getGrantDurationDTOPage(Page<Action> page) {
        return page.map(this::createDTO);
    }

    public ActionDTO findById(Long actionId) {
        Optional<Action> actionOptional = actionRepository.findById(actionId);

        checkExistenceAndAuthorization(actionOptional);

        return createDTO(actionOptional.get());
    }

    public MutableHttpResponse<?> create(CreateActionDTO createActionDTO) {

        Optional<ApplicationGrant> applicationGrantOptional = applicationGrantRepository.findById(createActionDTO.getApplicationGrantId());

        if (applicationGrantOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_REQUIRES_APPLICATION_GRANT_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        ApplicationGrant applicationGrant = applicationGrantOptional.get();

        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        if (!securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isUserTopicAdminOfGroup(applicationGrant.getPermissionsGroup().getId(), user.getId())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        Optional<ActionInterval> actionIntervalOptional = actionIntervalRepository.findByIdAndPermissionsGroupId(createActionDTO.getActionIntervalId(), applicationGrant.getPermissionsGroup().getId());

        if (actionIntervalOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_WAS_NOT_FOUND_OR_DOES_BELONG_TO_THE_SAME_GROUP_AS_APPLICATION_GRANT, HttpStatus.NOT_FOUND);
        }

        Set<TopicSet> topicSets = validateTopicSetsExistenceAndInSameGroupAsGrant(applicationGrant.getPermissionsGroup().getId(), createActionDTO.getTopicSetIds());
        Set<Topic> topics = validateTopicsExistenceAndInSameGroupAsGrant(applicationGrant.getPermissionsGroup().getId(), createActionDTO.getTopicIds());

        Action newAction = new Action(applicationGrant, actionIntervalOptional.get());
        newAction.setTopicSets(topicSets);
        newAction.setTopics(topics);

        Action savedAction = actionRepository.save(newAction);
        addPartitionsToPermission(savedAction, createActionDTO.getPartitions());

        return HttpResponse.ok(createDTO(actionRepository.update(savedAction)));
    }

    public MutableHttpResponse<?> update(@NotNull Long actionId, UpdateActionDTO updateActionDTO) {

        Optional<Action> actionOptional = actionRepository.findById(actionId);

        checkExistenceAndAdminAuthorization(actionOptional);

        ApplicationGrant applicationGrant = actionOptional.get().getApplicationGrant();

        Optional<ActionInterval> actionIntervalOptional = actionIntervalRepository.findByIdAndPermissionsGroupId(updateActionDTO.getActionIntervalId(), applicationGrant.getPermissionsGroup().getId());

        if (actionIntervalOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_WAS_NOT_FOUND_OR_DOES_BELONG_TO_THE_SAME_GROUP_AS_APPLICATION_GRANT, HttpStatus.NOT_FOUND);
        }

        Action action = actionOptional.get();
        action.setActionInterval(actionIntervalOptional.get());

        actionPartitionRepository.deleteAll(actionOptional.get().getPartitions());
        addPartitionsToPermission(action, updateActionDTO.getPartitions());

        ActionDTO dto = createDTO(actionRepository.update(action));
        return HttpResponse.ok(dto);
    }

    public HttpResponse deleteById(Long actionId) {

        Optional<Action> actionOptional = actionRepository.findById(actionId);

        checkExistenceAndAdminAuthorization(actionOptional);

        actionRepository.delete(actionOptional.get());
        return HttpResponse.noContent();
    }

    public ActionDTO createDTO(Action action) {
        Set<Map> topics = action.getTopics().stream().map((Function<Topic, Map>) topic -> Map.of("id", topic.getId(), "name", topic.getName())).collect(Collectors.toSet());
        Set<Map> topicSets = action.getTopicSets().stream().map((Function<TopicSet, Map>) topicSet -> Map.of("id", topicSet.getId(), "name", topicSet.getName())).collect(Collectors.toSet());
        Set<String> partitions = action.getPartitions().stream().map(ActionPartition::getPartitionName).collect(Collectors.toSet());
        return new ActionDTO(
                action.getId(),
                action.getApplicationGrant().getId(),
                action.getActionInterval().getId(),
                action.getActionInterval().getName(),
                topics,
                topicSets,
                partitions,
                action.getDateCreated(),
                action.getDateUpdated()
                );
    }

    private void addPartitionsToPermission(Action action, Set<String> partitions) {
        if (partitions != null) {
            action.setPartitions(partitions.stream()
                    .map(p -> actionPartitionRepository.save(new ActionPartition(action, p)))
                    .collect(Collectors.toSet()));
        }
    }

    private Set<Topic> validateTopicsExistenceAndInSameGroupAsGrant(Long groupId, Set<Long> topicIds) {
        Set<Optional<Topic>> optionalTopics = topicIds.stream()
                .map(id -> topicRepository.findByIdAndPermissionsGroupId(id, groupId))
                .collect(Collectors.toSet());

        if (optionalTopics.stream().anyMatch(Optional::isEmpty)) {
            throw new DPMException(ResponseStatusCodes.A_PROVIDED_TOPIC_WAS_NOT_FOUND_OR_DOES_NOT_BELONG_TO_SAME_GROUP, HttpStatus.NOT_FOUND);
        }

        return optionalTopics.stream().map(Optional::get).collect(Collectors.toSet());
    }

    private Set<TopicSet> validateTopicSetsExistenceAndInSameGroupAsGrant(Long groupId, Set<Long> topicSetIds) {
        Set<Optional<TopicSet>> optionalTopicSets = topicSetIds.stream()
                .map(id -> topicSetRepository.findByIdAndPermissionsGroupId(id, groupId))
                .collect(Collectors.toSet());

        if (optionalTopicSets.stream().anyMatch(Optional::isEmpty)) {
            throw new DPMException(ResponseStatusCodes.A_PROVIDED_TOPIC_SET_WAS_NOT_FOUND_OR_DOES_NOT_BELONG_TO_SAME_GROUP, HttpStatus.NOT_FOUND);
        }

        return optionalTopicSets.stream().map(Optional::get).collect(Collectors.toSet());
    }

    private void checkExistenceAndAdminAuthorization(Optional<Action> actionOptional) {
        if (actionOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserTopicAdminOfGroup(actionOptional.get().getApplicationGrant().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private void checkExistenceAndAuthorization(Optional<Action> actionOptional) {
        if (actionOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserMemberOfGroup(actionOptional.get().getApplicationGrant().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }
}
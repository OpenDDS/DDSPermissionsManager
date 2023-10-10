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
package io.unityfoundation.dds.permissions.manager.model.actioninterval;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.CreateActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.dto.ActionIntervalDTO;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class ActionIntervalService {

    private final ActionIntervalRepository actionIntervalRepository;
    private final GroupRepository groupRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;

    public ActionIntervalService(ActionIntervalRepository actionIntervalRepository, GroupRepository groupRepository, SecurityUtil securityUtil, GroupUserService groupUserService) {
        this.actionIntervalRepository = actionIntervalRepository;
        this.groupRepository = groupRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<ActionIntervalDTO> findAll(Pageable pageable, String filter, Long groupId) {
        return getActionIntervalDTOPage(getActionIntervalPage(pageable, filter, groupId));
    }

    private Page<ActionInterval> getActionIntervalPage(Pageable pageable, String filter, Long groupId) {

        if(!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (groupId == null) {
                    return actionIntervalRepository.findAll(pageable);
                }
                return actionIntervalRepository.findAllByPermissionsGroupIdIn(List.of(groupId), pageable);
            }

            if (groupId == null) {
                return actionIntervalRepository.findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, pageable);
            }

            all = actionIntervalRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);

            return actionIntervalRepository.findAllByIdInAndPermissionsGroupIdIn(all, List.of(groupId), pageable);
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
                return actionIntervalRepository.findAllByPermissionsGroupIdIn(groups, pageable);
            }

            all = actionIntervalRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return actionIntervalRepository.findAllByIdInAndPermissionsGroupIdIn(all, groups, pageable);
        }
    }

    private Page<ActionIntervalDTO> getActionIntervalDTOPage(Page<ActionInterval> page) {
        return page.map(this::createDTO);
    }

    public ActionIntervalDTO findById(Long actionIntervalId) {
        Optional<ActionInterval> actionIntervalOptional = actionIntervalRepository.findById(actionIntervalId);

        checkExistenceAndAuthorization(actionIntervalOptional);

        return createDTO(actionIntervalOptional.get());
    }

    public MutableHttpResponse<?> create(CreateActionIntervalDTO actionIntervalDTO) {

        Optional<Group> groupOptional = groupRepository.findById(actionIntervalDTO.getGroupId());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        if (!securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isUserTopicAdminOfGroup(groupOptional.get().getId(), user.getId())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        Optional<ActionInterval> searchActionIntervalByNameAndGroup = actionIntervalRepository.findByNameAndPermissionsGroup(
                actionIntervalDTO.getName().trim(), groupOptional.get());

        if (searchActionIntervalByNameAndGroup.isPresent()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_ALREADY_EXISTS);
        }

        ActionInterval newActionInterval = new ActionInterval(
                actionIntervalDTO.getName(),
                groupOptional.get());
        newActionInterval.setStartDate(actionIntervalDTO.getStartDate());
        newActionInterval.setEndDate(actionIntervalDTO.getEndDate());

        ActionIntervalDTO responseTopicDTO = createDTO(actionIntervalRepository.save(newActionInterval));
        return HttpResponse.ok(responseTopicDTO);
    }

    public MutableHttpResponse<?> update(@NotNull Long topicSetId, ActionIntervalDTO actionIntervalDTO) {
        Optional<Group> groupOptional = groupRepository.findById(actionIntervalDTO.getGroupId());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        Optional<ActionInterval> actionIntervalOptional = actionIntervalRepository.findById(topicSetId);

        checkExistenceAndAdminAuthorization(actionIntervalOptional);

        Optional<ActionInterval> searchActionIntervalByNameAndGroup = actionIntervalRepository.findByNameAndPermissionsGroup(
                actionIntervalDTO.getName().trim(), groupOptional.get());

        if (searchActionIntervalByNameAndGroup.isPresent() &&
                searchActionIntervalByNameAndGroup.get().getId() != actionIntervalOptional.get().getId()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_ALREADY_EXISTS);
        }else if (!Objects.equals(actionIntervalOptional.get().getPermissionsGroup().getId(), actionIntervalDTO.getGroupId())) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_CANNOT_UPDATE_GROUP_ASSOCIATION);
        }

        ActionInterval actionInterval = actionIntervalOptional.get();
        actionInterval.setName(actionIntervalDTO.getName().trim());
        actionInterval.setStartDate(actionIntervalDTO.getStartDate());
        actionInterval.setEndDate(actionIntervalDTO.getEndDate());

        ActionIntervalDTO dto = createDTO(actionIntervalRepository.update(actionInterval));
        return HttpResponse.ok(dto);
    }

    public HttpResponse deleteById(Long actionIntervalId) {

        Optional<ActionInterval> actionIntervalOptional = actionIntervalRepository.findById(actionIntervalId);

        checkExistenceAndAdminAuthorization(actionIntervalOptional);

        actionIntervalRepository.delete(actionIntervalOptional.get());
        return HttpResponse.noContent();
    }

    public ActionIntervalDTO createDTO(ActionInterval actionInterval) {
        return new ActionIntervalDTO(
                actionInterval.getId(),
                actionInterval.getName(),
                actionInterval.getPermissionsGroup().getId(),
                actionInterval.getPermissionsGroup().getName(),
                actionInterval.getStartDate(),
                actionInterval.getEndDate());
    }

    private void checkExistenceAndAdminAuthorization(Optional<ActionInterval> actionIntervalOptional) {
        if (actionIntervalOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserTopicAdminOfGroup(actionIntervalOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private void checkExistenceAndAuthorization(Optional<ActionInterval> actionIntervalOptional) {
        if (actionIntervalOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.ACTION_INTERVAL_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserMemberOfGroup(actionIntervalOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }
}
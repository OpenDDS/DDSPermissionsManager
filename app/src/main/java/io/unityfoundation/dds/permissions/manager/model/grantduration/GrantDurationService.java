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
package io.unityfoundation.dds.permissions.manager.model.grantduration;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.GrantDurationDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.dto.CreateGrantDurationDTO;
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
public class GrantDurationService {

    private final GrantDurationRepository grantDurationRepository;
    private final GroupRepository groupRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;

    public GrantDurationService(GrantDurationRepository grantDurationRepository, GroupRepository groupRepository, SecurityUtil securityUtil, GroupUserService groupUserService) {
        this.grantDurationRepository = grantDurationRepository;
        this.groupRepository = groupRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<GrantDurationDTO> findAll(Pageable pageable, String filter, Long groupId) {
        return getGrantDurationDTOPage(getGrantDurationPage(pageable, filter, groupId));
    }

    private Page<GrantDuration> getGrantDurationPage(Pageable pageable, String filter, Long groupId) {

        if(!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (groupId == null) {
                    return grantDurationRepository.findAll(pageable);
                }
                return grantDurationRepository.findAllByPermissionsGroupIdIn(List.of(groupId), pageable);
            }

            if (groupId == null) {
                return grantDurationRepository.findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, pageable);
            }

            all = grantDurationRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);

            return grantDurationRepository.findAllByIdInAndPermissionsGroupIdIn(all, List.of(groupId), pageable);
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
                return grantDurationRepository.findAllByPermissionsGroupIdIn(groups, pageable);
            }

            all = grantDurationRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return grantDurationRepository.findAllByIdInAndPermissionsGroupIdIn(all, groups, pageable);
        }
    }

    private Page<GrantDurationDTO> getGrantDurationDTOPage(Page<GrantDuration> page) {
        return page.map(this::createDTO);
    }

    public GrantDurationDTO findById(Long grantDurationId) {
        Optional<GrantDuration> grantDurationOptional = grantDurationRepository.findById(grantDurationId);

        checkExistenceAndAuthorization(grantDurationOptional);

        return createDTO(grantDurationOptional.get());
    }

    public MutableHttpResponse<?> create(CreateGrantDurationDTO grantDurationDTO) {

        Optional<Group> groupOptional = groupRepository.findById(grantDurationDTO.getGroupId());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.GRANT_DURATION_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        if (!securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isUserTopicAdminOfGroup(groupOptional.get().getId(), user.getId())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        Optional<GrantDuration> searchGrantDurationByNameAndGroup = grantDurationRepository.findByNameAndPermissionsGroup(
                grantDurationDTO.getName().trim(), groupOptional.get());

        if (searchGrantDurationByNameAndGroup.isPresent()) {
            throw new DPMException(ResponseStatusCodes.GRANT_DURATION_ALREADY_EXISTS);
        }

        GrantDuration newGrantDuration = new GrantDuration(
                grantDurationDTO.getName(),
                groupOptional.get());
        newGrantDuration.setDurationInMilliseconds(grantDurationDTO.getDurationInMilliseconds());
        newGrantDuration.setDurationMetadata(grantDurationDTO.getDurationMetadata());

        GrantDurationDTO responseTopicDTO = createDTO(grantDurationRepository.save(newGrantDuration));
        return HttpResponse.ok(responseTopicDTO);
    }

    public MutableHttpResponse<?> update(@NotNull Long durationId, GrantDurationDTO grantDurationDTO) {
        Optional<Group> groupOptional = groupRepository.findById(grantDurationDTO.getGroupId());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.GRANT_DURATION_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        Optional<GrantDuration> grantDurationOptional = grantDurationRepository.findById(durationId);

        checkExistenceAndAdminAuthorization(grantDurationOptional);

        Optional<GrantDuration> searchGrantDurationByNameAndGroup = grantDurationRepository.findByNameAndPermissionsGroup(
                grantDurationDTO.getName().trim(), groupOptional.get());

        if (searchGrantDurationByNameAndGroup.isPresent()) {
            throw new DPMException(ResponseStatusCodes.GRANT_DURATION_ALREADY_EXISTS);
        }else if (!Objects.equals(grantDurationOptional.get().getPermissionsGroup().getId(), grantDurationDTO.getGroupId())) {
            throw new DPMException(ResponseStatusCodes.GRANT_DURATION_CANNOT_UPDATE_GROUP_ASSOCIATION);
        }

        GrantDuration grantDuration = grantDurationOptional.get();
        grantDuration.setName(grantDurationDTO.getName().trim());
        grantDuration.setDurationInMilliseconds(grantDurationDTO.getDurationInMilliseconds());
        grantDuration.setDurationMetadata(grantDurationDTO.getDurationMetadata());

        GrantDurationDTO dto = createDTO(grantDurationRepository.update(grantDuration));
        return HttpResponse.ok(dto);
    }

    public HttpResponse deleteById(Long grantDurationId) {

        Optional<GrantDuration> grantDurationOptional = grantDurationRepository.findById(grantDurationId);

        checkExistenceAndAdminAuthorization(grantDurationOptional);

        grantDurationRepository.delete(grantDurationOptional.get());
        return HttpResponse.noContent();
    }

    public GrantDurationDTO createDTO(GrantDuration grantDuration) {
        return new GrantDurationDTO(
                grantDuration.getId(),
                grantDuration.getName(),
                grantDuration.getPermissionsGroup().getId(),
                grantDuration.getPermissionsGroup().getName(),
                grantDuration.getDurationInMilliseconds(),
                grantDuration.getDurationMetadata()
        );
    }

    private void checkExistenceAndAdminAuthorization(Optional<GrantDuration> grantDurationOptional) {
        if (grantDurationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.GRANT_DURATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserTopicAdminOfGroup(grantDurationOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private void checkExistenceAndAuthorization(Optional<GrantDuration> grantDurationOptional) {
        if (grantDurationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.GRANT_DURATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserMemberOfGroup(grantDurationOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }
}
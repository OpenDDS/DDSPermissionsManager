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
package io.unityfoundation.dds.permissions.manager.model.expirationpolicy;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.dto.CreateExpirationPolicyDTO;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.dto.ExpirationPolicyDTO;
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
public class ExpirationPolicyService {

    private final ExpirationPolicyRepository expirationPolicyRepository;
    private final GroupRepository groupRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;

    public ExpirationPolicyService(ExpirationPolicyRepository expirationPolicyRepository, GroupRepository groupRepository, SecurityUtil securityUtil, GroupUserService groupUserService) {
        this.expirationPolicyRepository = expirationPolicyRepository;
        this.groupRepository = groupRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<ExpirationPolicyDTO> findAll(Pageable pageable, String filter, Long groupId) {
        return getExpirationPolicyDTOPage(getExpirationPolicyPage(pageable, filter, groupId));
    }

    private Page<ExpirationPolicy> getExpirationPolicyPage(Pageable pageable, String filter, Long groupId) {

        if(!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (groupId == null) {
                    return expirationPolicyRepository.findAll(pageable);
                }
                return expirationPolicyRepository.findAllByPermissionsGroupIdIn(List.of(groupId), pageable);
            }

            if (groupId == null) {
                return expirationPolicyRepository.findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, pageable);
            }

            all = expirationPolicyRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);

            return expirationPolicyRepository.findAllByIdInAndPermissionsGroupIdIn(all, List.of(groupId), pageable);
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
                return expirationPolicyRepository.findAllByPermissionsGroupIdIn(groups, pageable);
            }

            all = expirationPolicyRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return expirationPolicyRepository.findAllByIdInAndPermissionsGroupIdIn(all, groups, pageable);
        }
    }

    private Page<ExpirationPolicyDTO> getExpirationPolicyDTOPage(Page<ExpirationPolicy> page) {
        return page.map(this::createDTO);
    }

    public ExpirationPolicyDTO findById(Long expirationPolicyId) {
        Optional<ExpirationPolicy> expirationPolicyOptional = expirationPolicyRepository.findById(expirationPolicyId);

        checkExistenceAndAuthorization(expirationPolicyOptional);

        return createDTO(expirationPolicyOptional.get());
    }

    public MutableHttpResponse<?> create(CreateExpirationPolicyDTO expirationPolicyDTO) {

        Optional<Group> groupOptional = groupRepository.findById(expirationPolicyDTO.getGroupId());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.EXPIRATION_POLICY_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        if (!securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isUserTopicAdminOfGroup(groupOptional.get().getId(), user.getId())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        Optional<ExpirationPolicy> searchExpirationPolicyByNameAndGroup = expirationPolicyRepository.findByNameAndPermissionsGroup(
                expirationPolicyDTO.getName().trim(), groupOptional.get());

        if (searchExpirationPolicyByNameAndGroup.isPresent()) {
            throw new DPMException(ResponseStatusCodes.EXPIRATION_POLICY_ALREADY_EXISTS);
        }

        ExpirationPolicy newExpirationPolicy = new ExpirationPolicy(
                expirationPolicyDTO.getName(),
                expirationPolicyDTO.getRefreshDate(),
                groupOptional.get());
        newExpirationPolicy.setStartDate(expirationPolicyDTO.getStartDate());
        newExpirationPolicy.setEndDate(expirationPolicyDTO.getEndDate());

        ExpirationPolicyDTO responseTopicDTO = createDTO(expirationPolicyRepository.save(newExpirationPolicy));
        return HttpResponse.ok(responseTopicDTO);
    }

    public MutableHttpResponse<?> update(@NotNull Long topicSetId, ExpirationPolicyDTO expirationPolicyDTO) {
        Optional<Group> groupOptional = groupRepository.findById(expirationPolicyDTO.getGroupId());

        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.EXPIRATION_POLICY_REQUIRES_GROUP_ASSOCIATION, HttpStatus.NOT_FOUND);
        }

        Optional<ExpirationPolicy> expirationPolicyOptional = expirationPolicyRepository.findById(topicSetId);

        checkExistenceAndAdminAuthorization(expirationPolicyOptional);

        Optional<ExpirationPolicy> searchExpirationPolicyByNameAndGroup = expirationPolicyRepository.findByNameAndPermissionsGroup(
                expirationPolicyDTO.getName().trim(), groupOptional.get());

        if (searchExpirationPolicyByNameAndGroup.isPresent()) {
            throw new DPMException(ResponseStatusCodes.EXPIRATION_POLICY_ALREADY_EXISTS);
        }else if (!Objects.equals(expirationPolicyOptional.get().getPermissionsGroup().getId(), expirationPolicyDTO.getGroupId())) {
            throw new DPMException(ResponseStatusCodes.EXPIRATION_POLICY_CANNOT_UPDATE_GROUP_ASSOCIATION);
        }

        ExpirationPolicy expirationPolicy = expirationPolicyOptional.get();
        expirationPolicy.setName(expirationPolicyDTO.getName().trim());
        expirationPolicy.setRefreshDate(expirationPolicyDTO.getRefreshDate());
        expirationPolicy.setStartDate(expirationPolicyDTO.getStartDate());
        expirationPolicy.setEndDate(expirationPolicyDTO.getEndDate());

        ExpirationPolicyDTO dto = createDTO(expirationPolicyRepository.update(expirationPolicy));
        return HttpResponse.ok(dto);
    }

    public HttpResponse deleteById(Long expirationPolicyId) {

        Optional<ExpirationPolicy> expirationPolicyOptional = expirationPolicyRepository.findById(expirationPolicyId);

        checkExistenceAndAdminAuthorization(expirationPolicyOptional);

        expirationPolicyRepository.delete(expirationPolicyOptional.get());
        return HttpResponse.noContent();
    }

    public ExpirationPolicyDTO createDTO(ExpirationPolicy expirationPolicy) {
        return new ExpirationPolicyDTO(
                expirationPolicy.getId(),
                expirationPolicy.getName(),
                expirationPolicy.getPermissionsGroup().getId(),
                expirationPolicy.getPermissionsGroup().getName(),
                expirationPolicy.getRefreshDate(),
                expirationPolicy.getStartDate(),
                expirationPolicy.getEndDate());
    }

    private void checkExistenceAndAdminAuthorization(Optional<ExpirationPolicy> expirationPolicyOptional) {
        if (expirationPolicyOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.EXPIRATION_POLICY_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserTopicAdminOfGroup(expirationPolicyOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }

    private void checkExistenceAndAuthorization(Optional<ExpirationPolicy> expirationPolicyOptional) {
        if (expirationPolicyOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.EXPIRATION_POLICY_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserMemberOfGroup(expirationPolicyOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }
    }
}
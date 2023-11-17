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
package io.unityfoundation.dds.permissions.manager.model.applicationgrant;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.security.token.jwt.generator.claims.JwtClaims;
import io.micronaut.security.token.jwt.generator.claims.JwtClaimsSetAdapter;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.action.Action;
import io.unityfoundation.dds.permissions.manager.model.action.ActionService;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.DetailedGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.GrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.CreateGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto.UpdateGrantDTO;
import io.unityfoundation.dds.permissions.manager.model.grantduration.GrantDuration;
import io.unityfoundation.dds.permissions.manager.model.grantduration.GrantDurationRepository;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class ApplicationGrantService {

    private final ApplicationGrantRepository applicationGrantRepository;
    private final ApplicationRepository applicationRepository;
    private final ActionService actionService;
    private final GroupRepository groupRepository;
    private final GrantDurationRepository grantDurationRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;
    private final JwtTokenValidator jwtTokenValidator;

    public ApplicationGrantService(ApplicationGrantRepository applicationGrantRepository, ApplicationRepository applicationRepository, ActionService actionService, GroupRepository groupRepository, GrantDurationRepository grantDurationRepository, SecurityUtil securityUtil, GroupUserService groupUserService, JwtTokenValidator jwtTokenValidator) {
        this.applicationGrantRepository = applicationGrantRepository;
        this.applicationRepository = applicationRepository;
        this.actionService = actionService;
        this.groupRepository = groupRepository;
        this.grantDurationRepository = grantDurationRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
        this.jwtTokenValidator = jwtTokenValidator;
    }

    public Page<GrantDTO> findAll(Pageable pageable, String filter, Long group) {
        if (!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        Page<ApplicationGrant> page = getApplicationGrantsPage(pageable, filter, group);

        return page.map(this::createDTO);
    }

    private Page<ApplicationGrant> getApplicationGrantsPage(Pageable pageable, String filter, Long groupId) {
        List<Long> all;
        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                if (groupId == null) {
                    return applicationGrantRepository.findAll(pageable);
                }
                return applicationGrantRepository.findAllByPermissionsGroupIdIn(List.of(groupId), pageable);
            }

            if (groupId == null) {
                return applicationGrantRepository.findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter, pageable);
            }

            all = applicationGrantRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);

            return applicationGrantRepository.findAllByIdInAndPermissionsGroupIdIn(all, List.of(groupId), pageable);
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
                return applicationGrantRepository.findAllByPermissionsGroupIdIn(groups, pageable);
            }

            all = applicationGrantRepository.findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(filter, filter);
            if (all.isEmpty()) {
                return Page.empty();
            }

            return applicationGrantRepository.findAllByIdInAndPermissionsGroupIdIn(all, groups, pageable);
        }
    }

    public Page<DetailedGrantDTO> findAllByApplicationId(Pageable pageable, Long applicationId) {
        if (!pageable.isSorted()) {
            pageable = pageable.order("name").order("permissionsGroup.name");
        }

        Page<ApplicationGrant> page = getApplicationGrantsPageByApplication(pageable, applicationId);

        return page.map(this::createDetailedDTO);
    }

    private Page<ApplicationGrant> getApplicationGrantsPageByApplication(Pageable pageable, Long applicationId) {

        if (securityUtil.isCurrentUserAdmin()) {
            return applicationGrantRepository.findByPermissionsApplicationId(applicationId, pageable);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            List<Long> groups = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());

            if (groups.isEmpty()) {
                return Page.empty();
            }

            return applicationGrantRepository.findAllByPermissionsApplicationIdAndPermissionsGroupIdIn(applicationId, groups, pageable);
        }
    }

    public GrantDTO findById(Long grantId) {
        Optional<ApplicationGrant> grantOptional = applicationGrantRepository.findById(grantId);

        if (grantOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !groupUserService.isUserMemberOfGroup(grantOptional.get().getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }

        return createDTO(grantOptional.get());
    }

    public Publisher<HttpResponse<GrantDTO>> create(String grantToken, CreateGrantDTO createGrantDTO) {
        return Publishers.map(jwtTokenValidator.validateToken(grantToken, null), authentication -> {
            JWT jwt;
            JwtClaims claims;
            try {
                jwt = JWTParser.parse(grantToken);
                claims = new JwtClaimsSetAdapter(jwt.getJWTClaimsSet());
                if (claims.get(JwtClaims.SUBJECT) != null) {
                    Long applicationId = Long.valueOf((String) claims.get(JwtClaims.SUBJECT));
                    return create(applicationId, createGrantDTO);
                } else {
                    throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_TOKEN_PARSE_EXCEPTION, HttpStatus.BAD_REQUEST);
                }
            } catch (ParseException e) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_TOKEN_PARSE_EXCEPTION, HttpStatus.BAD_REQUEST);
            }
        });
    }

    public HttpResponse<GrantDTO> create(Long applicationId, CreateGrantDTO createGrantDTO) {

        Optional<Group> groupOptional = groupRepository.findById(createGrantDTO.getGroupId());
        if (groupOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_GROUP_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Optional<GrantDuration> grantDurationOptional = grantDurationRepository.findById(createGrantDTO.getGrantDurationId());
        if (grantDurationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_GRANT_DURATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Optional<Application> applicationById = applicationRepository.findById(applicationId);
        if (applicationById.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Optional<ApplicationGrant> searchGrantByNameAndGroup = applicationGrantRepository.findByNameAndPermissionsGroup(
                createGrantDTO.getName().trim(), groupOptional.get());
        if (searchGrantByNameAndGroup.isPresent() ) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_ALREADY_EXISTS);
        }

        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        if (!securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isUserTopicAdminOfGroup(groupOptional.get().getId(), user.getId())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        ApplicationGrant newGrant = applicationGrantRepository.save(new ApplicationGrant(
                createGrantDTO.getName().trim(),
                applicationById.get(),
                groupOptional.get(),
                grantDurationOptional.get()
        ));

        GrantDTO dto = createDTO(newGrant);
        return HttpResponse.created(dto);
    }

    public GrantDTO createDTO(ApplicationGrant applicationGrant) {
        return new GrantDTO(
                applicationGrant.getId(),
                applicationGrant.getName(),
                applicationGrant.getPermissionsApplication().getId(),
                applicationGrant.getPermissionsApplication().getName(),
                applicationGrant.getPermissionsApplication().getPermissionsGroup().getName(),
                applicationGrant.getPermissionsGroup().getId(),
                applicationGrant.getPermissionsGroup().getName(),
                applicationGrant.getGrantDuration().getDurationInMilliseconds(),
                applicationGrant.getGrantDuration().getDurationMetadata()
        );
    }

    public DetailedGrantDTO createDetailedDTO(ApplicationGrant applicationGrant) {
        List<Action> actions = actionService.getAllByGrantId(applicationGrant.getId());
        List <ActionDTO> actionDTOs = actions.stream().map(actionService::createDTO).collect(Collectors.toList());

        return new DetailedGrantDTO(
                applicationGrant.getId(),
                applicationGrant.getName(),
                applicationGrant.getPermissionsApplication().getId(),
                applicationGrant.getPermissionsApplication().getName(),
                applicationGrant.getPermissionsApplication().getPermissionsGroup().getName(),
                applicationGrant.getPermissionsGroup().getId(),
                applicationGrant.getPermissionsGroup().getName(),
                applicationGrant.getGrantDuration().getDurationInMilliseconds(),
                applicationGrant.getGrantDuration().getDurationMetadata(),
                actionDTOs
        );
    }

    public HttpResponse deleteById(Long grantId) {

        Optional<ApplicationGrant> applicationGrantOptional = applicationGrantRepository.findById(grantId);

        if (applicationGrantOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            Long groupId = applicationGrantOptional.get().getPermissionsGroup().getId();
            Long applicationGroupId = applicationGrantOptional.get().getPermissionsApplication().getPermissionsGroup().getId();

            boolean isAuthorized = securityUtil.isCurrentUserAdmin() ||
                    groupUserService.isUserTopicAdminOfGroup(groupId, user.getId()) ||
                    groupUserService.isUserApplicationAdminOfGroup(applicationGroupId, user.getId());
            if (!isAuthorized) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }

        actionService.deleteByApplicationGrantId(grantId);
        applicationGrantRepository.deleteById(grantId);
        return HttpResponse.noContent();
    }

    public HttpResponse<GrantDTO> update(Long grantId, UpdateGrantDTO grantDTO) {

        Optional<ApplicationGrant> applicationGrantOptional = applicationGrantRepository.findById(grantId);

        if (applicationGrantOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        User user = securityUtil.getCurrentlyAuthenticatedUser().get();
        if (!securityUtil.isCurrentUserAdmin() &&
                !groupUserService.isUserTopicAdminOfGroup(applicationGrantOptional.get().getPermissionsGroup().getId(), user.getId())) {
            throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }

        // check if application with same name exists in group
        Optional<ApplicationGrant> searchGrantByNameAndGroup = applicationGrantRepository.findByNameAndPermissionsGroup(
                grantDTO.getName().trim(), applicationGrantOptional.get().getPermissionsGroup());
        if (searchGrantByNameAndGroup.isPresent() &&
                searchGrantByNameAndGroup.get().getId() != applicationGrantOptional.get().getId()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_ALREADY_EXISTS);
        }

        Optional<GrantDuration> grantDurationOptional = grantDurationRepository.findById(grantDTO.getGrantDurationId());
        if (grantDurationOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_GRANT_DURATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        ApplicationGrant applicationGrant = applicationGrantOptional.get();
        applicationGrant.setName(grantDTO.getName().trim());
        applicationGrant.setGrantDuration(grantDurationOptional.get());

        return HttpResponse.ok(createDTO(applicationGrantRepository.update(applicationGrant)));
    }

    public void deleteAllByApplication(Application application) {
        applicationGrantRepository.deleteByPermissionsApplicationEquals(application);
    }
}

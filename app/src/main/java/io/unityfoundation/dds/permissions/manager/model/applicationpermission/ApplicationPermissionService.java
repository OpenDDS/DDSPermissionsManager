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
package io.unityfoundation.dds.permissions.manager.model.applicationpermission;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.security.token.Claims;
import io.micronaut.security.token.jwt.generator.claims.JwtClaimsSetAdapter;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ApplicationPermissionService {

    public static final String APPLICATION_GRANT_TOKEN = "APPLICATION_GRANT_TOKEN";

    private final ApplicationPermissionRepository applicationPermissionRepository;
    private final ApplicationRepository applicationRepository;
    private final TopicRepository topicRepository;
    private final ReadPartitionRepository readPartitionRepository;
    private final WritePartitionRepository writePartitionRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;
    private final JwtTokenValidator jwtTokenValidator;

    public ApplicationPermissionService(ApplicationPermissionRepository applicationPermissionRepository,
                                        ApplicationRepository applicationRepository, TopicRepository topicRepository,
                                        ReadPartitionRepository readPartitionRepository, WritePartitionRepository writePartitionRepository,
                                        SecurityUtil securityUtil, GroupUserService groupUserService, JwtTokenValidator jwtTokenValidator) {
        this.applicationPermissionRepository = applicationPermissionRepository;
        this.applicationRepository = applicationRepository;
        this.topicRepository = topicRepository;
        this.readPartitionRepository = readPartitionRepository;
        this.writePartitionRepository = writePartitionRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
        this.jwtTokenValidator = jwtTokenValidator;
    }

    public Page<AccessPermissionDTO> indexByTopicId(Long topicId, Pageable pageable) {
        Page<ApplicationPermission> page;
        boolean isTopic = true;
        boolean publicMode = determinePublicMode(isTopic, topicId);

        if (publicMode) {
            page = getPublicApplicationPermissionsPage(isTopic, topicId, pageable);
        } else {
            page = getApplicationPermissionsPage(isTopic, topicId, pageable);
        }

        return getAccessPermissionDTOPage(page, publicMode);
    }

    public Page<AccessPermissionDTO> indexByApplicationId(Long applicationId, Pageable pageable) {
        Page<ApplicationPermission> page;
        boolean isTopic = false;
        boolean publicMode = determinePublicMode(isTopic, applicationId);

        if (!pageable.isSorted()) {
            pageable = pageable.order("permissionsTopic.name");
        }

        if (publicMode) {
            page = getPublicApplicationPermissionsPage(isTopic, applicationId, pageable);
        } else {
            page = getApplicationPermissionsPage(isTopic, applicationId, pageable);
        }

        return getAccessPermissionDTOPage(page, publicMode);
    }

    private static Page<AccessPermissionDTO> getAccessPermissionDTOPage(Page<ApplicationPermission> page, boolean publicMode) {
        return page.map(applicationPermission -> new AccessPermissionDTO(
                applicationPermission.getId(),
                applicationPermission.getPermissionsTopic().getId(),
                applicationPermission.getPermissionsTopic().getName(),
                applicationPermission.getPermissionsTopic().deriveCanonicalName(),
                applicationPermission.getPermissionsTopic().getPermissionsGroup().getName(),
                applicationPermission.getPermissionsApplication().getId(),
                applicationPermission.getPermissionsApplication().getName(),
                applicationPermission.getPermissionsApplication().getPermissionsGroup().getName(),
                applicationPermission.isPermissionRead(),
                applicationPermission.isPermissionWrite(),
                publicMode? Set.of() : applicationPermission.getReadPartitions().stream().map(ReadPartition::getPartitionName).collect(Collectors.toSet()),
                publicMode? Set.of() : applicationPermission.getWritePartitions().stream().map(WritePartition::getPartitionName).collect(Collectors.toSet())
        ));
    }

    private boolean determinePublicMode(boolean isTopic, Long entityId) {
        if (securityUtil.isCurrentUserAdmin()) {
            return false;
        }

        if (isTopic) {
            Optional<Topic> topicOptional = topicRepository.findById(entityId);
            if (topicOptional.isEmpty()) {
                throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
            }

            return !groupUserService.isCurrentUserMemberOfGroup(entityId) && topicOptional.get().getMakePublic();
        } else {
            Optional<Application> applicationOptional = applicationRepository.findById(entityId);
            if (applicationOptional.isEmpty()) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
            }

            return !groupUserService.isCurrentUserMemberOfGroup(entityId) && applicationOptional.get().getMakePublic();
        }
    }

    private Page<ApplicationPermission> getApplicationPermissionsPage(boolean isTopic, Long entityId, Pageable pageable) {
        if (securityUtil.isCurrentUserAdmin()) {
            if (isTopic) {
                return applicationPermissionRepository.findByPermissionsTopicId(entityId, pageable);
            } else {
                return applicationPermissionRepository.findByPermissionsApplicationId(entityId, pageable);
            }
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            List<Long> groups = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());
            if (groups.isEmpty()) {
                return Page.empty();
            }

            List<Long> groupsApplications = applicationRepository.findIdByPermissionsGroupIdIn(groups);
            List<Long> groupsTopics = topicRepository.findIdByPermissionsGroupIdIn(groups);

            if (isTopic) {
                return applicationPermissionRepository.findByPermissionsTopicIdAndPermissionsTopicIdIn(entityId, groupsTopics, pageable);
            } else {
                return applicationPermissionRepository.findByPermissionsApplicationIdAndPermissionsApplicationIdIn(entityId, groupsApplications, pageable);
            }
        }
    }

    private Page<ApplicationPermission> getPublicApplicationPermissionsPage(boolean isTopic, Long entityId, Pageable pageable) {
        if (isTopic) {
            return applicationPermissionRepository.findByPermissionsApplicationMakePublicTrueAndPermissionsTopicMakePublicTrueAndPermissionsTopicId(entityId, pageable);
        } else {
            return applicationPermissionRepository.findByPermissionsApplicationMakePublicTrueAndPermissionsTopicMakePublicTrueAndPermissionsApplicationId(entityId, pageable);
        }
    }

    public Publisher<HttpResponse<AccessPermissionDTO>> addAccess(String grantToken, Long topicId, AccessPermissionBodyDTO accessPermissionBodyDTO) {
        return Publishers.map(jwtTokenValidator.validateToken(grantToken, null), authentication -> {
            JWT jwt;
            Claims claims;
            try {
                jwt = JWTParser.parse(grantToken);
                claims = new JwtClaimsSetAdapter(jwt.getJWTClaimsSet());
                if (claims.get(Claims.SUBJECT) != null) {
                    Long applicationId = Long.valueOf((String) claims.get(Claims.SUBJECT));
                    return addAccess(applicationId, topicId, accessPermissionBodyDTO);
                } else {
                    throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_TOKEN_PARSE_EXCEPTION, HttpStatus.BAD_REQUEST);
                }
            } catch (ParseException e) {
                throw new DPMException(ResponseStatusCodes.APPLICATION_GRANT_TOKEN_PARSE_EXCEPTION, HttpStatus.BAD_REQUEST);
            }
        });
    }

    public HttpResponse<AccessPermissionDTO> addAccess(Long applicationId, Long topicId, AccessPermissionBodyDTO accessPermissionBodyDTO) {
        final HttpResponse response;

        Optional<Application> applicationById = applicationRepository.findById(applicationId);
        if (applicationById.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            Optional<Topic> topicById = topicRepository.findById(topicId);
            if (topicById.isEmpty()) {
                throw new DPMException(ResponseStatusCodes.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND);
            } else {
                Topic topic = topicById.get();

                User user = securityUtil.getCurrentlyAuthenticatedUser().get();
                if (!securityUtil.isCurrentUserAdmin() &&
                        !groupUserService.isUserTopicAdminOfGroup(topic.getPermissionsGroup().getId(), user.getId())) {
                    throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
                } else {
                    Application application = applicationById.get();
                    response = addAccess(application, topic, accessPermissionBodyDTO);
                }
            }
        }

        return response;
    }

    public HttpResponse addAccess(Application application, Topic topic, AccessPermissionBodyDTO accessPermissionBodyDTO) {
        ApplicationPermission newPermission = saveNewPermission(application, topic, accessPermissionBodyDTO);
        AccessPermissionDTO dto = createDTO(newPermission);
        return HttpResponse.created(dto);
    }

    public ApplicationPermission saveNewPermission(Application application, Topic topic, AccessPermissionBodyDTO accessPermissionBodyDTO) {
        if (applicationPermissionRepository.existsByPermissionsApplicationAndPermissionsTopic(application, topic)) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_PERMISSION_ALREADY_EXISTS);
        }
        ApplicationPermission applicationPermission = applicationPermissionRepository.save(
                new ApplicationPermission(application, topic, accessPermissionBodyDTO.isRead(), accessPermissionBodyDTO.isWrite())
        );
        addPartitionsToPermission(accessPermissionBodyDTO, applicationPermission);

        return applicationPermissionRepository.update(applicationPermission);
    }

    public AccessPermissionDTO createDTO(ApplicationPermission applicationPermission) {
        Long topicId = applicationPermission.getPermissionsTopic().getId();
        String topicName = applicationPermission.getPermissionsTopic().getName();
        String topicCanonicalName = applicationPermission.getPermissionsTopic().deriveCanonicalName();
        String topicGroup = applicationPermission.getPermissionsTopic().getPermissionsGroup().getName();
        Long applicationId = applicationPermission.getPermissionsApplication().getId();
        String applicationName = applicationPermission.getPermissionsApplication().getName();
        String applicationGroupName = applicationPermission.getPermissionsApplication().getPermissionsGroup().getName();
        boolean permissionRead = applicationPermission.isPermissionRead();
        boolean permissionWrite = applicationPermission.isPermissionWrite();
        Set<String> readPartitions = applicationPermission.getReadPartitions().stream().map(ReadPartition::getPartitionName).collect(Collectors.toSet());
        Set<String> writePartitions = applicationPermission.getWritePartitions().stream().map(WritePartition::getPartitionName).collect(Collectors.toSet());
        return new AccessPermissionDTO(
                applicationPermission.getId(),
                topicId,
                topicName,
                topicCanonicalName,
                topicGroup,
                applicationId,
                applicationName,
                applicationGroupName,
                permissionRead,
                permissionWrite,
                readPartitions,
                writePartitions
        );
    }

    public HttpResponse deleteById(Long permissionId) {

        Optional<ApplicationPermission> applicationPermissionOptional = applicationPermissionRepository.findById(permissionId);

        if (applicationPermissionOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_PERMISSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            Topic topic = applicationPermissionOptional.get().getPermissionsTopic();
            Application application = applicationPermissionOptional.get().getPermissionsApplication();
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() &&
                    !(
                            groupUserService.isUserTopicAdminOfGroup(topic.getPermissionsGroup().getId(), user.getId()) ||
                            groupUserService.isUserApplicationAdminOfGroup(application.getPermissionsGroup().getId(), user.getId())
                    )) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }

        applicationPermissionRepository.deleteById(permissionId);
        return HttpResponse.noContent();
    }

    public HttpResponse<AccessPermissionDTO> updateAccess(Long permissionId, AccessPermissionBodyDTO accessPermissionBodyDTO) {

        Optional<ApplicationPermission> applicationPermissionOptional = applicationPermissionRepository.findById(permissionId);

        if (applicationPermissionOptional.isEmpty()) {
            throw new DPMException(ResponseStatusCodes.APPLICATION_PERMISSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else {
            Topic topic = applicationPermissionOptional.get().getPermissionsTopic();
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();

            if (!securityUtil.isCurrentUserAdmin() && !groupUserService.isUserTopicAdminOfGroup(topic.getPermissionsGroup().getId(), user.getId())) {
                throw new DPMException(ResponseStatusCodes.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            }
        }

        ApplicationPermission applicationPermission = applicationPermissionOptional.get();
        applicationPermission.setPermissionRead(accessPermissionBodyDTO.isRead());
        applicationPermission.setPermissionWrite(accessPermissionBodyDTO.isWrite());

        readPartitionRepository.deleteAll(applicationPermission.getReadPartitions());
        writePartitionRepository.deleteAll(applicationPermission.getWritePartitions());
        addPartitionsToPermission(accessPermissionBodyDTO, applicationPermission);

        return HttpResponse.ok(createDTO(applicationPermissionRepository.update(applicationPermission)));
    }

    private void addPartitionsToPermission(AccessPermissionBodyDTO accessPermissionBodyDTO, ApplicationPermission applicationPermission) {
        Set<String> readPartitions = accessPermissionBodyDTO.getReadPartitions();
        Set<String> writePartitions = accessPermissionBodyDTO.getWritePartitions();
        if (readPartitions != null) {
            applicationPermission.setReadPartitions(readPartitions.stream()
                    .map(r -> readPartitionRepository.save(new ReadPartition(applicationPermission, r)))
                    .collect(Collectors.toSet()));
        }
        if (writePartitions != null) {
            applicationPermission.setWritePartitions(writePartitions.stream()
                    .map(w -> writePartitionRepository.save(new WritePartition(applicationPermission, w)))
                    .collect(Collectors.toSet()));
        }
    }

    public void deleteAllByTopic(Topic topic) {
        applicationPermissionRepository.deleteByPermissionsTopicEquals(topic);
    }
    public void deleteAllByApplication(Application application) {
        applicationPermissionRepository.deleteByPermissionsApplicationEquals(application);
    }

    public List<ApplicationPermission> findAllByApplicationAndReadEqualsTrue(Application application) {
        return applicationPermissionRepository.findByPermissionsApplicationAndPermissionReadTrue(application);
    }

    public List<ApplicationPermission> findAllByApplicationAndWriteEqualsTrue(Application application) {
        return applicationPermissionRepository.findByPermissionsApplicationAndPermissionWriteTrue(application);
    }
}

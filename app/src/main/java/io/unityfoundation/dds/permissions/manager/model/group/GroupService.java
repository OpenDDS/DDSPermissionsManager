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
package io.unityfoundation.dds.permissions.manager.model.group;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.unityfoundation.dds.permissions.manager.ResponseStatusCodes;
import io.unityfoundation.dds.permissions.manager.exception.DPMException;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class GroupService {

    private final GroupRepository groupRepository;
    private final ApplicationRepository applicationRepository;
    private final TopicRepository topicRepository;
    private final ApplicationPermissionRepository applicationPermissionRepository;
    private final SecurityUtil securityUtil;
    private final GroupUserService groupUserService;


    public GroupService(GroupRepository groupRepository, ApplicationRepository applicationRepository, TopicRepository topicRepository, ApplicationPermissionRepository applicationPermissionRepository, SecurityUtil securityUtil,
                        GroupUserService groupUserService) {
        this.groupRepository = groupRepository;
        this.applicationRepository = applicationRepository;
        this.topicRepository = topicRepository;
        this.applicationPermissionRepository = applicationPermissionRepository;
        this.securityUtil = securityUtil;
        this.groupUserService = groupUserService;
    }

    public Page<DetailedGroupDTO> findAll(Pageable pageable, String filter) {
        return getGroupPage(pageable, filter).map(group -> {
            DetailedGroupDTO groupsResponseDTO = new DetailedGroupDTO();
            groupsResponseDTO.setGroupFields(group);
            groupsResponseDTO.setTopics(group.getTopics().stream().map(Topic::getId).collect(Collectors.toSet()));
            groupsResponseDTO.setApplications(group.getApplications().stream().map(Application::getId).collect(Collectors.toSet()));
            groupsResponseDTO.setTopicCount(group.getTopics().size());
            groupsResponseDTO.setApplicationCount(group.getApplications().size());
            groupsResponseDTO.setGrantCount(group.getApplicationGrants().size());
            groupsResponseDTO.setMembershipCount(groupUserService.getMembershipCountByGroup(group));

            return groupsResponseDTO;
        });
    }

    private Page<Group> getGroupPage(Pageable pageable, String filter) {
        if (!pageable.isSorted()) {
            pageable = pageable.order(Sort.Order.asc("name"));
        }

        if (securityUtil.isCurrentUserAdmin()) {
            if (filter == null) {
                return groupRepository.findAll(pageable);
            }

            return groupRepository.findAllByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCase(filter, filter, pageable);
        } else {
            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            List<Long> groupsList = groupUserService.getAllGroupsUserIsAMemberOf(user.getId());
            if (groupsList.isEmpty()) {
                return Page.empty();
            }

            if (filter == null) {
                return groupRepository.findAllByIdIn(groupsList, pageable);
            }

            List<Long> searchByNameOrDescription = groupRepository.findIdByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCase(filter, filter);
            List<Long> intersection = searchByNameOrDescription.stream()
                    .distinct()
                    .filter(groupsList::contains)
                    .collect(Collectors.toList());

            return groupRepository.findAllByIdIn(intersection, pageable);
        }
    }

    public MutableHttpResponse<?> save(SimpleGroupDTO groupRequestDTO) {

        Optional<Group> searchGroupByName = groupRepository.findByName(groupRequestDTO.getName().trim());
        boolean isPublic = Boolean.TRUE.equals(groupRequestDTO.getPublic());

        Group group;
        if (groupRequestDTO.getId() == null) {
            if (!securityUtil.isCurrentUserAdmin()) {
                return HttpResponse.unauthorized();
            }
            if (searchGroupByName.isPresent()) {
                throw new DPMException(ResponseStatusCodes.GROUP_ALREADY_EXISTS);
            }

            group = groupRepository.save(new Group(groupRequestDTO.getName(), groupRequestDTO.getDescription(), isPublic));
        } else {
            if (searchGroupByName.isPresent() && !searchGroupByName.get().getId().equals(groupRequestDTO.getId())) {
                return HttpResponse.badRequest("Group with same name already exists");
            }

            Optional<Group> groupById = groupRepository.findById(groupRequestDTO.getId());
            if (groupById.isEmpty()) {
                return HttpResponse.notFound();
            }
            if (!securityUtil.isCurrentUserAdmin() && !isUserGroupAdminOfGroup(groupById.get())) {
                return HttpResponse.unauthorized();
            }

            group = groupById.get();
            group.setName(groupRequestDTO.getName());
            group.setDescription(groupRequestDTO.getDescription());
            if (group.getMakePublic() && !isPublic) {
                cascadePrivate(group);
            }
            group.setMakePublic(isPublic);

            group = groupRepository.update(group);
        }

        return HttpResponse.ok(new SimpleGroupDTO(group.getId(), group.getName(), group.getDescription(), group.getMakePublic()));
    }

    private void cascadePrivate(Group group) {

        if (group.getApplications() != null) {
            group.getApplications().forEach(application -> {
                application.setMakePublic(false);
                applicationRepository.update(application);
            });
        }

        if (group.getTopics() != null) {
            group.getTopics().forEach(topic -> {
                topic.setMakePublic(false);
                topicRepository.update(topic);
            });
        }
    }

    public MutableHttpResponse<?> deleteById(Long id) {
        Optional<Group> groupOptional = groupRepository.findById(id);
        if (groupOptional.isEmpty()) {
            return HttpResponse.notFound("Group not found");
        }

        Group group = groupOptional.get();
        groupUserService.removeByGroup(group);
        applicationPermissionRepository.deleteByPermissionsApplicationIdIn(group.getApplications().stream().map(Application::getId).collect(Collectors.toList()));
        applicationPermissionRepository.deleteByPermissionsTopicIdIn(group.getTopics().stream().map(Topic::getId).collect(Collectors.toList()));
        groupRepository.deleteById(id);

        return HttpResponse.seeOther(URI.create("/api/groups"));
    }

    public boolean isUserGroupAdminOfGroup(Group group) {
        return securityUtil.getCurrentlyAuthenticatedUser()
                .map(user -> groupUserService.isUserGroupAdminOfGroup(group.getId(), user.getId()))
                .orElse(false);
    }

    public Page<SimpleGroupDTO> search(String filter, GroupAdminRole role, Pageable pageable) {
        return getGroupSearchPage(filter, role, pageable).map(group -> new SimpleGroupDTO(group.getId(), group.getName(), group.getDescription(), group.getMakePublic()));
    }

    private Page<Group> getGroupSearchPage(String filter, GroupAdminRole role, Pageable pageable) {

        if (securityUtil.isCurrentUserAdmin()) {
            if (!pageable.isSorted()) {
                pageable = pageable.order(Sort.Order.asc("name"));
            }

            return groupRepository.findAllByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCase(filter, filter, pageable);
        } else {
            if (!pageable.isSorted()) {
                pageable = pageable.order(Sort.Order.asc("permissionsGroup.name"));
            }

            User user = securityUtil.getCurrentlyAuthenticatedUser().get();
            return groupUserService.getAllGroupsUserIsAnAdminOf(user, filter, pageable, role);
        }
    }
}

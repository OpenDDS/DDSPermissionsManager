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

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.group.Group;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationGrantRepository extends PageableRepository<ApplicationGrant, Long> {
//    boolean existsByPermissionsApplicationAndPermissionsTopic(Application permissionsApplication, Topic permissionsTopic);
    Page<ApplicationGrant> findByPermissionsApplicationId(Long applicationId, Pageable pageable);
    Page<ApplicationGrant> findAllByPermissionsApplicationIdAndPermissionsGroupIdIn(Long applicationId, List<Long> groups, Pageable pageable);
    List<ApplicationGrant> findByPermissionsApplication(Application permissionsApplication);
    Page<ApplicationGrant> findByPermissionsApplicationIdAndPermissionsApplicationIdIn(Long applicationId, List<Long> groupsApplications, Pageable pageable);
    void deleteByPermissionsApplicationEquals(Application permissionsApplication);
    void deleteByPermissionsApplicationIdIn(Collection<Long> permissionsApplications);
    Page<ApplicationGrant> findAllByPermissionsGroupIdIn(List<Long> groupId, Pageable pageable);
    Page<ApplicationGrant> findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String name, String groupName, Pageable pageable);
    List<Long> findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String name, String groupName);
    Page<ApplicationGrant> findAllByIdInAndPermissionsGroupIdIn(List<Long> all, List<Long> groupId, Pageable pageable);
    Optional<ApplicationGrant> findByNameAndPermissionsGroup(String name, Group group);
    List<Long> findIdByPermissionsGroupIdIn(List<Long> groups);
}

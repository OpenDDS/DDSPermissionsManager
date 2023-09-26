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

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import io.unityfoundation.dds.permissions.manager.model.group.Group;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrantDurationRepository extends PageableRepository<GrantDuration, Long> {
    Page<GrantDuration> findAllByPermissionsGroupIdIn(List<Long> groupIds, Pageable pageable);

    Page<GrantDuration> findAllByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String name, String groupName, Pageable pageable);

    List<Long> findIdByNameContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String name, String groupName);

    Page<GrantDuration> findAllByIdInAndPermissionsGroupIdIn(List<Long> grandDurationIds, List<Long> groupIds, Pageable pageable);

    Optional<GrantDuration> findByNameAndPermissionsGroup(String name, Group group);
}
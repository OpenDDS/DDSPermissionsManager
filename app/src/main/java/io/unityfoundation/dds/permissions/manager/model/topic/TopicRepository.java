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
package io.unityfoundation.dds.permissions.manager.model.topic;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import io.unityfoundation.dds.permissions.manager.model.group.Group;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends PageableRepository<Topic, Long> {

    Page<Topic> findAllByPermissionsGroupIdIn(List<Long> groupIds, Pageable pageable);

    Page<Topic> findAllByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String topic, String topicDescription, String group, Pageable pageable);

    List<Long> findIdByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCaseOrPermissionsGroupNameContainsIgnoreCase(String topic,String topicDescription, String group);

    Page<Topic> findAllByIdInAndPermissionsGroupIdIn(List<Long> all, List<Long> groups, Pageable pageable);

    Optional<Topic> findByNameAndPermissionsGroup(@NotNull @NonNull String name,
                                                  @NotNull @NonNull Group group);

    List<Long> findIdByPermissionsGroupIdIn(List<Long> groups);

    Page<Topic> findAllByMakePublicTrue(Pageable pageable);

    List<Topic> findTop50ByMakePublicTrue();

    List<Long> findIdByNameContainsIgnoreCaseOrDescriptionContainsIgnoreCase(String topic, String topicDescription);

    Page<Topic> findByMakePublicTrueAndIdIn(List<Long> entityIds, Pageable pageable);

    List<Topic> findTop50ByMakePublicTrueAndIdIn(List<Long> secondEntityIds);

    Optional<Topic> findByIdAndPermissionsGroupId(Long id, Long groupId);
}

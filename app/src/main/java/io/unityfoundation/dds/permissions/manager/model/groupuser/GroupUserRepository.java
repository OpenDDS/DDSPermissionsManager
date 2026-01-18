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
package io.unityfoundation.dds.permissions.manager.model.groupuser;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Repository
public interface GroupUserRepository extends PageableRepository<GroupUser, Long> {

    int countByPermissionsGroupIdAndPermissionsUserIdAndGroupAdminTrue(@NotNull @NonNull Long groupId, @NotNull @NonNull Long userId);
    int countByPermissionsGroupIdAndPermissionsUserIdAndTopicAdminTrue(@NotNull @NonNull Long groupId, @NotNull @NonNull Long userId);
    int countByPermissionsGroupIdAndPermissionsUserIdAndApplicationAdminTrue(@NotNull @NonNull Long groupId, @NotNull @NonNull Long userId);

    void deleteAllByPermissionsUserId(@NotNull @NonNull Long userId);
    void deleteByPermissionsGroupId(Long groupId);

    List<GroupUser> findAllByPermissionsUserId(@NotNull @NonNull Long userId);

    Page<GroupUser> findAllByPermissionsGroupIdIn(@NotNull @NonNull List<Long> groupIds, Pageable pageable);

    Page<GroupUser> findAllByPermissionsGroupNameContainsIgnoreCaseOrPermissionsUserEmailContainsIgnoreCase(@NotNull @NonNull String name, @NotNull @NonNull String email, Pageable pageable);

    List<Long> findIdByPermissionsGroupNameContainsIgnoreCaseOrPermissionsUserEmailContainsIgnoreCase(String filter, String filter1);

    Page<GroupUser> findAllByIdInAndPermissionsGroupIdIn(List<Long> groupUsersList, List<Long> groupsList, Pageable pageable);

    // todo: fix to have desired effect
//    Page<GroupUser> findAllByPermissionsGroupNameContainsOrPermissionsUserEmailContainsAndPermissionsGroupIdIn(@NotNull @NonNull String name, @NotNull @NonNull String email, List<Long> groupsList, Pageable pageable);

    int countByPermissionsUserId(@NotNull @NonNull Long userId);
    int countByPermissionsUserIdAndPermissionsGroupIdNotEqual(@NotNull @NonNull Long userId, @NotNull @NonNull Long groupId);

    boolean existsByPermissionsGroupIdAndPermissionsUserId(@NotNull @NonNull Long groupId, @NotNull @NonNull Long userId);

    List<GroupUser> findAllByPermissionsGroupId(@NotNull @NonNull Long groupId);
    List<User> findPermissionsUserByPermissionsGroupIdAndPermissionsUserAdminFalse(Long permissionsGroup_id);
    int countByPermissionsGroup(Group group);

    Page<Group> findPermissionsGroupByPermissionsUserEqualsAndPermissionsGroupNameContainsIgnoreCaseAndGroupAdminTrue(User permissionsUser, String group, Pageable pageable);
    Page<Group> findPermissionsGroupByPermissionsUserEqualsAndPermissionsGroupNameContainsIgnoreCaseAndTopicAdminTrue(User permissionsUser, String group, Pageable pageable);
    Page<Group> findPermissionsGroupByPermissionsUserEqualsAndPermissionsGroupNameContainsIgnoreCaseAndApplicationAdminTrue(User permissionsUser, String group, Pageable pageable);

    List<User> findPermissionsUserByPermissionsGroupIdAndTopicAdminTrue(Long groupId);
    List<User> findPermissionsUserByPermissionsGroupIdAndApplicationAdminTrue(Long groupId);
}

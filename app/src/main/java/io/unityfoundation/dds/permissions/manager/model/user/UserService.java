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
package io.unityfoundation.dds.permissions.manager.model.user;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpResponse;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUser;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserService;
import io.unityfoundation.dds.permissions.manager.security.SecurityUtil;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class UserService {
    private final SecurityUtil securityUtil;
    private final UserRepository userRepository;
    private final GroupUserService groupUserService;
    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    public UserService(SecurityUtil securityUtil, UserRepository userRepository, GroupUserService groupUserService) {
        this.securityUtil = securityUtil;
        this.userRepository = userRepository;
        this.groupUserService = groupUserService;
    }

    @Transactional
    @NonNull
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Page<AdminDTO> findAll(Pageable pageable, String filter) {
        return getUsers(pageable, filter).map(user -> new AdminDTO(user.getId(), user.getEmail()));
    }

    private Page<User> getUsers(Pageable pageable, String filter) {
        if (!pageable.isSorted()) {
            pageable = pageable.order(Sort.Order.asc("email"));
        }

        if (filter == null) {
            return userRepository.findByAdminTrue(pageable);
        }

        return userRepository.findByAdminTrueAndEmailContainsIgnoreCase(filter, pageable);
    }

    public List<Long> getIdsOfUsersWhoShareGroupsWithCurrentUser() {
        return securityUtil.getCurrentlyAuthenticatedUser()
                .map(user -> {
                    Long currentUserId = user.getId();
                    List<Long> currentUsersGroupIds = groupUserService.getAllGroupsUserIsAMemberOf(currentUserId);
                    return currentUsersGroupIds.stream()
                            .map(groupUserService::getUsersOfGroup)
                            .flatMap(List::stream)
                            .map(GroupUser::getPermissionsUser)
                            .map(User::getId)
                            .collect(Collectors.toList());
                }).orElseGet(Collections::emptyList);
    }

    @Transactional
    public HttpResponse save(AdminDTO adminDTO) {
        Optional<User> userSearchByEmail = userRepository.findByEmail(adminDTO.getEmail());

        User user;
        if (userSearchByEmail.isEmpty()) {
            user = generateAdminFromDTO(adminDTO);
            user = userRepository.save(user);
            LOG.info(user.getEmail() + " is now a super admin");
        } else {
            user = userSearchByEmail.get();
            user.setAdmin(true);
            user = userRepository.update(user);
            LOG.info(user.getEmail() + " is now a super admin");
        }

        return HttpResponse.ok(new AdminDTO(user.getId(), user.getEmail()));
    }

    private User generateAdminFromDTO(AdminDTO adminDTO) {
        User user = new User();
        user.setId(adminDTO.getId());
        user.setEmail(adminDTO.getEmail());
        user.setAdmin(true);
        return user;
    }

    @Transactional
    public void deleteById(Long id) {
        removeUserFromGroups(id);
        userRepository.deleteById(id);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void removeUserFromGroups(Long userId) {
        groupUserService.removeUserFromAllGroups(userId);
    }

    @Transactional
    public boolean removeAdminPrivilegeById(Long id) {

        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            return false;
        }

        User user = userOptional.get();

        LOG.info(user.getEmail() + " is no longer a super admin");

        if (user.isAdmin() && groupUserService.countMembershipsByUserId(id) == 0) {
            userRepository.delete(user);
        } else {
            user.setAdmin(false);
            userRepository.update(user);
        }

        return true;
    }
}

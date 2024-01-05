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
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "permissions_group_user")
public class GroupUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER, cascade = CascadeType.DETACH)
    private Group permissionsGroup;

    @ManyToOne(optional = false, fetch = FetchType.EAGER, cascade = CascadeType.DETACH)
    private User permissionsUser;

    @NonNull
    private boolean groupAdmin = false;

    @NonNull
    private boolean topicAdmin = false;

    @NonNull
    private boolean applicationAdmin = false;

    public GroupUser() {
    }

    public GroupUser(@NonNull Group group, @NonNull User user) {
        this.permissionsGroup = group;
        this.permissionsUser = user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Group getPermissionsGroup() {
        return permissionsGroup;
    }

    public void setPermissionsGroup(Group permissionsGroup) {
        this.permissionsGroup = permissionsGroup;
    }

    public User getPermissionsUser() {
        return permissionsUser;
    }

    public void setPermissionsUser(User permissionsUser) {
        this.permissionsUser = permissionsUser;
    }

    public boolean isGroupAdmin() {
        return groupAdmin;
    }

    public void setGroupAdmin(boolean groupAdmin) {
        this.groupAdmin = groupAdmin;
    }

    public boolean isTopicAdmin() {
        return topicAdmin;
    }

    public void setTopicAdmin(boolean topicAdmin) {
        this.topicAdmin = topicAdmin;
    }

    public boolean isApplicationAdmin() {
        return applicationAdmin;
    }

    public void setApplicationAdmin(boolean applicationAdmin) {
        this.applicationAdmin = applicationAdmin;
    }
}

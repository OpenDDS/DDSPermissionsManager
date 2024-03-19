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

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class GroupUserResponseDTO {

    private Long id;
    private Long permissionsUser;
    private String permissionsUserEmail;
    private long permissionsGroup;
    private String permissionsGroupName;
    private boolean isGroupAdmin = false;
    private boolean isTopicAdmin = false;
    private boolean isApplicationAdmin = false;

    public GroupUserResponseDTO() {
    }

    public GroupUserResponseDTO(GroupUser member) {
        this.id = member.getId();
        this.permissionsUser = member.getPermissionsUser().getId();
        this.permissionsUserEmail = member.getPermissionsUser().getEmail();
        this.permissionsGroup = member.getPermissionsGroup().getId();
        this.permissionsGroupName = member.getPermissionsGroup().getName();
        this.isGroupAdmin = member.isGroupAdmin();
        this.isTopicAdmin = member.isTopicAdmin();
        this.isApplicationAdmin = member.isApplicationAdmin();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPermissionsUser() {
        return permissionsUser;
    }

    public void setPermissionsUser(Long permissionsUser) {
        this.permissionsUser = permissionsUser;
    }

    public long getPermissionsGroup() {
        return permissionsGroup;
    }

    public void setPermissionsGroup(long permissionsGroup) {
        this.permissionsGroup = permissionsGroup;
    }

    public boolean isGroupAdmin() {
        return isGroupAdmin;
    }

    public void setGroupAdmin(boolean groupAdmin) {
        isGroupAdmin = groupAdmin;
    }

    public boolean isTopicAdmin() {
        return isTopicAdmin;
    }

    public void setTopicAdmin(boolean topicAdmin) {
        isTopicAdmin = topicAdmin;
    }

    public boolean isApplicationAdmin() {
        return isApplicationAdmin;
    }

    public void setApplicationAdmin(boolean applicationAdmin) {
        isApplicationAdmin = applicationAdmin;
    }

    public String getPermissionsUserEmail() {
        return permissionsUserEmail;
    }

    public void setPermissionsUserEmail(String permissionsUserEmail) {
        this.permissionsUserEmail = permissionsUserEmail;
    }

    public String getPermissionsGroupName() {
        return permissionsGroupName;
    }

    public void setPermissionsGroupName(String permissionsGroupName) {
        this.permissionsGroupName = permissionsGroupName;
    }
}

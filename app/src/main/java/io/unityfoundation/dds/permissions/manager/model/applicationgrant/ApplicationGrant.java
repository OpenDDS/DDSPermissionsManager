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


import io.micronaut.core.annotation.NonNull;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.grantduration.GrantDuration;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "permissions_application_grant")
public class ApplicationGrant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @NotBlank
    @Size(min = 3)
    private String name;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Application permissionsApplication;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Group permissionsGroup;

    @NonNull
    @ManyToOne
    private GrantDuration grantDuration;


    public ApplicationGrant() {
    }

    public ApplicationGrant(String name, Application application, Group group, GrantDuration grantDuration) {
        this.name = name;
        this.permissionsApplication = application;
        this.permissionsGroup = group;
        this.grantDuration = grantDuration;

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    public Application getPermissionsApplication() {
        return permissionsApplication;
    }

    public void setPermissionsApplication(@NonNull Application permissionsApplication) {
        this.permissionsApplication = permissionsApplication;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public Group getPermissionsGroup() {
        return permissionsGroup;
    }

    public void setPermissionsGroup(@NonNull Group permissionsGroup) {
        this.permissionsGroup = permissionsGroup;
    }

    @NonNull
    public GrantDuration getGrantDuration() {
        return grantDuration;
    }

    public void setGrantDuration(@NonNull GrantDuration grantDuration) {
        this.grantDuration = grantDuration;
    }
}

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


import io.micronaut.core.annotation.NonNull;
import io.unityfoundation.dds.permissions.manager.model.group.Group;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

@Entity
@Table(name = "permissions_grant_duration")
public class GrantDuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @NotBlank
    @Size(min = 3)
    private String name;

    @NonNull
    @PositiveOrZero
    private Long durationInMilliseconds;

    private String durationMetadata;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Group permissionsGroup;


    public GrantDuration() {
    }

    public GrantDuration(@NonNull String name, @NonNull Group group) {
        this.name = name;
        this.permissionsGroup = group;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @PrePersist
    void trimName() {
        this.name = this.name.trim();
    }

    @NonNull
    public Group getPermissionsGroup() {
        return permissionsGroup;
    }

    public void setPermissionsGroup(@NonNull Group permissionsGroup) {
        this.permissionsGroup = permissionsGroup;
    }

    @NonNull
    public Long getDurationInMilliseconds() {
        return durationInMilliseconds;
    }

    public void setDurationInMilliseconds(@NonNull Long durationInMilliseconds) {
        this.durationInMilliseconds = durationInMilliseconds;
    }

    public String getDurationMetadata() {
        return durationMetadata;
    }

    public void setDurationMetadata(String durationMetadata) {
        this.durationMetadata = durationMetadata;
    }
}
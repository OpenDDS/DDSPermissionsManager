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
package io.unityfoundation.dds.permissions.manager.model.application;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "permissions_application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @NotBlank
    @Size(min = 3)
    private String name;

    @Nullable
    private String encryptedPassword;

    @Size(max = 4000)
    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean makePublic = false;

    @DateCreated
    private Instant dateCreated;

    @DateUpdated
    private Instant dateUpdated;

    @ManyToOne
    @JoinColumn(name = "permissions_group_id", nullable = false)
    private Group permissionsGroup;

    public Application() {
    }

    public Application(@NonNull String name, String description, Boolean isPublic) {
        this.name = name;
        this.description = description;
        this.makePublic = isPublic;
    }

    public Application(@NonNull String name, @NonNull Group permissionsGroup) {
        this.name = name;
        this.permissionsGroup = permissionsGroup;
    }

    public Application(@NonNull String name, @NonNull Group permissionsGroup, String description, Boolean isPublic) {
        this.name = name;
        this.description = description;
        this.makePublic = isPublic;
        this.permissionsGroup = permissionsGroup;
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

    public Group getPermissionsGroup() {
        return permissionsGroup;
    }

    public void setPermissionsGroup(Group permissionsGroup) {
        this.permissionsGroup = permissionsGroup;
    }

    @Nullable
    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(@Nullable String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    @PrePersist
    void trimName() {
        this.name = this.name.trim();
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public boolean getMakePublic() {
        return makePublic;
    }

    public void setMakePublic(boolean isPublic) {
        makePublic = isPublic;
    }

    public Instant getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Instant dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Instant getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Instant dateUpdated) {
        this.dateUpdated = dateUpdated;
    }
}

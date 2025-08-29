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
package io.unityfoundation.dds.permissions.manager.model.topicset;


import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "permissions_topic_set")
public class TopicSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @NotBlank
    @Size(min = 3)
    private String name;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Group permissionsGroup;

    @DateCreated
    private Instant dateCreated;

    @DateUpdated
    private Instant dateUpdated;


    public TopicSet() {
    }

    public TopicSet(@NonNull String name, @NonNull Group group) {
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

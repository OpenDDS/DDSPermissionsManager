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
package io.unityfoundation.dds.permissions.manager.model.actioninterval;


import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.unityfoundation.dds.permissions.manager.model.group.Group;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;

@Entity
@Table(name = "permissions_action_interval")
public class ActionInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "permissionsGroup")
//    @OnDelete(action = OnDeleteAction.CASCADE)
//    private Set<Topic> topics = new HashSet<>();

    @NonNull
    @NotBlank
    @Size(min = 3)
    private String name;

    @NonNull
    private Instant startDate;

    @NonNull
    private Instant endDate;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Group permissionsGroup;


    public ActionInterval() {
    }

    public ActionInterval(@NonNull String name, @NonNull Group group) {
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

    @NotNull
    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(@Nullable Instant startDate) {
        this.startDate = startDate;
    }

    @NotNull
    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(@Nullable Instant endDate) {
        this.endDate = endDate;
    }
}
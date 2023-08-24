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

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;

@Introspected
public class ApplicationDTO implements EntityDTO {

    private Long id;
    @NotBlank
    @Size(min = 3)
    private String name;
    @Size(max = 4000)
    private String description;
    private Boolean isPublic;
    @NotNull
    private Long group;
    private String groupName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant dateUpdated;

    public ApplicationDTO() {
    }

    public ApplicationDTO(Application app) {
        this.id = app.getId();
        this.name = app.getName();
        this.description = app.getDescription();
        this.isPublic = app.getMakePublic();
        this.dateCreated = app.getDateCreated();
        this.dateUpdated = app.getDateUpdated();
        this.group = app.getPermissionsGroup().getId();
        this.groupName = app.getPermissionsGroup().getName();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getGroup() {
        return group;
    }

    public void setGroup(Long group) {
        this.group = group;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
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

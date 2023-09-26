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
package io.unityfoundation.dds.permissions.manager.model.grantduration.dto;

import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Introspected
public class GrantDurationDTO implements EntityDTO {

    @NotBlank
    @Size(min = 3)
    private Long id;

    @NotNull
    private String name;
    private Long groupId;
    private String groupName;

    @NotNull
    private Long durationInMilliseconds;

    public GrantDurationDTO() {
    }

    public GrantDurationDTO(Long id, String name, Long groupId, String groupName, Long durationInMilliseconds) {
        this.id = id;
        this.name = name;
        this.groupId = groupId;
        this.groupName = groupName;
        this.durationInMilliseconds = durationInMilliseconds;
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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Long getDurationInMilliseconds() {
        return durationInMilliseconds;
    }

    public void setDurationInMilliseconds(Long durationInMilliseconds) {
        this.durationInMilliseconds = durationInMilliseconds;
    }
}

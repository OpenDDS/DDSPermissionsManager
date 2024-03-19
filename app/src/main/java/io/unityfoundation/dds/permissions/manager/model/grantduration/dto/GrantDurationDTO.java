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

import io.micronaut.serde.annotation.Serdeable;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Serdeable
public class GrantDurationDTO implements EntityDTO {

    @NotNull
    private Long id;

    @NotBlank
    @Size(min = 3)
    private String name;
    private Long groupId;
    private String groupName;

    @NotNull
    private Long durationInMilliseconds;

    private String durationMetadata;

    private List<String> admins;

    private Integer grantCount;

    public GrantDurationDTO() {
    }

    public GrantDurationDTO(Long id, String name, Long groupId, String groupName, Long durationInMilliseconds,
                            String durationMetadata, List<String> admins, Integer grantCount) {
        this.id = id;
        this.name = name;
        this.groupId = groupId;
        this.groupName = groupName;
        this.durationInMilliseconds = durationInMilliseconds;
        this.durationMetadata = durationMetadata;
        this.admins = admins;
        this.grantCount = grantCount;
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

    public String getDurationMetadata() {
        return durationMetadata;
    }

    public void setDurationMetadata(String durationMetadata) {
        this.durationMetadata = durationMetadata;
    }

    public List<String> getAdmins() {
        return admins;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins;
    }

    public Integer getGrantCount() {
        return grantCount;
    }

    public void setGrantCount(Integer grantCount) {
        this.grantCount = grantCount;
    }
}

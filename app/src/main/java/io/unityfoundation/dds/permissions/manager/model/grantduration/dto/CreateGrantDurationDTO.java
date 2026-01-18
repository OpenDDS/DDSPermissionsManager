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
import io.unityfoundation.dds.permissions.manager.model.grantduration.GrantDuration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Serdeable
public class CreateGrantDurationDTO implements EntityDTO {

    @NotBlank
    @Size(min = 3)
    private String name;

    @NotNull
    private Long groupId;

    @NotNull
    @PositiveOrZero
    private Long durationInMilliseconds;

    private String durationMetadata;


    public CreateGrantDurationDTO() {
    }

    public CreateGrantDurationDTO(GrantDuration grantDuration) {
        this.name = grantDuration.getName();
        this.groupId = grantDuration.getPermissionsGroup().getId();
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
}
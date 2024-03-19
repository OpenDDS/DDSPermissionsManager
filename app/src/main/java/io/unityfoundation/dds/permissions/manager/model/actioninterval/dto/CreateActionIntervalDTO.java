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
package io.unityfoundation.dds.permissions.manager.model.actioninterval.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionInterval;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Serdeable
public class CreateActionIntervalDTO implements EntityDTO {

    @NotBlank
    @Size(min = 3)
    private String name;

    @NotNull
    private Long groupId;

    @NotNull
    private Instant startDate;

    @NotNull
    private Instant endDate;


    public CreateActionIntervalDTO() {
    }

    public CreateActionIntervalDTO(ActionInterval actionInterval) {
        this.name = actionInterval.getName();
        this.groupId = actionInterval.getPermissionsGroup().getId();
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

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }
}
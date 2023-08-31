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
package io.unityfoundation.dds.permissions.manager.model.expirationpolicy.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.ExpirationPolicy;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;

@Introspected
public class CreateExpirationPolicyDTO implements EntityDTO {

    @NotBlank
    @Size(min = 3)
    private String name;

    @NotNull
    private Long groupId;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant refreshDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant endDate;


    public CreateExpirationPolicyDTO() {
    }

    public CreateExpirationPolicyDTO(ExpirationPolicy expirationPolicy) {
        this.name = expirationPolicy.getName();
        this.groupId = expirationPolicy.getPermissionsGroup().getId();
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

    public Instant getRefreshDate() {
        return refreshDate;
    }

    public void setRefreshDate(Instant refreshDate) {
        this.refreshDate = refreshDate;
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
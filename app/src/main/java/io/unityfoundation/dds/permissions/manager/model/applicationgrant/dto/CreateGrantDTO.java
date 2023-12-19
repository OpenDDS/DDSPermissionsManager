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
package io.unityfoundation.dds.permissions.manager.model.applicationgrant.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Serdeable
public class CreateGrantDTO implements EntityDTO {
    @NotBlank
    @Size(min = 3)
    private String name;

    @NotNull
    private Long groupId;

    @NotNull
    private Long grantDurationId;

    public CreateGrantDTO() {
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

    public Long getGrantDurationId() {
        return grantDurationId;
    }

    public void setGrantDurationId(Long grantDurationId) {
        this.grantDurationId = grantDurationId;
    }
}

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
package io.unityfoundation.dds.permissions.manager.model.action.dto;

import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import io.unityfoundation.dds.permissions.manager.model.action.Action;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import java.util.Set;

@Introspected
public class CreateActionDTO extends UpdateActionDTO {

    @NotNull
    private Long applicationGrantId;

    public CreateActionDTO() {
    }

    public Long getApplicationGrantId() {
        return applicationGrantId;
    }

    public void setApplicationGrantId(Long applicationGrantId) {
        this.applicationGrantId = applicationGrantId;
    }
}
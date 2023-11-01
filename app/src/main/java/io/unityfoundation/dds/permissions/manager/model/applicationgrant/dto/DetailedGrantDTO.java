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

import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import io.unityfoundation.dds.permissions.manager.model.action.dto.ActionDTO;

import java.util.List;

@Introspected
public class DetailedGrantDTO extends GrantDTO {

    List<ActionDTO> actions;

    public DetailedGrantDTO(Long id, String name, Long applicationId, String applicationName, String applicationGroupName,
                            Long groupId, String groupName, Long durationInMilliseconds, String durationMetadata,
                            List<ActionDTO> actions) {
        super(id, name, applicationId, applicationName, applicationGroupName, groupId, groupName, durationInMilliseconds, durationMetadata);
        this.actions = actions;
    }

    public List<ActionDTO> getActions() {
        return actions;
    }

    public void setActions(List<ActionDTO> actions) {
        this.actions = actions;
    }
}

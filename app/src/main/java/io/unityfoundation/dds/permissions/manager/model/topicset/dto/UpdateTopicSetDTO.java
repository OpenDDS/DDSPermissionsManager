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
package io.unityfoundation.dds.permissions.manager.model.topicset.dto;

import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSet;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Introspected
public class UpdateTopicSetDTO implements EntityDTO {

    @NotBlank
    @Size(min = 3)
    private String name;

    public UpdateTopicSetDTO() {
    }

    public UpdateTopicSetDTO(TopicSet topicSet) {
        this.name = topicSet.getName();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

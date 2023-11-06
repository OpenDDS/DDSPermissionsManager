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

import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Introspected
public class UpdateActionDTO {

    @NotNull
    private Long actionIntervalId;
    private Set<String> partitions = new HashSet<>();
    private Set<Long> topicSetIds = new HashSet<>();
    private Set<Long> topicIds = new HashSet<>();

    public Long getActionIntervalId() {
        return actionIntervalId;
    }

    public void setActionIntervalId(Long actionIntervalId) {
        this.actionIntervalId = actionIntervalId;
    }

    public Set<String> getPartitions() {
        return partitions;
    }

    public void setPartitions(Set<String> partitions) {
        this.partitions = partitions;
    }

    public Set<Long> getTopicSetIds() {
        return topicSetIds;
    }

    public void setTopicSetIds(Set<Long> topicSetIds) {
        this.topicSetIds = topicSetIds;
    }

    public Set<Long> getTopicIds() {
        return topicIds;
    }

    public void setTopicIds(Set<Long> topicIds) {
        this.topicIds = topicIds;
    }
}

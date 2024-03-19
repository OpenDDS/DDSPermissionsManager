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
package io.unityfoundation.dds.permissions.manager.model.applicationpermission;

import io.micronaut.serde.annotation.Serdeable;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;

import java.util.Set;

@Serdeable
public class AccessPermissionDTO implements EntityDTO {
    private final Long topicId;
    private final String topicName;
    private final String topicCanonicalName;
    private final String topicGroup;
    private final Long applicationId;
    private final String applicationName;
    private final String applicationGroupName;
    private final boolean read;
    private final boolean write;
    private final Set<String> readPartitions;
    private final Set<String> writePartitions;
    private final Long id;

    public AccessPermissionDTO(Long id, Long topicId, String topicName, String topicCanonicalName, String topicGroup, Long applicationId, String applicationName, String applicationGroupName, boolean read, boolean write, Set<String> readPartitions, Set<String> writePartitions) {
        this.id = id;
        this.topicId = topicId;
        this.topicName = topicName;
        this.topicCanonicalName = topicCanonicalName;
        this.topicGroup = topicGroup;
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.applicationGroupName = applicationGroupName;
        this.read = read;
        this.write = write;
        this.readPartitions = readPartitions;
        this.writePartitions = writePartitions;
    }

    public Long getTopicId() {
        return topicId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isWrite() {
        return write;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getApplicationGroupName() {
        return applicationGroupName;
    }

    public String getTopicGroup() {
        return topicGroup;
    }

    public String getTopicCanonicalName() {
        return topicCanonicalName;
    }

    public Set<String> getReadPartitions() {
        return readPartitions;
    }

    public Set<String> getWritePartitions() {
        return writePartitions;
    }
}

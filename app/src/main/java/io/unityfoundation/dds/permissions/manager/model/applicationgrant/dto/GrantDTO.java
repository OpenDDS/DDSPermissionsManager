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

import java.util.List;

@Serdeable
public class GrantDTO implements EntityDTO {
    private final Long id;
    private final String name;
    private final Long applicationId;
    private final String applicationName;
    private final String applicationGroupName;
    private final Long groupId;
    private final String groupName;
    private final Long durationInMilliseconds;
    private final String durationMetadata;
    private List<String> admins;

    public GrantDTO(Long id, String name, Long applicationId, String applicationName, String applicationGroupName, Long groupId, String groupName, Long durationInMilliseconds, String durationMetadata) {
        this.id = id;
        this.name = name;
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.applicationGroupName = applicationGroupName;
        this.groupId = groupId;
        this.groupName = groupName;
        this.durationInMilliseconds = durationInMilliseconds;
        this.durationMetadata = durationMetadata;
    }


    public Long getApplicationId() {
        return applicationId;
    }


    public Long getId() {
        return id;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getApplicationGroupName() {
        return applicationGroupName;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getName() {
        return name;
    }

    public Long getDurationInMilliseconds() {
        return durationInMilliseconds;
    }

    public String getDurationMetadata() {
        return durationMetadata;
    }

    public List<String> getAdmins() {
        return admins;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins;
    }
}

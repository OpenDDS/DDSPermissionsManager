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

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionInterval;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

@Introspected
public class ActionIntervalDTO implements EntityDTO {

    @NotBlank
    @Size(min = 3)
    private Long id;

    @NotNull
    private String name;
    private Long groupId;
    private String groupName;
    private List<String> admins;
    private Integer actionCount;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startDate;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant endDate;

    public ActionIntervalDTO() {
    }

    public ActionIntervalDTO(Long id, String name, Long groupId, String groupName, Instant startDate, Instant endDate,
                             List<String> admins, Integer actionCount) {
        this.id = id;
        this.name = name;
        this.groupId = groupId;
        this.groupName = groupName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.admins = admins;
        this.actionCount = actionCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getGroupName() {
        return groupName;
    }

    public List<String> getAdmins() {
        return admins;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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

    public Integer getActionCount() {
        return actionCount;
    }

    public void setActionCount(Integer actionCount) {
        this.actionCount = actionCount;
    }
}

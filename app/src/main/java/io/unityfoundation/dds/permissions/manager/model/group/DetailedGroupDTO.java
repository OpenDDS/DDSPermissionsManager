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
package io.unityfoundation.dds.permissions.manager.model.group;

import io.micronaut.serde.annotation.Serdeable;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;

import java.util.Set;

@Serdeable
public class DetailedGroupDTO implements EntityDTO {

    private Long id;
    private String name;
    private String description;
    private Boolean isPublic;
    private Set<Long> topics;
    private Set<Long> applications;
    private int membershipCount;
    private int topicCount;
    private int applicationCount;
    private int grantCount;

    public DetailedGroupDTO() {
    }

    public void setGroupFields(Group group) {
        this.id = group.getId();
        this.name = group.getName();
        this.isPublic = group.getMakePublic();
        this.description = group.getDescription();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<Long> getTopics() {
        return topics;
    }

    public void setTopics(Set<Long> topics) {
        this.topics = topics;
    }

    public Set<Long> getApplications() {
        return applications;
    }

    public void setApplications(Set<Long> applications) {
        this.applications = applications;
    }

    public long getMembershipCount() {
        return membershipCount;
    }

    public void setMembershipCount(int membershipCount) {
        this.membershipCount = membershipCount;
    }

    public long getTopicCount() {
        return topicCount;
    }

    public void setTopicCount(int topicCount) {
        this.topicCount = topicCount;
    }

    public long getApplicationCount() {
        return applicationCount;
    }

    public void setApplicationCount(int applicationCount) {
        this.applicationCount = applicationCount;
    }

    public int getGrantCount() {
        return grantCount;
    }

    public void setGrantCount(int grantCount) {
        this.grantCount = grantCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
    }
}

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


import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.expirationpolicy.ExpirationPolicy;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions_group")
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @NotBlank
    @Size(min = 3)
    @Column(unique = true)
    private String name;

    @Size(max = 4000)
    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean makePublic = false;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "permissionsGroup")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<Topic> topics = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "permissionsGroup")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<Application> applications = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "permissionsGroup")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ExpirationPolicy> expirationPolicies = new HashSet<>();

    public Group() {
    }

    public Group(@NonNull String name) {
        this.name = name;
    }

    public Group(@NonNull String name, String description, Boolean isPublic) {
        this.name = name;
        this.description = description;
        this.makePublic = isPublic;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public Set<Topic> getTopics() {
        if (topics == null) return null;
        return Collections.unmodifiableSet(topics);
    }

    public void setTopics(Set<Topic> topics) {
        this.topics = topics;
    }

    public boolean removeTopic(Long topicId) {
        return topics.removeIf(topic -> topicId != null && topicId.equals(topic.getId()));
    }

    public void addTopic(Topic topic) {
        topics.add(topic);
    }

    public Set<Application> getApplications() {
        if (applications == null) return null;
        return Collections.unmodifiableSet(applications);
    }

    public void setApplications(Set<Application> applications) {
        this.applications = applications;
    }

    public boolean removeApplication(Long applicationId) {
        return topics.removeIf(application -> applicationId != null && applicationId.equals(application.getId()));
    }

    public void addApplication(Application application) {
        applications.add(application);
    }

    public Set<ExpirationPolicy> getExpirationPolicies() {
        if (expirationPolicies == null) return null;
        return Collections.unmodifiableSet(expirationPolicies);
    }

    public void setExpirationPolicies(Set<ExpirationPolicy> expirationPolicies) {
        this.expirationPolicies = expirationPolicies;
    }

    public boolean removeExpirationPolicy(Long expirationPolicyId) {
        return expirationPolicies.removeIf(expirationPolicy -> expirationPolicyId != null && expirationPolicyId.equals(expirationPolicy.getId()));
    }

    public void addExpirationPolicy(ExpirationPolicy expirationPolicy) {
        expirationPolicies.add(expirationPolicy);
    }

    @PrePersist
    void trimName() {
        this.name = this.name.trim();
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public boolean getMakePublic() {
        return makePublic;
    }

    public void setMakePublic(boolean isPublic) {
        makePublic = isPublic;
    }
}

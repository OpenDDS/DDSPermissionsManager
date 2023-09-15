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
package io.unityfoundation.dds.permissions.manager.model.topicset;


import io.micronaut.core.annotation.NonNull;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions_topic_set")
public class TopicSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @NotBlank
    @Size(min = 3)
    private String name;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Group permissionsGroup;

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Topic> topics = new HashSet<>();


    public TopicSet() {
    }

    public TopicSet(@NonNull String name, @NonNull Group group) {
        this.name = name;
        this.permissionsGroup = group;
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

    @PrePersist
    void trimName() {
        this.name = this.name.trim();
    }

    @NonNull
    public Group getPermissionsGroup() {
        return permissionsGroup;
    }

    public void setPermissionsGroup(@NonNull Group permissionsGroup) {
        this.permissionsGroup = permissionsGroup;
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
}

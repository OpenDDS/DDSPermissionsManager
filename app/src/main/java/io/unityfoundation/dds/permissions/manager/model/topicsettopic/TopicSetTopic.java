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
package io.unityfoundation.dds.permissions.manager.model.topicsettopic;


import io.micronaut.core.annotation.NonNull;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSet;
import jakarta.persistence.*;

@Entity
@Table(name = "permissions_topic_set_topic")
public class TopicSetTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private TopicSet permissionsTopicSet;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Topic permissionsTopic;

    public TopicSetTopic() {
    }

    public TopicSetTopic(@NonNull TopicSet permissionsTopicSet, @NonNull Topic permissionsTopic) {
        this.permissionsTopicSet = permissionsTopicSet;
        this.permissionsTopic = permissionsTopic;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    public TopicSet getPermissionsTopicSet() {
        return permissionsTopicSet;
    }

    public void setPermissionsTopicSet(@NonNull TopicSet permissionsTopicSet) {
        this.permissionsTopicSet = permissionsTopicSet;
    }

    @NonNull
    public Topic getPermissionsTopic() {
        return permissionsTopic;
    }

    public void setPermissionsTopic(@NonNull Topic permissionsTopic) {
        this.permissionsTopic = permissionsTopic;
    }
}
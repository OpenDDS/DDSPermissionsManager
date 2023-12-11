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
package io.unityfoundation.dds.permissions.manager.model.actiontopic;


import io.micronaut.core.annotation.NonNull;
import io.unityfoundation.dds.permissions.manager.model.action.Action;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;

import javax.persistence.*;

@Entity
@Table(name = "permissions_action_topic")
public class ActionTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Action permissionsAction;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Topic permissionsTopic;

    public ActionTopic() {
    }

    public ActionTopic(@NonNull Action permissionsAction, @NonNull Topic permissionsTopic) {
        this.permissionsAction = permissionsAction;
        this.permissionsTopic = permissionsTopic;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    public Action getPermissionsAction() {
        return permissionsAction;
    }

    public void setPermissionsAction(@NonNull Action permissionsAction) {
        this.permissionsAction = permissionsAction;
    }

    @NonNull
    public Topic getPermissionsTopic() {
        return permissionsTopic;
    }

    public void setPermissionsTopic(@NonNull Topic permissionsTopic) {
        this.permissionsTopic = permissionsTopic;
    }
}
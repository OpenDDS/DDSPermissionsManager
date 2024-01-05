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


import io.micronaut.core.annotation.NonNull;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions_application_permission")
public class ApplicationPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Application permissionsApplication;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Topic permissionsTopic;

    private boolean permissionRead = false;

    private boolean permissionWrite = false;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "rApplicationPermission")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ReadPartition> readPartitions = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "wApplicationPermission")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<WritePartition> writePartitions = new HashSet<>();


    public ApplicationPermission() {
    }

    public ApplicationPermission(@NonNull Application application, @NonNull Topic topic, boolean permissionRead, boolean permissionWrite) {
        this.permissionsApplication = application;
        this.permissionsTopic = topic;
        this.permissionRead = permissionRead;
        this.permissionWrite = permissionWrite;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @NonNull
    public Application getPermissionsApplication() {
        return permissionsApplication;
    }

    public void setPermissionsApplication(@NonNull Application permissionsApplication) {
        this.permissionsApplication = permissionsApplication;
    }

    @NonNull
    public Topic getPermissionsTopic() {
        return permissionsTopic;
    }

    public void setPermissionsTopic(@NonNull Topic permissionsTopic) {
        this.permissionsTopic = permissionsTopic;
    }

    public boolean isPermissionRead() {
        return permissionRead;
    }

    public void setPermissionRead(boolean permissionRead) {
        this.permissionRead = permissionRead;
    }

    public boolean isPermissionWrite() {
        return permissionWrite;
    }

    public void setPermissionWrite(boolean permissionWrite) {
        this.permissionWrite = permissionWrite;
    }

    public Set<ReadPartition> getReadPartitions() {
        if (readPartitions == null) return null;
        return Collections.unmodifiableSet(readPartitions);
    }

    public void setReadPartitions(Set<ReadPartition> readReadPartitions) {
        this.readPartitions = readReadPartitions;
    }

    public boolean removeReadPartition(Long partitionId) {
        return readPartitions.removeIf(readPartition -> partitionId != null && partitionId.equals(readPartition.getId()));
    }

    public Set<WritePartition> getWritePartitions() {
        if (writePartitions == null) return null;
        return Collections.unmodifiableSet(writePartitions);
    }

    public void setWritePartitions(Set<WritePartition> writePartitions) {
        this.writePartitions = writePartitions;
    }

    public boolean removeWritePartition(Long partitionId) {
        return writePartitions.removeIf(writePartition -> partitionId != null && partitionId.equals(writePartition.getId()));
    }
}

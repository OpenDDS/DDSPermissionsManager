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

import javax.persistence.*;

@Entity
@Table(name = "permissions_partition")
public class ReadPartition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "permissions_application_permission_id")
    private ApplicationPermission rApplicationPermission;

    private String partitionName;

    public ReadPartition() {
    }

    public ReadPartition(ApplicationPermission applicationPermission, String partitionName) {
        this.rApplicationPermission = applicationPermission;
        this.partitionName = partitionName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ApplicationPermission getApplicationPermission() {
        return rApplicationPermission;
    }

    public void setApplicationPermission(ApplicationPermission applicationPermission) {
        this.rApplicationPermission = applicationPermission;
    }

    public String getPartitionName() {
        return partitionName;
    }

    public void setPartitionName(String partitionName) {
        this.partitionName = partitionName;
    }
}

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
public class AccessPermissionBodyDTO implements EntityDTO {

    private boolean read;
    private boolean write;
    private Set<String> readPartitions;
    private Set<String> writePartitions;

    public AccessPermissionBodyDTO(Set<String> readPartitions, Set<String> writePartitions) {
        this.readPartitions = readPartitions;
        this.writePartitions = writePartitions;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isWrite() {
        return write;
    }

    public void setWrite(boolean write) {
        this.write = write;
    }

    public Set<String> getReadPartitions() {
        return readPartitions;
    }

    public void setReadPartitions(Set<String> readPartitions) {
        this.readPartitions = readPartitions;
    }

    public Set<String> getWritePartitions() {
        return writePartitions;
    }

    public void setWritePartitions(Set<String> writePartitions) {
        this.writePartitions = writePartitions;
    }
}

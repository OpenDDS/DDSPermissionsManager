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
package io.unityfoundation.dds.permissions.manager.model.action;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;

import java.util.List;

@Repository
public interface ActionRepository extends PageableRepository<Action, Long> {
    Page<Action> findAllByCanPublishTrue(Pageable pageable);
    Page<Action> findAllByCanPublishFalse(Pageable pageable);
    Page<Action> findAllByApplicationGrantIdIn(List<Long> grantIds, Pageable pageable);
    Page<Action> findAllByCanPublishTrueAndApplicationGrantIdIn(List<Long> grantIds, Pageable pageable);
    Page<Action> findAllByCanPublishFalseAndApplicationGrantIdIn(List<Long> grantIds, Pageable pageable);
    Page<Action> findAllByApplicationGrantNameContainsIgnoreCase(String grantName, Pageable pageable);
    Page<Action> findAllByCanPublishTrueAndApplicationGrantNameContainsIgnoreCase(String filter, Pageable pageable);
    Page<Action> findAllByCanPublishFalseAndApplicationGrantNameContainsIgnoreCase(String filter, Pageable pageable);
    List<Long> findIdByApplicationGrantNameContainsIgnoreCase(String grantName);
    List<Long> findIdByCanPublishTrueAndApplicationGrantNameContainsIgnoreCase(String filter);
    List<Long> findIdByCanPublishFalseAndApplicationGrantNameContainsIgnoreCase(String filter);
    Page<Action> findAllByIdInAndApplicationGrantIdIn(List<Long> grandDurationIds, List<Long> groupIds, Pageable pageable);
    List<Action> findAllByApplicationGrantId(Long applicationGrantId);
}
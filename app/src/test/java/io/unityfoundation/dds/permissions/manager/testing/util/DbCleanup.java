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
package io.unityfoundation.dds.permissions.manager.testing.util;

import io.unityfoundation.dds.permissions.manager.model.action.ActionRepository;
import io.unityfoundation.dds.permissions.manager.model.actiontopic.ActionTopicRepository;
import io.unityfoundation.dds.permissions.manager.model.application.ApplicationRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrantRepository;
import io.unityfoundation.dds.permissions.manager.model.applicationpermission.ApplicationPermissionRepository;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionIntervalRepository;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicRepository;
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSetRepository;
import io.unityfoundation.dds.permissions.manager.model.topicsettopic.TopicSetTopicRepository;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import jakarta.inject.Singleton;

import javax.transaction.Transactional;

@Singleton
public class DbCleanup {
    private final TopicRepository topicRepository;
    private final GroupRepository groupRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationPermissionRepository applicationPermissionRepository;
    private final GroupUserRepository groupUserRepository;
    private final TopicSetRepository topicSetRepository;
    private final ActionIntervalRepository actionIntervalRepository;
    private final ApplicationGrantRepository applicationGrantRepository;
    private final ActionRepository actionRepository;
    private final ActionTopicRepository actionTopicRepository;
    private final TopicSetTopicRepository topicSetTopicRepository;

    public DbCleanup(TopicRepository topicRepository, GroupRepository groupRepository,
                     ApplicationRepository applicationRepository, UserRepository userRepository,
                     ApplicationPermissionRepository applicationPermissionRepository, GroupUserRepository groupUserRepository,
                     TopicSetRepository topicSetRepository, ActionIntervalRepository actionIntervalRepository, ApplicationGrantRepository applicationGrantRepository, ActionRepository actionRepository, ActionTopicRepository actionTopicRepository, TopicSetTopicRepository topicSetTopicRepository) {
        this.topicRepository = topicRepository;
        this.groupRepository = groupRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.applicationPermissionRepository = applicationPermissionRepository;
        this.groupUserRepository = groupUserRepository;
        this.actionIntervalRepository = actionIntervalRepository;
        this.topicSetRepository = topicSetRepository;
        this.applicationGrantRepository = applicationGrantRepository;
        this.actionRepository = actionRepository;
        this.actionTopicRepository = actionTopicRepository;
        this.topicSetTopicRepository = topicSetTopicRepository;
    }

    @Transactional
    public void cleanup() {
        actionTopicRepository.deleteAll();
        actionRepository.deleteAll();
        topicSetTopicRepository.deleteAll();
        topicSetRepository.deleteAll();
        actionIntervalRepository.deleteAll();
        groupUserRepository.deleteAll();
        applicationPermissionRepository.deleteAll();
        applicationGrantRepository.deleteAll();
        topicRepository.deleteAll();
        applicationRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();
    }
}
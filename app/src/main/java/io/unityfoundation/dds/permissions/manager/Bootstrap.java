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
package io.unityfoundation.dds.permissions.manager;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.unityfoundation.dds.permissions.manager.model.application.Application;
import io.unityfoundation.dds.permissions.manager.model.group.Group;
import io.unityfoundation.dds.permissions.manager.model.group.GroupRepository;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUser;
import io.unityfoundation.dds.permissions.manager.model.groupuser.GroupUserRepository;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSet;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionInterval;
import io.unityfoundation.dds.permissions.manager.model.grantduration.GrantDuration;
import io.unityfoundation.dds.permissions.manager.model.topic.TopicKind;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrant;
import io.unityfoundation.dds.permissions.manager.model.user.User;
import io.unityfoundation.dds.permissions.manager.model.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Requires(property = "dpm.bootstrap.data.enabled", value = StringUtils.TRUE)
@ConfigurationProperties("dpm.bootstrap")
public class Bootstrap {

    @MapFormat(transformation = MapFormat.MapTransformation.NESTED)
    private Map<String, Object> data;

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupUserRepository groupUserRepository;
    private static final Logger LOG = LoggerFactory.getLogger(Bootstrap.class);

    public Bootstrap(UserRepository userRepository, GroupRepository groupRepository,
            GroupUserRepository groupUserRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupUserRepository = groupUserRepository;
    }

    @EventListener
    public void devData(ServerStartupEvent event) {
        if(data != null) {
            if(data.containsKey("admin-users")) {
                ((List<String>) data.get("admin-users")).stream().forEach(email -> {
                        LOG.info(email + " is now a super admin");
                        userRepository.save(new User(email, true));
                    });
            }
            if(data.containsKey("non-admin-users")) {
                ((List<String>) data.get("non-admin-users")).stream().forEach(email -> userRepository.save(new User(email)));
            }

            if(data.containsKey("groups")) {
                ((List<Map<String, ?>>) data.get("groups")).stream().forEach(groupMap -> {
                    String groupName = (String) groupMap.get("name");
                    String groupDescription = (String) groupMap.get("description");
                    Boolean groupIsPublic =  Boolean.TRUE.equals(groupMap.get("is-public"));
                    Group group = groupRepository.save(new Group(groupName, groupDescription, groupIsPublic));

                    if (groupMap.containsKey("users")) {
                        List<Map> users = (List<Map>) groupMap.get("users");
                        users.stream().forEach((Map user) -> {
                            String email = (String) user.get("email");
                            GroupUser groupUser = new GroupUser(group, userRepository.findByEmail(email).get());

                            if(user.containsKey("admin-flags")) {
                                List<String> adminFlags = (List<String>) user.get("admin-flags");
                                groupUser.setGroupAdmin(adminFlags.contains("group"));
                                groupUser.setApplicationAdmin(adminFlags.contains("application"));
                                groupUser.setTopicAdmin(adminFlags.contains("topic"));
                            }

                            groupUserRepository.save(groupUser);
                        });
                    }

                    if (groupMap.containsKey("topics")) {
                        List<Map<String, String>> topics = (List<Map<String, String>>) groupMap.get("topics");
                        topics.stream().forEach(topicMap -> {
                            String name = topicMap.get("name");
                            String topicDescription = topicMap.get("description");
                            Boolean topicIsPublic = Boolean.TRUE.equals(topicMap.get("is-public"));
                            TopicKind kind = TopicKind.valueOf(topicMap.get("kind"));
                            group.addTopic(new Topic(name, kind, topicDescription, topicIsPublic, group));
                        });
                    }


                    if (groupMap.containsKey("topic-sets")) {
                        List<Map<String, String>> topicSets = (List<Map<String, String>>) groupMap.get("topic-sets");
                        topicSets.stream().forEach(topicSetMap -> {
                            String name = topicSetMap.get("name");
                            group.addTopicSet(new TopicSet(name, group));
                        });
                    }

                    if (groupMap.containsKey("applications")) {
                        List<Map<String, String>> applications = (List<Map<String, String>>) groupMap.get("applications");
                        applications.stream().forEach(applicationMap -> {
                            String applicationName = applicationMap.get("name");
                            String applicationDescription = applicationMap.get("description");
                            Boolean applicationIsPublic = Boolean.TRUE.equals(applicationMap.get("is-public"));
                            group.addApplication(new Application(applicationName, group, applicationDescription, applicationIsPublic));
                        });
                    }

                    if (groupMap.containsKey("grant-durations")) {
                        List<Map<String, Object>> grantDurations = (List<Map<String, Object>>) groupMap.get("grant-durations");
                        grantDurations.stream().forEach(grantDurationMap -> {
                            String grantDurationName = (String) grantDurationMap.get("name");
                            Long grantDurationMilliseconds = (Long) grantDurationMap.get("durationInMilliseconds");
                            String grantDurationMetadata = (String) grantDurationMap.get("durationMetadata");
                            GrantDuration grantDuration = new GrantDuration(grantDurationName, group);
                            grantDuration.setDurationInMilliseconds(grantDurationMilliseconds);
                            grantDuration.setDurationMetadata(grantDurationMetadata);
                            group.addGrantDurations(grantDuration);
                        });
                    }

                     if (groupMap.containsKey("action-intervals")) {
                        List<Map<String, Object>> actionIntervals = (List<Map<String, Object>>) groupMap.get("action-intervals");
                        actionIntervals.stream().forEach(actionIntervalMap -> {
                            String actionIntervalName = (String) actionIntervalMap.get("name");
                            Instant startDate = ((OffsetDateTime) actionIntervalMap.getOrDefault("startDate", OffsetDateTime.now())).toInstant();
                            Instant endDate = ((OffsetDateTime) actionIntervalMap.getOrDefault("endDate", OffsetDateTime.now())).toInstant();
                            ActionInterval actionInterval = new ActionInterval(actionIntervalName, group);
                            actionInterval.setStartDate(startDate);
                            actionInterval.setEndDate(endDate);
                            group.addActionInterval(actionInterval);
                        });

                    }

                    if (groupMap.containsKey("grants")) {
                        List<Map<String, Object>> grants = (List<Map<String, Object>>) groupMap.get("grants");
                        grants.stream().forEach(grantMap -> {
                            String grantName = (String) grantMap.get("name");
                            group.addApplicationGrant(new ApplicationGrant(grantName, group.getApplications().iterator().next(), group, group.getGrantDurations().iterator().next()));
                        });
                    }

                    groupRepository.update(group);
                });
            }
        }
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}

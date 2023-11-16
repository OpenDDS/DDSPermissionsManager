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


import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.unityfoundation.dds.permissions.manager.model.actioninterval.ActionInterval;
import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrant;
import io.unityfoundation.dds.permissions.manager.model.topic.Topic;
import io.unityfoundation.dds.permissions.manager.model.topicset.TopicSet;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions_action")
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private ApplicationGrant applicationGrant;

    @NonNull
    private Boolean canPublish;

    @NonNull
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private ActionInterval actionInterval;

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<TopicSet> topicSets = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    private Set<Topic> topics = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "action")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ActionPartition> partitions = new HashSet<>();

    @DateCreated
    private Instant dateCreated;

    @DateUpdated
    private Instant dateUpdated;

    public Action() {
    }

    public Action(ApplicationGrant applicationGrant, ActionInterval actionInterval, Boolean canPublish) {
        this.applicationGrant = applicationGrant;
        this.actionInterval = actionInterval;
        this.canPublish = canPublish;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public Set<Topic> getTopics() {
        if (topics == null) return null;
        return Collections.unmodifiableSet(topics);
    }

    public void setTopics(Set<Topic> topics) {
        this.topics = topics;
    }

    public boolean removeTopic(Long topicId) {
        this.dateUpdated = Instant.now();
        return topics.removeIf(topic -> topicId != null && topicId.equals(topic.getId()));
    }

    public void addTopic(Topic topic) {
        topics.add(topic);
        this.dateUpdated = Instant.now();
    }

    public Set<TopicSet> getTopicSets() {
        if (topicSets == null) return null;
        return Collections.unmodifiableSet(topicSets);
    }

    public void setTopicSets(Set<TopicSet> topicSets) {
        this.topicSets = topicSets;
    }

    public boolean removeTopicSet(Long topicSetId) {
        this.dateUpdated = Instant.now();
        return topics.removeIf(topicSet -> topicSetId != null && topicSetId.equals(topicSet.getId()));
    }

    public void addTopicSet(TopicSet topicSet) {
        topicSets.add(topicSet);
        this.dateUpdated = Instant.now();
    }

    public Instant getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Instant dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Instant getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Instant dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    @NonNull
    public ApplicationGrant getApplicationGrant() {
        return applicationGrant;
    }

    public void setApplicationGrant(@NonNull ApplicationGrant applicationGrant) {
        this.applicationGrant = applicationGrant;
    }

    @NonNull
    public ActionInterval getActionInterval() {
        return actionInterval;
    }

    public void setActionInterval(@NonNull ActionInterval actionInterval) {
        this.actionInterval = actionInterval;
    }

    public Set<ActionPartition> getPartitions() {
        return partitions;
    }

    public void setPartitions(Set<ActionPartition> partitions) {
        this.partitions = partitions;
    }

    public Boolean getCanPublish() {
        return canPublish;
    }

    public void setCanPublish(Boolean canPublish) {
        this.canPublish = canPublish;
    }
}
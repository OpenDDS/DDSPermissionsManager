package io.unityfoundation.dds.permissions.manager.model.action.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.core.annotation.Introspected;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Introspected
public class ActionDTO implements EntityDTO {

    private Long id;
    private Long applicationGrantId;
    private Long actionIntervalId;
    private String actionIntervalName;
    private Set<Map> topics = new HashSet<>();
    private Set<Map> topicSets = new HashSet<>();
    Set<String> partitions = new HashSet<>();

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant dateUpdated;

    public ActionDTO() {
    }

    public ActionDTO(Long id, Long applicationGrantId, Long actionIntervalId, String actionIntervalName, Set<Map> topics, Set<Map> topicSets, Set<String> partitions, Instant dateCreated, Instant dateUpdated) {
        this.id = id;
        this.applicationGrantId = applicationGrantId;
        this.actionIntervalId = actionIntervalId;
        this.actionIntervalName = actionIntervalName;
        this.topics = topics;
        this.topicSets = topicSets;
        this.partitions = partitions;
        this.dateCreated = dateCreated;
        this.dateUpdated = dateUpdated;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationGrantId() {
        return applicationGrantId;
    }

    public void setApplicationGrantId(Long applicationGrantId) {
        this.applicationGrantId = applicationGrantId;
    }

    public Long getActionIntervalId() {
        return actionIntervalId;
    }

    public void setActionIntervalId(Long actionIntervalId) {
        this.actionIntervalId = actionIntervalId;
    }

    public String getActionIntervalName() {
        return actionIntervalName;
    }

    public void setActionIntervalName(String actionIntervalName) {
        this.actionIntervalName = actionIntervalName;
    }

    public Set<Map> getTopics() {
        return topics;
    }

    public void setTopics(Set<Map> topics) {
        this.topics = topics;
    }

    public Set<Map> getTopicSets() {
        return topicSets;
    }

    public void setTopicSets(Set<Map> topicSets) {
        this.topicSets = topicSets;
    }

    public Set<String> getPartitions() {
        return partitions;
    }

    public void setPartitions(Set<String> partitions) {
        this.partitions = partitions;
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
}

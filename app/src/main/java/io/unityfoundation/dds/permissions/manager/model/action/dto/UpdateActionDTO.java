package io.unityfoundation.dds.permissions.manager.model.action.dto;

import io.micronaut.core.annotation.Introspected;

import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Introspected
public class UpdateActionDTO {

    @NotNull
    private Long actionIntervalId;
    private Set<String> partitions = new HashSet<>();
    private Set<Long> topicSetIds = new HashSet<>();
    private Set<Long> topicIds = new HashSet<>();

    public Long getActionIntervalId() {
        return actionIntervalId;
    }

    public void setActionIntervalId(Long actionIntervalId) {
        this.actionIntervalId = actionIntervalId;
    }

    public Set<String> getPartitions() {
        return partitions;
    }

    public void setPartitions(Set<String> partitions) {
        this.partitions = partitions;
    }

    public Set<Long> getTopicSetIds() {
        return topicSetIds;
    }

    public void setTopicSetIds(Set<Long> topicSetIds) {
        this.topicSetIds = topicSetIds;
    }

    public Set<Long> getTopicIds() {
        return topicIds;
    }

    public void setTopicIds(Set<Long> topicIds) {
        this.topicIds = topicIds;
    }
}

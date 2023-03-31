package io.unityfoundation.dds.permissions.manager.model.topic;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.unityfoundation.dds.permissions.manager.model.EntityDTO;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Introspected
public class TopicDTO implements EntityDTO {

    private Long id;
    @NotBlank
    @Size(min = 3)
    private String name;
    private TopicKind kind;
    @Size(max = 4000)
    private String description;
    private Boolean isPublic;
    @NotNull
    private Long group;
    private String groupName;
    private String canonicalName;

    public TopicDTO() {
    }

    public TopicDTO(Topic topic) {
        this.id = topic.getId();
        this.name = topic.getName();
        this.kind = topic.getKind();
        this.description = topic.getDescription();
        this.isPublic = topic.getMakePublic();
        this.group = topic.getPermissionsGroup().getId();
        this.groupName = topic.getPermissionsGroup().getName();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TopicKind getKind() {
        return kind;
    }

    public void setKind(TopicKind kind) {
        this.kind = kind;
    }

    public Long getGroup() {
        return group;
    }

    public void setGroup(Long group) {
        this.group = group;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
    }
}

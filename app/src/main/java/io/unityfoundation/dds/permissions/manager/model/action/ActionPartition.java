package io.unityfoundation.dds.permissions.manager.model.action;

import io.unityfoundation.dds.permissions.manager.model.applicationgrant.ApplicationGrant;

import javax.persistence.*;

@Entity
@Table(name = "permissions_action_partition")
public class ActionPartition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "permissions_application_permission_id")
    private Action action;

    private String partitionName;

    public ActionPartition() {
    }

    public ActionPartition(Action action, String partitionName) {
        this.action = action;
        this.partitionName = partitionName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getPartitionName() {
        return partitionName;
    }

    public void setPartitionName(String partitionName) {
        this.partitionName = partitionName;
    }

}

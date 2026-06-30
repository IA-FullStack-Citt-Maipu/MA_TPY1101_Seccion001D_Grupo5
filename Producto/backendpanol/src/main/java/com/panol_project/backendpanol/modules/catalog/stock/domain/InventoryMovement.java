package com.panol_project.backendpanol.modules.catalog.stock.domain;

import java.time.Instant;
import java.util.UUID;

public class InventoryMovement {

    private String id;

    private UUID implementUuid;

    private MovementAction action;

    private Integer quantity;

    private UUID performedByUuid;

    private String performedByName;

    private Instant timestamp;

    private String notes;

    public InventoryMovement() {
    }

    public InventoryMovement(UUID implementUuid, MovementAction action, Integer quantity, UUID performedByUuid, Instant timestamp, String notes) {
        this.implementUuid = implementUuid;
        this.action = action;
        this.quantity = quantity;
        this.performedByUuid = performedByUuid;
        this.timestamp = timestamp;
        this.notes = notes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getImplementUuid() {
        return implementUuid;
    }

    public void setImplementUuid(UUID implementUuid) {
        this.implementUuid = implementUuid;
    }

    public MovementAction getAction() {
        return action;
    }

    public void setAction(MovementAction action) {
        this.action = action;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public UUID getPerformedByUuid() {
        return performedByUuid;
    }

    public void setPerformedByUuid(UUID performedByUuid) {
        this.performedByUuid = performedByUuid;
    }

    public String getPerformedByName() {
        return performedByName;
    }

    public void setPerformedByName(String performedByName) {
        this.performedByName = performedByName;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

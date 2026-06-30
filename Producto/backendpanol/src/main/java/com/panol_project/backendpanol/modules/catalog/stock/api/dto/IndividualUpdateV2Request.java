package com.panol_project.backendpanol.modules.catalog.stock.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class IndividualUpdateV2Request {

    private String status;
    private String condition;
    private String notes;
    private UUID currentLocationUuid;
    private Boolean active;
    private Integer remainingLife;
    private boolean remainingLifePresent;
    private Boolean assetCodeReprintRequired;

    public String status() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String condition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String notes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @JsonProperty("current_location_uuid")
    public UUID currentLocationUuid() {
        return currentLocationUuid;
    }

    @JsonProperty("current_location_uuid")
    public void setCurrentLocationUuid(UUID currentLocationUuid) {
        this.currentLocationUuid = currentLocationUuid;
    }

    public Boolean active() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @JsonProperty("remaining_life")
    public Integer remainingLife() {
        return remainingLife;
    }

    public boolean remainingLifePresent() {
        return remainingLifePresent;
    }

    @JsonProperty("remaining_life")
    public void setRemainingLife(Integer remainingLife) {
        this.remainingLife = remainingLife;
        this.remainingLifePresent = true;
    }

    @JsonProperty("asset_code_reprint_required")
    public Boolean assetCodeReprintRequired() {
        return assetCodeReprintRequired;
    }

    @JsonProperty("asset_code_reprint_required")
    public void setAssetCodeReprintRequired(Boolean assetCodeReprintRequired) {
        this.assetCodeReprintRequired = assetCodeReprintRequired;
    }
}

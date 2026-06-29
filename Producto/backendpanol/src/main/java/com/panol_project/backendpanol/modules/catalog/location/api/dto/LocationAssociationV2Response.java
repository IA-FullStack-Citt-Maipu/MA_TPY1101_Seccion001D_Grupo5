package com.panol_project.backendpanol.modules.catalog.location.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record LocationAssociationV2Response(
        @JsonProperty("location_uuid")
        UUID locationUuid,
        @JsonProperty("association_count")
        Integer associationCount,
        @JsonProperty("implement_count")
        Integer implementCount,
        @JsonProperty("individual_count")
        Integer individualCount,
        @JsonProperty("can_delete")
        Boolean canDelete
) {
}

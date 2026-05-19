package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateImplementV2Request(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 4000) String description,
        @NotNull UUID categoryUuid,
        @NotNull UUID locationUuid,
        @JsonProperty("item_type")
        @NotBlank
        @Pattern(regexp = "^(fungible|no_fungible)$")
        String itemType,
        @NotNull @JsonProperty("min_stock") Integer minStock,
        String barcode,
        @JsonProperty("img_url") String imgUrl,
        String observations
) {
}

package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateImplementV2Request(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String name,
        @Size(max = 2000, message = "La descripcion no puede superar 2000 caracteres")
        String description,
        @NotNull(message = "La categoria es obligatoria")
        UUID categoryUuid,
        @NotNull(message = "La ubicacion es obligatoria")
        UUID locationUuid,
        @JsonProperty("item_type")
        @NotBlank(message = "El tipo de implemento es obligatorio")
        @Pattern(regexp = "^(fungible|no_fungible)$", message = "El tipo de implemento debe ser fungible o no_fungible")
        String itemType,
        @JsonProperty("min_stock")
        @NotNull(message = "El stock minimo es obligatorio")
        Integer minStock,
        @Size(max = 100, message = "El codigo de barras no puede superar 100 caracteres")
        String barcode,
        @JsonProperty("img_url")
        @Size(max = 2000, message = "La URL de imagen no puede superar 2000 caracteres")
        String imgUrl,
        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
        String observations
) {
}

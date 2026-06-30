package com.panol_project.backendpanol.modules.catalog.implement.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateImplementV2Request(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 4000) String description,
        @NotNull UUID categoryUuid,
        @NotNull UUID locationUuid,
        @JsonProperty("item_type")
        @NotBlank
        @Pattern(regexp = "^(consumable|reusable|individual)$")
        String itemType,
        @NotNull @JsonProperty("min_stock") Integer minStock,
        String barcode,
        @JsonProperty("img_url") String imgUrl,
        String observations,
        @JsonProperty("cost_center")
        @Pattern(regexp = "^\\d{1,10}$", message = "El Ce.coste debe contener solo digitos y un maximo de 10 caracteres")
        String costCenter,
        @JsonProperty("net_value")
        @Digits(integer = 9, fraction = 0, message = "El valor neto debe ser un monto entero de hasta 9 digitos")
        BigDecimal netValue
) {
}

package com.panol_project.backendpanol.modules.catalog.location.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLocationRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String name,
        @Size(max = 255, message = "La descripcion no puede superar 255 caracteres")
        String description
) {
}

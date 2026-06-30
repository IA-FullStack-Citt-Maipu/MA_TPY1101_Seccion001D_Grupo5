package com.panol_project.backendpanol.modules.catalog.category.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoriaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
        String descripcion
) {
}

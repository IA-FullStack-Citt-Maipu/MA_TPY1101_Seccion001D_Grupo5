package com.panol_project.backendpanol.modules.catalog.category.application.contract;

import java.util.UUID;

public interface CategoryValidationContract {
    void validarCategoriaActivaParaImplemento(UUID categoryUuid);
}

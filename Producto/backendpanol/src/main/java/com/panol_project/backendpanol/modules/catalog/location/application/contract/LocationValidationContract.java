package com.panol_project.backendpanol.modules.catalog.location.application.contract;

import java.util.UUID;

public interface LocationValidationContract {
    void validarLocationExistente(UUID locationUuid);
}

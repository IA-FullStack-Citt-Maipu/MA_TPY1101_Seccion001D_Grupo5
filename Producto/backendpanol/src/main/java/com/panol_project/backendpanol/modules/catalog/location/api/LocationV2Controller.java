package com.panol_project.backendpanol.modules.catalog.location.api;

import com.panol_project.backendpanol.modules.catalog.location.api.dto.CreateLocationRequest;
import com.panol_project.backendpanol.modules.catalog.location.api.dto.LocationAssociationV2Response;
import com.panol_project.backendpanol.modules.catalog.location.api.dto.LocationSelectorV2Response;
import com.panol_project.backendpanol.modules.catalog.location.api.dto.UpdateLocationRequest;
import com.panol_project.backendpanol.modules.catalog.location.application.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/locations")
public class LocationV2Controller {

    private final LocationService locationService;

    public LocationV2Controller(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<LocationSelectorV2Response> listSelector() {
        var locations = locationService.listarSelector();
        return locations.stream().map(location -> new LocationSelectorV2Response(location.uuid(), location.name(), location.description(), location.active())).toList();
    }

    @GetMapping("/management")
    @PreAuthorize("hasRole('COORDINADOR')")
    public List<LocationSelectorV2Response> listManagement() {
        var locations = locationService.listarGestion();
        return locations.stream().map(location -> new LocationSelectorV2Response(location.uuid(), location.name(), location.description(), location.active())).toList();
    }

    @GetMapping("/{locationUuid}/associations")
    @PreAuthorize("hasRole('COORDINADOR')")
    public LocationAssociationV2Response associations(@PathVariable UUID locationUuid) {
        var summary = locationService.obtenerResumenAsociaciones(locationUuid);
        return new LocationAssociationV2Response(
                summary.locationUuid(),
                summary.totalAssociations(),
                summary.implementAssociations(),
                summary.individualAssociations(),
                summary.totalAssociations() == 0
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COORDINADOR')")
    public LocationSelectorV2Response create(@Valid @RequestBody CreateLocationRequest request) {
        var created = locationService.crear(request.name(), request.description());
        return new LocationSelectorV2Response(created.uuid(), created.name(), created.description(), created.active());
    }

    @PutMapping("/{locationUuid}")
    @PreAuthorize("hasRole('COORDINADOR')")
    public LocationSelectorV2Response update(@PathVariable UUID locationUuid, @Valid @RequestBody UpdateLocationRequest request) {
        var updated = locationService.editar(locationUuid, request.name(), request.description());
        return new LocationSelectorV2Response(updated.uuid(), updated.name(), updated.description(), updated.active());
    }

    @PatchMapping("/{locationUuid}/active")
    @PreAuthorize("hasRole('COORDINADOR')")
    public LocationSelectorV2Response setActive(@PathVariable UUID locationUuid, @RequestParam boolean active) {
        var updated = locationService.setActive(locationUuid, active);
        return new LocationSelectorV2Response(updated.uuid(), updated.name(), updated.description(), updated.active());
    }

    @DeleteMapping("/{locationUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('COORDINADOR')")
    public void delete(@PathVariable UUID locationUuid) {
        locationService.eliminar(locationUuid);
    }
}

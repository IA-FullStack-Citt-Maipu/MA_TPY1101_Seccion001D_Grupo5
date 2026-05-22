package com.panol_project.backendpanol.modules.catalog.room.api;

import com.panol_project.backendpanol.modules.catalog.room.api.dto.RoomSelectorV2Response;
import com.panol_project.backendpanol.modules.catalog.room.application.RoomService;
import com.panol_project.backendpanol.modules.catalog.room.domain.RoomOption;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/rooms")
public class RoomV2Controller {

    private final RoomService roomService;

    public RoomV2Controller(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RoomSelectorV2Response> listSelector() {
        return roomService.listarSelector().stream()
                .map(this::toResponse)
                .toList();
    }

    private RoomSelectorV2Response toResponse(RoomOption room) {
        return new RoomSelectorV2Response(room.id(), room.uuid(), room.name());
    }
}

package com.panol_project.backendpanol.modules.catalog.room.domain;

import java.util.List;

public interface RoomRepository {

    List<RoomOption> findAllActive();
}

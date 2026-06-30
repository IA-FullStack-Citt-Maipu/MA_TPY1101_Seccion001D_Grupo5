package com.panol_project.backendpanol.modules.catalog.room.application;

import com.panol_project.backendpanol.modules.catalog.room.domain.RoomOption;
import com.panol_project.backendpanol.modules.catalog.room.domain.RoomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private final RoomRepository repository;

    public RoomService(RoomRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RoomOption> listarSelector() {
        return repository.findAllActive();
    }
}

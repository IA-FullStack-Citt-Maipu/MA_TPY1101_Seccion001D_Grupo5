package com.panol_project.backendpanol.modules.users.application;

import com.panol_project.backendpanol.modules.users.application.contract.UserDirectoryContract;
import com.panol_project.backendpanol.modules.users.domain.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService implements UserDirectoryContract {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<UUID, String> getNombresUsuariosByUuid(List<UUID> userUuids) {
        return repository.findNamesByUuids(userUuids);
    }
}

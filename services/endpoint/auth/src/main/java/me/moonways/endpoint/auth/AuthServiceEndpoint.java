package me.moonways.endpoint.auth;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.jdbc.core.DatabaseConnection;
import me.moonways.bridgenet.jdbc.provider.DatabaseProvider;
import me.moonways.bridgenet.model.service.auth.Account;
import me.moonways.bridgenet.model.service.auth.AuthServiceModel;
import me.moonways.bridgenet.model.service.auth.AuthorizationResult;
import me.moonways.bridgenet.services.loader.endpoint.EndpointRemoteContext;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;

import java.util.Optional;
import java.util.UUID;

public final class AuthServiceEndpoint extends EndpointServiceObject implements AuthServiceModel {

    @Inject
    private DatabaseConnection databaseConnection;
    @Inject
    private DatabaseProvider databaseProvider;

    @Override
    protected void construct(EndpointRemoteContext context) {
        // todo
    }

    @Override
    public Optional<Account> findAccountById(UUID playerId) {
        return Optional.empty(); //todo
    }

    @Override
    public AuthorizationResult tryLogin(UUID playerId, String inputPassword) {
        return AuthorizationResult.FAILURE; //todo
    }

    @Override
    public AuthorizationResult tryRegistration(UUID playerId, String inputPassword) {
        return AuthorizationResult.FAILURE; //todo
    }

    @Override
    public AuthorizationResult tryPasswordChange(UUID playerId, String actualPassword, String newPassword) {
        return AuthorizationResult.FAILURE; //todo
    }

    @Override
    public AuthorizationResult tryLogOut(UUID playerId) {
        return AuthorizationResult.FAILURE; //todo
    }

    @Override
    public AuthorizationResult tryAccountDelete(UUID playerId) {
        return AuthorizationResult.FAILURE; //todo
    }
}

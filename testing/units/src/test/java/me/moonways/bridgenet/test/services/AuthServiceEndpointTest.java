package me.moonways.bridgenet.test.services;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.model.service.auth.Account;
import me.moonways.bridgenet.model.service.auth.AuthServiceModel;
import me.moonways.bridgenet.model.service.auth.AuthenticationSession;
import me.moonways.bridgenet.model.service.auth.AuthorizationResult;
import me.moonways.bridgenet.test.data.TestConst;
import me.moonways.bridgenet.test.engine.ModernTestEngineRunner;
import me.moonways.bridgenet.test.engine.component.module.impl.ServicesModule;
import me.moonways.bridgenet.test.engine.persistance.TestModules;
import me.moonways.bridgenet.test.engine.persistance.TestOrdered;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(ModernTestEngineRunner.class)
@TestModules(ServicesModule.class)
public class AuthServiceEndpointTest {

    @Inject
    private AuthServiceModel serviceModel;

    @Test
    @TestOrdered(0)
    public void test_accountRegistration() {
        assertFalse(serviceModel.findAccountById(TestConst.Player.ID).isPresent());

        AuthorizationResult resultSuccess = serviceModel.tryRegistration(TestConst.Player.ID, TestConst.Auth.ACTUAL_PASSWORD);
        AuthorizationResult resultAlreadyRegistered = serviceModel.tryRegistration(TestConst.Player.ID, TestConst.Auth.ACTUAL_PASSWORD);

        assertTrue(serviceModel.findAccountById(TestConst.Player.ID).isPresent());
        assertEquals(resultSuccess, AuthorizationResult.SUCCESS);
        assertEquals(resultAlreadyRegistered, AuthorizationResult.FAILURE__ALREADY_REGISTERED);
    }

    @Test
    @TestOrdered(1)
    public void test_accountSession() {
        Optional<Account> accountById = serviceModel.findAccountById(TestConst.Player.ID);
        assertTrue(accountById.isPresent());

        Account account = accountById.get();

        AuthenticationSession session = account.getSession();

        assertNotNull(session.getLastAuthenticationDate());
        assertNotNull(session.getLastAuthenticationIp());
        assertFalse(session.isExpired());
        assertTrue(session.isActive());
        assertFalse(session.isInactive());
    }

    @Test
    @TestOrdered(2)
    public void test_accountLogout() {
        assertTrue(serviceModel.findAccountById(TestConst.Player.ID).isPresent());

        AuthorizationResult resultSuccess = serviceModel.tryLogOut(TestConst.Player.ID);
        AuthorizationResult resultNotLogged = serviceModel.tryLogOut(TestConst.Player.ID);

        assertEquals(resultSuccess, AuthorizationResult.SUCCESS);
        assertEquals(resultNotLogged, AuthorizationResult.FAILURE__NOT_LOGGED);
    }

    @Test
    @TestOrdered(3)
    public void test_accountLogging() {
        assertTrue(serviceModel.findAccountById(TestConst.Player.ID).isPresent());

        AuthorizationResult resultSuccess = serviceModel.tryLogin(TestConst.Player.ID, TestConst.Auth.ACTUAL_PASSWORD);
        AuthorizationResult resultAlreadyLogged = serviceModel.tryLogin(TestConst.Player.ID, TestConst.Auth.ACTUAL_PASSWORD);

        assertEquals(resultSuccess, AuthorizationResult.SUCCESS);
        assertEquals(resultAlreadyLogged, AuthorizationResult.FAILURE__ALREADY_LOGGED);
    }

    @Test
    @TestOrdered(4)
    public void test_accountPasswordChange() {
        assertTrue(serviceModel.findAccountById(TestConst.Player.ID).isPresent());

        AuthorizationResult resultUncorrectedPassword = serviceModel.tryPasswordChange(TestConst.Player.ID, TestConst.Auth.NEW_PASSWORD, TestConst.Auth.ACTUAL_PASSWORD);
        AuthorizationResult resultSimilarPreviousPassword = serviceModel.tryPasswordChange(TestConst.Player.ID, TestConst.Auth.ACTUAL_PASSWORD, TestConst.Auth.ACTUAL_PASSWORD);
        AuthorizationResult resultSuccess = serviceModel.tryPasswordChange(TestConst.Player.ID, TestConst.Auth.ACTUAL_PASSWORD, TestConst.Auth.NEW_PASSWORD);

        assertEquals(resultUncorrectedPassword, AuthorizationResult.FAILURE__UNCORRECTED_PASSWORD);
        assertEquals(resultSimilarPreviousPassword, AuthorizationResult.FAILURE__SIMILAR_PREVIOUS_PASSWORDS);
        assertEquals(resultSuccess, AuthorizationResult.SUCCESS);
    }

    @Test
    @TestOrdered(5)
    public void test_accountDelete() {
        assertTrue(serviceModel.findAccountById(TestConst.Player.ID).isPresent());

        AuthorizationResult resultSuccess = serviceModel.tryAccountDelete(TestConst.Player.ID);
        AuthorizationResult resultNotFound = serviceModel.tryAccountDelete(TestConst.Player.ID);

        assertEquals(resultSuccess, AuthorizationResult.SUCCESS);
        assertEquals(resultNotFound, AuthorizationResult.FAILURE__ACCOUNT_NOT_FOUND);
    }
}

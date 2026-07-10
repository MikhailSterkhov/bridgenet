package me.moonways.bridgenet.client.api;

import com.google.gson.GsonBuilder;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.assembly.ResourcesAssembly;
import me.moonways.bridgenet.assembly.ResourcesTypes;
import me.moonways.bridgenet.jdbc.core.DatabaseConnection;
import me.moonways.bridgenet.jdbc.core.compose.DatabaseComposer;
import me.moonways.bridgenet.jdbc.entity.EntityRepositoryFactory;
import me.moonways.bridgenet.jdbc.provider.BridgenetJdbcProvider;
import me.moonways.bridgenet.jdbc.provider.DatabaseProvider;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Log4j2
public final class ClientEngine {

    private BeansService beansService;

    @Inject
    private ResourcesAssembly assembly;
    @Inject
    private DatabaseProvider databaseProvider;

    public void setProperties() {
        // jdbc settings.
        System.setProperty("system.jdbc.username", "username");
        System.setProperty("system.jdbc.password", "password");
    }

    public BeansService bindAll() {
        beansService = new BeansService();

        beansService.start();

        beansService.bind(new Properties());
        beansService.bind(new GsonBuilder().setLenient().create());

        beansService.inject(this);

        bindJdbcConnection();

        return beansService;
    }

    private void bindJdbcConnection() {
        BridgenetJdbcProvider.JdbcSettingsConfig jdbcSettingsConfig = readSettings();
        BridgenetJdbcProvider bridgenetJdbcProvider = new BridgenetJdbcProvider(databaseProvider);

        bridgenetJdbcProvider.initConnection(jdbcSettingsConfig);

        DatabaseProvider databaseProvider = bridgenetJdbcProvider.getDatabaseProvider();
        DatabaseConnection databaseConnection = bridgenetJdbcProvider.getDatabaseConnection();
        DatabaseComposer composer = databaseProvider.getComposer();

        beansService.bind(composer);
        beansService.bind(databaseConnection);
        beansService.bind(new EntityRepositoryFactory(composer, databaseConnection));
    }

    private BridgenetJdbcProvider.JdbcSettingsConfig readSettings() {
        return assembly.readJsonAtEntity(ResourcesTypes.JDBC_JSON,
                StandardCharsets.UTF_8,
                BridgenetJdbcProvider.JdbcSettingsConfig.class);
    }

}

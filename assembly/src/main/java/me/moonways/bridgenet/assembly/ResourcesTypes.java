package me.moonways.bridgenet.assembly;

/**
 * Данный класс отвечает за хранения наименований
 * ресурсов, хранящихся в модуле 'assembly' в константах
 * для более быстрого и удобного доступа к ним.
 */
public final class ResourcesTypes {

    // general resources into 'etc' directory.
    public static final String SYSTEM_OVERRIDE_PROPERTIES = "config.properties";
    public static final String BOOTSTRAP_XML = "bootstrap.xml";
    public static final String JDBC_JSON = "jdbc.json";
    public static final String PROFILER_ATTRIBUTES_JSON = "profiler_attributes.json";
    public static final String MTP_CONFIG_JSON = "mtpconfig.json";
    public static final String REST_SERVER_XML = "rest_server.xml";
    public static final String REMOTE_SERVICES_XML = "remote_services.xml";
    public static final String DECORATORS_XML = "decorators.xml";
    public static final String RMI_POLICY = "rmi.policy";

    // minecraft data json files.
    public static final String MINECRAFT_ENCHANTS_JSON = "minecraft_data/enchantments.json";
    public static final String MINECRAFT_ITEMS_JSON = "minecraft_data/items.json";

    // class-loader resources with InputStream.
    public static final String LOG4J2_XML = "log4j2.xml";

}

package me.moonways.bridgenet.rmi.service;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.assembly.ResourcesAssembly;
import me.moonways.bridgenet.assembly.ResourcesTypes;
import me.moonways.bridgenet.rmi.endpoint.Endpoint;
import me.moonways.bridgenet.rmi.endpoint.EndpointController;
import me.moonways.bridgenet.rmi.module.ModuleFactory;
import me.moonways.bridgenet.rmi.module.ModuleID;
import me.moonways.bridgenet.rmi.module.RemoteModule;
import me.moonways.bridgenet.rmi.module.ServiceModulesContainer;
import me.moonways.bridgenet.rmi.xml.XMLServiceModuleDescriptor;
import me.moonways.bridgenet.rmi.xml.XMLServicesConfigDescriptor;
import me.moonways.bridgenet.rmi.xml.XmlServiceInfoDescriptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RMISecurityManager;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Log4j2
@Autobind
public final class RemoteServicesManagement {

    @Getter
    private XMLServicesConfigDescriptor xmlConfiguration;

    @Getter
    private final Map<String, ServiceInfo> servicesInfos = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<ServiceInfo, RemoteService> servicesImplements = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<Class<?>, Consumer<? extends RemoteService>> subscriptionsOnRegitstrationMap = Collections.synchronizedMap(new HashMap<>());

    @Getter
    private final Map<ModuleID, ModuleFactory> modulesFactories = Collections.synchronizedMap(new HashMap<>());

    @Getter
    private final Map<ServiceInfo, ServiceModulesContainer> modulesContainerMap = Collections.synchronizedMap(new HashMap<>());

    @Inject
    private BeansService beansService;
    @Inject
    private ResourcesAssembly assembly;

    @Getter
    private final EndpointController endpointController = new EndpointController();

    @PostConstruct
    void init() {
        injectSecurityPolicy();
        beansService.inject(endpointController);
    }

    public <T extends RemoteService> void subscribeOnExported(Class<T> serviceClass, Consumer<T> consumer) {
        subscriptionsOnRegitstrationMap.put(serviceClass, consumer);
    }

    @SuppressWarnings("deprecation")
    private void injectSecurityPolicy() {
        String policyFilepath = assembly.readResourcePath(ResourcesTypes.RMI_POLICY);

        System.setProperty("java.security.policy", policyFilepath);
        System.setSecurityManager(new RMISecurityManager());
    }

    public void initConfig() {
        xmlConfiguration = assembly.readXmlAtEntity(ResourcesTypes.REMOTE_SERVICES_XML, XMLServicesConfigDescriptor.class);

        log.info("Parsed RMI XML-Configuration content: {}", xmlConfiguration);

        List<XMLServiceModuleDescriptor> xmlModulesList = xmlConfiguration.getModulesList();
        List<XmlServiceInfoDescriptor> xmlServicesList = xmlConfiguration.getServicesList();

        if (xmlModulesList != null) {
            initXmlModules(xmlModulesList);
        }

        if (xmlServicesList != null) {
            initXmlServices(xmlServicesList);
        }
    }

    public void initEndpointsController() {
        endpointController.injectInternalComponents();
        endpointController.findEndpoints();

        List<Endpoint> endpointsList = endpointController.getEndpoints();

        for (Endpoint endpoint : endpointsList) {

            ServiceInfo serviceInfo = endpoint.getServiceInfo();
            ServiceModulesContainer serviceModulesContainer = new ServiceModulesContainer(serviceInfo);

            for (ModuleFactory moduleFactory : modulesFactories.values()) {
                serviceModulesContainer.injectModule(moduleFactory);
            }

            modulesContainerMap.put(serviceInfo, serviceModulesContainer);
        }
    }

    public void exportEndpoints() {
        log.info("Exporting remote-services...");
        endpointController.bindEndpoints();
    }

    private void initXmlModules(List<XMLServiceModuleDescriptor> xmlModulesList) {
        log.info("Registering §2{} §rremote service modules", xmlModulesList.size());

        for (XMLServiceModuleDescriptor xmlService : xmlModulesList) {
            registerModule(xmlService);
        }
    }

    private void initXmlServices(List<XmlServiceInfoDescriptor> xmlServicesList) {
        log.info("Registering §2{} §rremote services descriptions", xmlServicesList.size());

        for (XmlServiceInfoDescriptor xmlService : xmlServicesList) {

            String name = xmlService.getName().toUpperCase();
            String bindPort = xmlService.getBindPort();
            String modelPath = xmlService.getModelPath();

            log.info("Registered new Service description: §f{} §r(port={}, class={})", name, bindPort, modelPath);

            servicesInfos.put(name.toLowerCase(), createServiceInfo(xmlService));
        }
    }

    private ServiceInfo createServiceInfo(XmlServiceInfoDescriptor wrapper) {
        String name = wrapper.getName();
        Class<?> modelClass = null;

        try {
            ClassLoader classLoader = getClass().getClassLoader();
            modelClass = classLoader.loadClass(wrapper.getModelPath());
        } catch (ClassNotFoundException exception) {
            log.error(new RemoteServiceException(exception));
        }

        if (!RemoteService.class.isAssignableFrom(modelClass) || !modelClass.isInterface()) {
            log.error(new RemoteServiceException("model of service " + name + " is not valid"));
        }

        int port = Integer.parseInt(wrapper.getBindPort());

        @SuppressWarnings("unchecked") ServiceInfo serviceInfo = new ServiceInfo(
                name, port, (Class<? extends RemoteService>) modelClass
        );

        return serviceInfo;
    }

    public Optional<RemoteService> findInstance(ServiceInfo serviceInfo) {
        return Optional.ofNullable(servicesImplements.get(serviceInfo));
    }

    public void registerService(ServiceInfo serviceInfo, RemoteService remoteService) {
        beansService.inject(remoteService);
        servicesImplements.put(serviceInfo, remoteService);

        Class<? extends RemoteService> serviceClass = remoteService.getClass();

        new HashSet<>(subscriptionsOnRegitstrationMap.keySet())
                .stream()
                .filter(cls -> serviceClass.isAssignableFrom(cls) || cls.isAssignableFrom(serviceClass))
                .forEach(modelClass -> {

                    Consumer<RemoteService> consumer = (Consumer<RemoteService>) subscriptionsOnRegitstrationMap.remove(modelClass);
                    if (consumer != null) {
                        consumer.accept(remoteService);
                    }
                });
    }

    private ModuleID getModuleID(Class<? extends RemoteModule> cls) {
        ModuleID moduleID = null;
        try {
            RemoteModule<?> module = cls.newInstance();
            moduleID = module.getId();
        } catch (InstantiationException | IllegalAccessException exception) {
            log.error(new RemoteServiceException(exception));
        }
        ;

        return moduleID;
    }

    private ModuleFactory createModuleFactory(XMLServiceModuleDescriptor wrapper) {
        Class<?> targetClass = null;
        Class<?> configClass = null;

        try {
            ClassLoader classLoader = getClass().getClassLoader();

            targetClass = classLoader.loadClass(wrapper.getTargetClass());
            configClass = classLoader.loadClass(wrapper.getConfigClass());
        } catch (ClassNotFoundException exception) {
            log.error(new RemoteServiceException(exception));
        }

        final Class<? extends RemoteModule> checkedModuleClass = targetClass.asSubclass(RemoteModule.class);
        final ModuleID moduleID = getModuleID(checkedModuleClass);

        Class<?> finalConfigClass = configClass;
        Function<ServiceInfo, RemoteModule<?>> factoryFunc = (serviceInfo) -> {
            try {
                RemoteModule<?> module = checkedModuleClass.newInstance();
                beansService.inject(module);

                Method bindMethod = Arrays.stream(checkedModuleClass.getMethods())
                        .filter(method -> method.getName().equals("bind"))
                        .findFirst()
                        .orElse(null);

                bindMethod.invoke(module, xmlConfiguration, serviceInfo, finalConfigClass);

                return (RemoteModule<?>) module;
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException exception) {
                log.error(new RemoteServiceException(exception));
                return null;
            }
        };

        return new ModuleFactory(moduleID, factoryFunc);
    }

    private void registerModule(XMLServiceModuleDescriptor wrapper) {
        ModuleFactory moduleFactory = createModuleFactory(wrapper);
        ModuleID id = moduleFactory.getId();

        log.info("{} was registered", id);

        modulesFactories.put(id, moduleFactory);
    }
}

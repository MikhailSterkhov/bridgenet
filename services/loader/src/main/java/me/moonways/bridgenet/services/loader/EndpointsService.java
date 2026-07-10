package me.moonways.bridgenet.services.loader;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.assembly.ResourcesAssembly;
import me.moonways.bridgenet.assembly.ResourcesTypes;
import me.moonways.bridgenet.services.loader.endpoint.EndpointController;
import me.moonways.bridgenet.services.loader.xml.XMLServicesConfigDescriptor;
import me.moonways.bridgenet.services.loader.xml.XmlServiceInfoDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j2
@Autobind
public final class EndpointsService {

    @Getter
    private XMLServicesConfigDescriptor xmlConfiguration;

    @Getter
    private final Map<String, ServiceInfo> servicesInfos = Collections.synchronizedMap(new HashMap<>());
    @Getter
    private final Map<ServiceInfo, Object> servicesImplements = Collections.synchronizedMap(new HashMap<>());

    @Inject
    private BeansService beansService;
    @Inject
    private ResourcesAssembly assembly;

    @Getter
    private final EndpointController endpointController = new EndpointController();

    private final List<Runnable> boundAllSubscribers = new ArrayList<>();

    @PostConstruct
    void init() {
        beansService.inject(endpointController);
    }

    public void initConfig() {
        xmlConfiguration = assembly.readXmlAtEntity(ResourcesTypes.SERVICES_XML, XMLServicesConfigDescriptor.class);

        log.debug("Parsed services XML-configuration content: {}", xmlConfiguration);

        List<XmlServiceInfoDescriptor> xmlServicesList = xmlConfiguration.getServicesList();
        if (xmlServicesList != null) {
            initXmlServices(xmlServicesList);
        }
    }

    public void initEndpointsController() {
        endpointController.injectInternalComponents();
        endpointController.findEndpoints();
    }

    public void bindEndpoints() {
        log.debug("Binding services endpoints...");
        endpointController.bindEndpoints();
    }

    public void unbindEndpoints() {
        log.debug("Unbinding services endpoints...");
        endpointController.unbindEndpoints();

        servicesInfos.clear();
        servicesImplements.clear();
    }

    private void initXmlServices(List<XmlServiceInfoDescriptor> xmlServicesList) {
        log.info("Registering §2{} §rservice descriptions", xmlServicesList.size());

        for (XmlServiceInfoDescriptor xmlService : xmlServicesList) {
            log.debug("Registering new service: §f{} §r(class={})", xmlService.getName(), xmlService.getModelPath());
            servicesInfos.put(xmlService.getName().toLowerCase(), createServiceInfo(xmlService));
        }
    }

    private ServiceInfo createServiceInfo(XmlServiceInfoDescriptor descriptor) {
        String name = descriptor.getName();
        try {
            Class<?> modelClass = getClass().getClassLoader().loadClass(descriptor.getModelPath());
            if (!modelClass.isInterface()) {
                throw new RemoteServiceException("model of service " + name + " is not an interface");
            }
            return new ServiceInfo(name, modelClass);
        } catch (ClassNotFoundException exception) {
            throw new RemoteServiceException("model class of service " + name + " is not found", exception);
        }
    }

    public Optional<Object> findInstance(ServiceInfo serviceInfo) {
        return Optional.ofNullable(servicesImplements.get(serviceInfo));
    }

    public void registerService(ServiceInfo serviceInfo, Object serviceInstance) {
        beansService.inject(serviceInstance);
        servicesImplements.put(serviceInfo, serviceInstance);

        if (servicesImplements.size() == servicesInfos.size()) {
            boundAllSubscribers.forEach(Runnable::run);
        }
    }

    public void unregisterService(ServiceInfo serviceInfo) {
        servicesImplements.remove(serviceInfo);
    }

    public void subscribeExportedAll(Runnable runnable) {
        boundAllSubscribers.add(runnable);
    }
}

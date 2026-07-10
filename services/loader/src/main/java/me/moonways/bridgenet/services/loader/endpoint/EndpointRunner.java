package me.moonways.bridgenet.services.loader.endpoint;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.assembly.util.StreamToStringUtils;
import me.moonways.bridgenet.services.loader.EndpointsService;
import me.moonways.bridgenet.services.loader.ServiceInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Log4j2
public class EndpointRunner {

    private static final String URL_SPEC_FORMAT = "jar:file:%s!/";

    private final Map<Endpoint, Class<?>> servicesImplementsMap = Collections.synchronizedMap(new HashMap<>());

    @Inject
    private EndpointsService endpointsService;
    @Inject
    private BeansService beansService;

    public void start(Endpoint endpoint) {
        String name = endpoint.getServiceInfo().getName();

        if (!validate(endpoint)) {
            log.warn("§6Endpoint '{}' integration aborted: §eFailed validation", name);
            return;
        }

        bind(endpoint);
    }

    public void stop(Endpoint endpoint) {
        unbind(endpoint);
    }

    @SneakyThrows
    private boolean validate(Endpoint endpoint) {
        final String name = endpoint.getServiceInfo().getName();
        final EndpointConfig config = endpoint.getConfig();

        Path applicationJarPath = endpoint.getPath().resolve(config.getJar());

        if (!Files.exists(applicationJarPath) || StreamToStringUtils.toBytesBySize(new FileInputStream(applicationJarPath.toFile())).length == 0) {
            log.error("§4Application runner file '{}' for '{}' endpoint is not found", applicationJarPath, name);
            return false;
        }

        Class<?> interfaceClass = endpoint.getServiceInfo().getModelClass();
        Class<?> serviceImplementClass = findServiceImplementClass(name, applicationJarPath, interfaceClass);

        if (serviceImplementClass == null) {
            log.error("§4Founded endpoint '{}' implementation is not implement from '{}'", name, interfaceClass.getName());
            return false;
        }

        servicesImplementsMap.put(endpoint, serviceImplementClass);
        return true;
    }

    @SuppressWarnings("resource")
    private Class<?> findServiceImplementClass(String endpointName, Path applicationJarPath, Class<?> interfaceClass) {
        File file = applicationJarPath.toFile();

        try (JarFile jarFile = new JarFile(file)) {
            Enumeration<JarEntry> enumeration = jarFile.entries();

            URL[] urls = {new URL(String.format(URL_SPEC_FORMAT, file.getAbsolutePath()))};
            URLClassLoader classLoader = URLClassLoader.newInstance(urls, interfaceClass.getClassLoader());

            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();

                if (entry.isDirectory() || !entry.getName().endsWith(".class") || !entry.getName().contains("moonways") || entry.getName().contains("api"))
                    continue;

                String className = entry.getName()
                        .substring(0, entry.getName().length() - ".class".length())
                        .replace("/", ".");

                Class<?> serviceImplementClass = classLoader.loadClass(className);

                if (interfaceClass.isAssignableFrom(serviceImplementClass)) {
                    return serviceImplementClass;
                }
            }
        } catch (IOException | ClassNotFoundException exception) {
            log.error("§4Cannot be find endpoint '{}' implement class: §c{}", endpointName, exception.toString());
        }

        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void bind(Endpoint endpoint) {
        ServiceInfo serviceInfo = endpoint.getServiceInfo();
        Class<?> serviceImplementClass = servicesImplementsMap.remove(endpoint);

        try {
            Object serviceInstance = serviceImplementClass.getConstructor().newInstance();

            log.info("Binding endpoint '{}' from §3{} §rto §9{}", serviceInfo.getName(),
                    serviceInfo.getModelClass().getSimpleName(), serviceInstance.getClass().getSimpleName());

            beansService.bind((Class) serviceInfo.getModelClass(), serviceInstance);
            endpointsService.registerService(serviceInfo, serviceInstance);

            if (serviceInstance instanceof EndpointServiceObject) {
                ((EndpointServiceObject) serviceInstance).init(endpoint);
            }
        } catch (ReflectiveOperationException exception) {
            log.error("§4Cannot bind an endpoint '{}'", serviceInfo.getName(), exception);
        }
    }

    private void unbind(Endpoint endpoint) {
        ServiceInfo serviceInfo = endpoint.getServiceInfo();

        log.info("Unbinding endpoint '{}' from §3{}", serviceInfo.getName(), serviceInfo.getModelClass().getSimpleName());
        beansService.unbind(serviceInfo.getModelClass());
        endpointsService.unregisterService(serviceInfo);
    }
}

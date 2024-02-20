package me.moonways.bridgenet.bootstrap;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.api.util.thread.Threads;
import me.moonways.bridgenet.bootstrap.hook.ApplicationBootstrapHook;
import me.moonways.bridgenet.bootstrap.hook.BootstrapHookContainer;
import me.moonways.bridgenet.bootstrap.hook.BootstrapHookPriority;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

@Log4j2
public class AppBootstrap {

    @Getter
    private final BootstrapHookContainer hooksContainer = new BootstrapHookContainer();
    @Getter
    private final BeansService beansService = new BeansService();

    private void processBootstrapHooks(@NotNull BootstrapHookPriority priority) {
        Collection<ApplicationBootstrapHook> hooksByPriority = hooksContainer.findOrderedHooks(priority);
        if (hooksByPriority == null)
            return;

        log.info("AppBootstrap.processBootstrapHooks() => begin: (priority={});", priority);

        hooksByPriority.forEach(hook -> {

            String namespace = hooksContainer.findHookName(hook.getClass());

            hook.onBefore();
            hook.apply(this, namespace);
        });

        log.info("AppBootstrap.processBootstrapHooks() => end;");
    }

    private void startBeansActivity() {
        log.info("Starting beans service processing");

        beansService.bind(System.getProperties());

        beansService.start(BeansService.generateDefaultProperties());

        beansService.bind(this);
        beansService.inject(hooksContainer);
    }

    public void start(String[] args) {
        log.info("Running Bridgenet bootstrap process with args = {}", Arrays.toString(args));

        startBeansActivity();
        hooksContainer.bindHooks();

        processBootstrapHooks(BootstrapHookPriority.RUNNER);
        processBootstrapHooks(BootstrapHookPriority.POST_RUNNER);

        final Runtime runtime = Runtime.getRuntime();

        runtime.addShutdownHook(new Thread(this::shutdown));
    }

    public void shutdown() {
        log.info("§4Shutting down Bridgenet services");

        processBootstrapHooks(BootstrapHookPriority.PRE_SHUTDOWN);
        processBootstrapHooks(BootstrapHookPriority.SHUTDOWN);

        Threads.shutdownForceAll();

        Runtime.getRuntime().halt(0);
    }
}

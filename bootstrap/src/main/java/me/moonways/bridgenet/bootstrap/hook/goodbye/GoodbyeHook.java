package me.moonways.bridgenet.bootstrap.hook.goodbye;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.bootstrap.AppBootstrap;
import me.moonways.bridgenet.bootstrap.hook.BootstrapHook;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class GoodbyeHook extends BootstrapHook {

    @Override
    protected void postExecute(@NotNull AppBootstrap bootstrap) {
        log.info("Thanks for using and goodbye :)");
    }
}
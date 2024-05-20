package me.moonways.bridgenet.api.command.process.registration;

import me.moonways.bridgenet.api.command.uses.Command;
import me.moonways.bridgenet.api.container.MapContainerImpl;
import me.moonways.bridgenet.api.inject.Autobind;

import java.util.UUID;

@Autobind
public class CommandRegistry extends MapContainerImpl<UUID, Command> {

    public void remove(Class<?> cls) {
        for (Command command : values()) {
            if (command.getBean().getType().getRoot().equals(cls)) {
                remove(command.getInfo().getUid());
            }
        }
    }
}

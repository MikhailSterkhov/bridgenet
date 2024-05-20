package me.moonways.bridgenet.api.command.process.verification.impl.cooldown;

import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.info.CooldownInfoImpl;
import me.moonways.bridgenet.api.command.process.verification.impl.cooldown.info.ExpirationCooldownInfoImpl;
import me.moonways.bridgenet.api.container.MapContainerImpl;

import java.util.List;
import java.util.stream.Collectors;

public class CooldownContainer extends MapContainerImpl<String, List<ExpirationCooldownInfoImpl>> {

    public void add(String commandName, ExpirationCooldownInfoImpl cooldownInfo) {
        List<ExpirationCooldownInfoImpl> expirationCooldownInfoWrappers = get(commandName);

        expirationCooldownInfoWrappers.add(cooldownInfo);
    }

    public void remove(String commandName, String entityName) {
        List<ExpirationCooldownInfoImpl> cooldownInfoList = get(commandName);

        add(commandName, cooldownInfoList.stream()
                .filter(cooldownInfo -> !cooldownInfo.getEntityName().equalsIgnoreCase(entityName))
                .collect(Collectors.toList()));
    }

    public CooldownInfoImpl get(String commandName, String entityName) {
        return get(commandName).stream()
                .filter(cooldownInfo -> cooldownInfo.getEntityName().equalsIgnoreCase(entityName))
                .findFirst().orElse(null);
    }
}

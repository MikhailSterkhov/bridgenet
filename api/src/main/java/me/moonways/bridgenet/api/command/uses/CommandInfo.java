package me.moonways.bridgenet.api.command.uses;

import lombok.Getter;
import lombok.Setter;
import me.moonways.bridgenet.api.command.uses.entity.EntitySenderType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Класс, который хранит все метаданные команды.
 */
public class CommandInfo {

    @Getter
    private final UUID uid;
    @Getter
    private final String name;

    @Getter
    @Setter
    private String accessKey;

    private final List<EntitySenderType> senders = new ArrayList<>();

    public CommandInfo(String commandName, @Nullable String accessKey) {
        this.uid = UUID.nameUUIDFromBytes(commandName.getBytes());
        this.name = commandName;
        this.accessKey = accessKey;
    }

    /**
     * Добавить тип, который может выполнять команды.
     *
     * @param senderType - тип отправителя.
     */
    public void addSenderType(EntitySenderType senderType) {
        senders.add(senderType);
    }
}

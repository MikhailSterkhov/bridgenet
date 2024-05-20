package me.moonways.bridgenet.api.command.uses;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.BeanMethod;

/**
 * Класс, которые содержит данные о команде
 * для дальнейшего выполнения.
 */
@Getter
@RequiredArgsConstructor
public class Command {

    private final Bean bean;
    private final BeanMethod beanMethod;

    private final CommandInfo info;
}

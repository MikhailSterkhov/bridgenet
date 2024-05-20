package me.moonways.bridgenet.api.modern_command.object;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.BeanMethod;

@Getter
@RequiredArgsConstructor
public class ReflectCommandConfiguration {

    private final Bean bean;
    private final BeanMethod beanMethod;
}

package me.moonways.bridgenet.api.modern_command;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.service.BeansScanningService;
import me.moonways.bridgenet.api.inject.processor.TypeAnnotationProcessorResult;
import me.moonways.bridgenet.api.inject.processor.persistence.GetTypeAnnotationProcessor;
import me.moonways.bridgenet.api.inject.processor.persistence.WaitTypeAnnotationProcessor;
import me.moonways.bridgenet.api.modern_command.object.EntityCommand;
import me.moonways.bridgenet.api.modern_command.persistance.NamedCommand;

import java.util.List;

@WaitTypeAnnotationProcessor(NamedCommand.class)

public class CommandService {

    @GetTypeAnnotationProcessor
    private TypeAnnotationProcessorResult<Object> commandsResult;

    @Inject
    private BeansScanningService scanningService;

    public List<EntityCommand> create(Class<?> cls, Object object) {
        Bean bean = toBean(cls, object);
        return PersistenceCommandUtil.create(bean);
    }

    public List<EntityCommand> create(Bean bean) {
        return PersistenceCommandUtil.create(bean);
    }

    private Bean toBean(Class<?> parentCls, Object object) {
        return scanningService.createBean(parentCls, object);
    }
}

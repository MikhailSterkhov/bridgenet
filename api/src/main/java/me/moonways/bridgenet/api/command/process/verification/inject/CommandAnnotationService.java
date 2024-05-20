package me.moonways.bridgenet.api.command.process.verification.inject;

import me.moonways.bridgenet.api.command.process.verification.inject.validate.CommandAnnotationValidateRequest;
import me.moonways.bridgenet.api.command.process.verification.inject.validate.CommandAnnotationValidateResult;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.service.BeansScanningService;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.api.inject.processor.TypeAnnotationProcessorAdapter;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Сервис, выполняющий работу инжекции и обработки
 * кастомных аннотаций для команд.
 */
@Autobind
public class CommandAnnotationService {

    private final Map<Class<? extends Annotation>, CommandAnnotationHandler<?>> handlers = new HashMap<>();

    @Inject
    private BeansScanningService scanningService;

    @Inject
    private BeansService beansService;

    /**
     * Загрузить все обработчики аннотаций.
     */
    @PostConstruct
    public void inject() {
        Set<Bean> beans = scanningService.scanBeansBySuperclass(CommandAnnotationHandler.class);

        for (Bean bean : beans) {
            Class<? extends Annotation> annCls = TypeAnnotationProcessorAdapter.getGenericType(0, bean.getType().getRoot());

            CommandAnnotationHandler<?> commandAnnotationHandler = (CommandAnnotationHandler<?>) bean.getRoot();
            beansService.inject(commandAnnotationHandler);

            handlers.put(annCls, commandAnnotationHandler);
        }
    }

    /**
     * Удалить все обработчики аннотаций.
     */
    public void removeAll() {
        handlers.clear();
    }

    /**
     * Выполнить проверку валидности всех аннотаций команды на исполнение по отношению
     * к отправителю.
     *
     * @param context - врапнутый контекст выполнения команды (@CommandExecutionContext).
     */
    public List<CommandAnnotationValidateResult> validateAll(CommandWrapAnnotationContext context) {
        List<CommandAnnotationValidateResult> results = new ArrayList<>();

        for (Class<? extends Annotation> cls : handlers.keySet()) {
            if (!context.getCommand().getBeanMethod().isAnnotated(cls))
                continue;

            results.add(validate(context, cls));
        }

        return results;
    }

    /**
     * Выполнить проверку валидности аннотации команды на исполнение по отношению
     * к отправителю.
     *
     * @param context - врапнутый контекст выполнения команды (@CommandExecutionContext).
     * @param cls - класс аннотации.
     */
    private <T extends Annotation> CommandAnnotationValidateResult validate(CommandWrapAnnotationContext context, Class<T> cls) {
        T annotation = context.getCommand().getBeanMethod().getAnnotation(cls).orElse(null);

        return getHandler(cls).validate(
                CommandAnnotationValidateRequest.create(annotation, context.getCommand().getInfo(), context.toExecutionContext()));
    }

    /**
     * Предварительно загрузить все данные для команды, которая
     * использует кастомные аннотации.
     *
     * @param context - контекст аннотации команды с хранением рефлексивных данных.
     */
    public void prepare(CommandReflectAnnotationContext context) {
        for (Class<? extends Annotation> cls : handlers.keySet()) {
            if (!context.getItem().isAnnotationPresent(cls))
                continue;

            doPrepare(context, cls);
        }
    }

    /**
     * Предварительно загрузить данные для команды, которая
     * использует кастомную аннотацию.
     *
     * @param nativeCommandContext - контекст аннотации команды с хранением рефлексивных данных.
     * @param cls - класс аннотации.
     * @param <T> - тип обьекта аннотации.
     */
    private <T extends Annotation> void doPrepare(CommandReflectAnnotationContext nativeCommandContext, Class<T> cls) {
        T annotation = nativeCommandContext.getItem().getDeclaredAnnotation(cls);

        getHandler(cls).prepare(
                CommandBaseAnnotationContext.create(annotation, nativeCommandContext.getInfo()));
    }

    /**
     * Получить обработчик аннотации.
     *
     * @param cls - класс аннотации.
     * @param <T> - тип обьекта аннотации.
     */
    private <T extends Annotation> CommandAnnotationHandler<T> getHandler(Class<T> cls) {
        //noinspection unchecked
        return (CommandAnnotationHandler<T>) handlers.get(cls);
    }
}

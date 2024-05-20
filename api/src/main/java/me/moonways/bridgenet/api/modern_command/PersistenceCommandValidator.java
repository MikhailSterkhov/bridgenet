package me.moonways.bridgenet.api.modern_command;

import lombok.experimental.UtilityClass;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.BeanMethod;
import me.moonways.bridgenet.api.modern_command.object.exception.CommandException;

import java.lang.annotation.Annotation;

@UtilityClass
public class PersistenceCommandValidator {

    private static final String ANNOTATION_NOT_FOUND_MSG = "Can't find annotation %s on %s";
    private static final String ANNOTATION_IS_EMPTY_MSG = "Empty annotation %s on %s";

    private static final String NULLABLE_BASE_METHOD_MSG = "Can't find base method on %s";

    private static final String ANNOTATION_ALIASES_PREFIX = "@Aliases";
    private static final String ANNOTATION_NAMED_COMMAND_PREFIX = "@NamedCommand";

    public void validateEmptyAliases(BeanMethod beanMethod, String[] alias) {
        String methodName = beanMethod.getRoot().getName();

        if (alias.length == 0) {
            throw new CommandException(String.format(ANNOTATION_IS_EMPTY_MSG, ANNOTATION_ALIASES_PREFIX, methodName));
        }
    }

    public void validateEmptyAliases(Bean bean, String[] alias) {
        if (alias.length == 0) {
            throw new CommandException(String.format(ANNOTATION_IS_EMPTY_MSG, ANNOTATION_NAMED_COMMAND_PREFIX, bean.getType().getRoot().getName()));
        }
    }

    public void validateMethodIsAnnotated(BeanMethod beanMethod, Class<? extends Annotation> cls) {
        String methodName = beanMethod.getRoot().getName();

        if (!beanMethod.getAnnotation(cls).isPresent()) {
            throw new CommandException(String.format(ANNOTATION_NOT_FOUND_MSG, cls.getName(), methodName));
        }
    }

    public void validateBeanIsAnnotated(Bean bean, Class<? extends Annotation> cls) {
        String clsName = bean.getType().getRoot().getName();

        if (!bean.getType().getAnnotation(cls).isPresent()) {
            throw new CommandException(String.format(ANNOTATION_NOT_FOUND_MSG, cls.getName(), clsName));
        }
    }

    public void validateNullableBaseMethod(Bean bean, BeanMethod element) {
        if (element == null) {
            throw new CommandException(String.format(NULLABLE_BASE_METHOD_MSG, bean.getType().getRoot().getName()));
        }
    }
}

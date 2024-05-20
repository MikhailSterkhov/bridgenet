package me.moonways.bridgenet.api.modern_command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.BeanMethod;
import me.moonways.bridgenet.api.modern_command.object.CommandConfiguration;
import me.moonways.bridgenet.api.modern_command.object.EntityCommand;
import me.moonways.bridgenet.api.modern_command.persistance.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@UtilityClass
public final class PersistenceCommandUtil {

    public List<EntityCommand> create(Bean bean) {
        BeanMethod beanMethod = lookupBaseMethod(bean);

        PersistenceCommandValidator.validateNullableBaseMethod(bean, beanMethod);

        List<EntityCommand> subElements = lookupSubElements(bean);
        List<EntityCommand> baseElements = new ArrayList<>();

        String[] aliases = lookupAliases(bean);

        ParseConfigurationBaseAdapter configurationAdapter = new ParseConfigurationBaseAdapter(bean);

        CommandConfiguration dampConfiguration = parseConfiguration(bean, beanMethod, configurationAdapter);

        for (String alias : aliases) {
            UUID uuid = generateUid(alias);

            CommandConfiguration preparedConfiguration = combineConfiguration(dampConfiguration, alias, aliases);

            EntityCommand baseElement = EntityCommand.builder()
                    .id(uuid)
                    .configuration(preparedConfiguration)
                    .subElements(subElements)
                    .build();

            baseElements.add(baseElement);
        }

        return baseElements;
    }

    private BeanMethod lookupBaseMethod(Bean bean) {
        return bean.getType().getAllDeclaredFunctionsByAnnotation(CommandElement.class)
                .stream()
                .filter(PersistenceCommandUtil::isBaseElementType)
                .findFirst()
                .orElse(null);
    }

    private List<EntityCommand> lookupSubElements(Bean bean) {
        List<EntityCommand> entityCommands = new ArrayList<>();

        List<BeanMethod> beanMethods = bean.getType().getAllDeclaredFunctionsByAnnotation(CommandElement.class)
                .stream()
                .filter(PersistenceCommandUtil::isSubElementType)
                .collect(Collectors.toList());

        for (BeanMethod element : beanMethods) {
            ParseConfigurationSubAdapter configurationAdapter = new ParseConfigurationSubAdapter(element);

            String[] aliases = lookupAliases(element);

            CommandConfiguration dampConfiguration = parseConfiguration(bean, element, configurationAdapter);

            for (String alias : aliases) {
                UUID uuid = generateUid(alias);

                CommandConfiguration preparedConfiguration = combineConfiguration(dampConfiguration, alias, aliases);

                EntityCommand entityCommand = EntityCommand.builder()
                        .id(uuid)
                        .configuration(preparedConfiguration)
                        .build();

                entityCommands.add(entityCommand);
            }
        }
        return entityCommands;
    }

    private boolean isSubElementType(BeanMethod beanMethod) {
        Optional<CommandElement> element = beanMethod.getAnnotation(CommandElement.class);
        return element.map(commandElement -> commandElement.value().equals(CommandElementType.SUBCOMMAND)).orElse(false);
    }

    private boolean isBaseElementType(BeanMethod beanMethod) {
        Optional<CommandElement> element = beanMethod.getAnnotation(CommandElement.class);
        return element.map(commandElement -> commandElement.value().equals(CommandElementType.BASE_COMMAND)).orElse(false);
    }

    private CommandConfiguration parseConfiguration(Bean bean, BeanMethod beanMethod, ParseConfigurationAdapter<?> configurationAdapter) {
        return CommandConfiguration.builder(bean, beanMethod)
                .usage(configurationAdapter.lookupUsage())
                .description(configurationAdapter.lookupDescription())
                .permission(configurationAdapter.lookupPermission())
                .cooldown(configurationAdapter.lookupCooldown())
                .elementType(configurationAdapter.elementType)
                .build();
    }

    private CommandConfiguration combineConfiguration(CommandConfiguration configuration, String name, String[] aliases) {
        return CommandConfiguration
                .builder(configuration)
                .combine(configuration)
                .name(name)
                .aliases(aliases)
                .build();
    }

    @RequiredArgsConstructor
    public abstract class ParseConfigurationAdapter<Element> {

        @Getter
        private final Element element;
        private final CommandElementType elementType;

        public abstract String lookupUsage();

        public abstract String lookupDescription();

        public abstract String lookupPermission();

        public abstract long lookupCooldown();
    }

    final class ParseConfigurationBaseAdapter extends ParseConfigurationAdapter<Bean> {

        public ParseConfigurationBaseAdapter(Bean bean) {
            super(bean, CommandElementType.BASE_COMMAND);
        }

        @Override
        public String lookupUsage() {
            return PersistenceCommandUtil.lookupUsage(getElement());
        }

        @Override
        public String lookupDescription() {
            return PersistenceCommandUtil.lookupDescription(getElement());
        }

        @Override
        public String lookupPermission() {
            return PersistenceCommandUtil.lookupPermission(getElement());
        }

        @Override
        public long lookupCooldown() {
            return PersistenceCommandUtil.lookupCooldown(getElement());
        }
    }

    final class ParseConfigurationSubAdapter extends ParseConfigurationAdapter<BeanMethod> {

        public ParseConfigurationSubAdapter(BeanMethod beanMethod) {
            super(beanMethod, CommandElementType.SUBCOMMAND);
        }

        @Override
        public String lookupUsage() {
            return PersistenceCommandUtil.lookupUsage(getElement());
        }

        @Override
        public String lookupDescription() {
            return PersistenceCommandUtil.lookupDescription(getElement());
        }

        @Override
        public String lookupPermission() {
            return PersistenceCommandUtil.lookupPermission(getElement());
        }

        @Override
        public long lookupCooldown() {
            return PersistenceCommandUtil.lookupCooldown(getElement());
        }
    }

    private UUID generateUid(String value) {
        return UUID.fromString(value);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private String[] lookupAliases(BeanMethod method) {
        Class<Aliases> cls = Aliases.class;

        PersistenceCommandValidator.validateMethodIsAnnotated(method, cls);

        String[] alias = method.getAnnotation(cls)
                .get()
                .value();

        PersistenceCommandValidator.validateEmptyAliases(method, alias);

        return alias;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private String[] lookupAliases(Bean bean) {
        Class<NamedCommand> cls = NamedCommand.class;

        PersistenceCommandValidator.validateBeanIsAnnotated(bean, cls);

        String[] alias = bean.getType()
                .getAnnotation(cls)
                .get()
                .value();

        PersistenceCommandValidator.validateEmptyAliases(bean, alias);

        return alias;
    }

    private String lookupUsage(Bean bean) {
        Optional<Usage> usage = bean.getType().getAnnotation(Usage.class);

        return usage.map(Usage::value).orElse(null);
    }

    private String lookupUsage(BeanMethod beanMethod) {
        Optional<Usage> usage = beanMethod.getAnnotation(Usage.class);

        return usage.map(Usage::value).orElse(null);
    }

    private String lookupDescription(BeanMethod beanMethod) {
        Optional<Description> description = beanMethod.getAnnotation(Description.class);

        return description.map(Description::value).orElse(null);
    }

    private String lookupDescription(Bean bean) {
        Optional<Description> description = bean.getType().getAnnotation(Description.class);

        return description.map(Description::value).orElse(null);
    }

    private String lookupPermission(Bean bean) {
        Optional<Permission> permission = bean.getType().getAnnotation(Permission.class);

        return permission.map(Permission::value).orElse(null);
    }

    private String lookupPermission(BeanMethod beanMethod) {
        Optional<Permission> permission = beanMethod.getAnnotation(Permission.class);

        return permission.map(Permission::value).orElse(null);
    }

    private long lookupCooldown(Bean bean) {
        Optional<Cooldown> cooldown = bean.getType().getAnnotation(Cooldown.class);

        return cooldown.map(Cooldown::value).orElse(0L);
    }

    private long lookupCooldown(BeanMethod beanMethod) {
        Optional<Cooldown> cooldown = beanMethod.getAnnotation(Cooldown.class);

        return cooldown.map(Cooldown::value).orElse(0L);
    }
}

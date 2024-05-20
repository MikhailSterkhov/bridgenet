package me.moonways.bridgenet.api.modern_command.object;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.BeanMethod;
import me.moonways.bridgenet.api.modern_command.CommandElementType;

import java.util.ArrayList;
import java.util.List;

@Getter
public class CommandConfiguration extends ReflectCommandConfiguration {

    private final String name;
    private final String[] aliases;

    private final String usage;
    private final String description;
    private final String permission;

    private final long cooldown;

    private final CommandElementType elementType;

    private final List<CommandExecutionEntityType> allowedEntities = new ArrayList<>();

    public CommandConfiguration(Bean bean, BeanMethod beanMethod, String name, String[] aliases, String usage,
                                String description, String permission, long cooldown, CommandElementType elementType) {
        super(bean, beanMethod);

        this.name = name;
        this.aliases = aliases;
        this.usage = usage;
        this.description = description;
        this.permission = permission;
        this.cooldown = cooldown;
        this.elementType = elementType;
    }

    public static Builder builder(Bean bean, BeanMethod beanMethod) {
        return new Builder(bean, beanMethod);
    }

    public static Builder builder(CommandConfiguration configuration) {
        return new Builder(configuration.getBean(), configuration.getBeanMethod());
    }

    public void grantAccess(CommandExecutionEntityType entityType) {
        allowedEntities.add(entityType);
    }

    public void denyAccess(CommandExecutionEntityType entityType) {
        allowedEntities.remove(entityType);
    }

    public boolean isExternalElement() {
        return elementType.equals(CommandElementType.BASE_COMMAND);
    }

    public boolean isInternalElement() {
        return elementType.equals(CommandElementType.SUBCOMMAND);
    }

    @RequiredArgsConstructor
    public static class Builder {

        private final Bean bean;
        private final BeanMethod beanMethod;

        private String name;
        private String[] aliases;

        private String usage;
        private String description;
        private String permission;

        private long cooldown;

        private CommandElementType elementType;

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Builder combine(CommandConfiguration configuration) {
            this.name = configuration.name;
            this.aliases = configuration.aliases;
            this.usage = configuration.usage;
            this.description = configuration.description;
            this.elementType = configuration.elementType;
            this.permission = configuration.permission;
            this.cooldown = configuration.cooldown;
            return this;
        }

        public Builder aliases(String[] value) {
            this.aliases = value;
            return this;
        }

        public Builder usage(String value) {
            this.usage = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder permission(String value) {
            this.permission = value;
            return this;
        }

        public Builder cooldown(long value) {
            this.cooldown = value;
            return this;
        }

        public Builder elementType(CommandElementType value) {
            this.elementType = value;
            return this;
        }

        public CommandConfiguration build() {
            return new CommandConfiguration(bean, beanMethod, name, aliases, usage, description, permission, cooldown, elementType);
        }
    }
}

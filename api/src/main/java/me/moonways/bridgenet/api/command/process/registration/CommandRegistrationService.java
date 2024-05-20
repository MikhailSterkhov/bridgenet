package me.moonways.bridgenet.api.command.process.registration;

import me.moonways.bridgenet.api.command.uses.Command;
import me.moonways.bridgenet.api.command.uses.CommandInfo;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.bean.Bean;
import me.moonways.bridgenet.api.inject.bean.BeanMethod;
import me.moonways.bridgenet.api.inject.bean.service.BeansScanningService;
import me.moonways.bridgenet.api.command.CommandPermission;
import me.moonways.bridgenet.api.command.GeneralCommand;
import me.moonways.bridgenet.api.command.SubCommand;
import me.moonways.bridgenet.api.command.exception.CommandException;
import me.moonways.bridgenet.api.command.process.verification.inject.CommandAnnotationService;
import me.moonways.bridgenet.api.command.process.verification.inject.CommandReflectAnnotationContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Autobind
public class CommandRegistrationService {

    @Inject
    private CommandRegistry registry;

    @Inject
    private BeansScanningService scanningService;

    @Inject
    private CommandAnnotationService commandAnnotationService;

    public void register(Bean bean) {
        List<Command> generalCommands = registerGeneral(bean);

        for (Command generalCommand : generalCommands) {
            String generalCommandName = generalCommand.getInfo().getName();

            registerSub(generalCommandName, bean);
        }
    }

    public void register(Class<?> parentCls, Object parent) {
        Bean bean = scanningService.createBean(parentCls, parent);
        register(bean);
    }

    private List<Command> registerGeneral(Bean bean) {
        List<Command> generalCommands = createGeneralCommands(bean);

        for (Command general : generalCommands) {
            prepare(general);

            registry.add(general.getInfo().getUid(), general);
        }

        return generalCommands;
    }

    private void registerSub(String generalCommandName, Bean bean) {
        List<Command> commands = createSubcommands(generalCommandName, bean);

        for (Command command : commands) {
            prepare(command);

            registry.add(command.getInfo().getUid(), command);
        }
    }

    private Command createCommand(Bean bean, BeanMethod beanMethod, String commandName, @Nullable String accessKey) {
        return createCommand(bean, beanMethod, createCommandInfo(commandName, accessKey));
    }

    private Command createCommand(Bean bean, BeanMethod beanMethod, CommandInfo commandInfo) {
        return new Command(bean, beanMethod, commandInfo);
    }


    private List<Command> createGeneralCommands(Bean bean) {
        BeanMethod beanMethod = bean.getType().getAllDeclaredFunctionsByAnnotation(GeneralCommand.class)
                .stream().findFirst().orElseThrow(CommandException::new);

        return createGeneralCommands(bean, beanMethod);
    }

    private List<Command> createGeneralCommands(Bean bean, BeanMethod beanMethod) {
        String accessKey = getAccessKey(beanMethod);

        return beanMethod.getAnnotation(GeneralCommand.class)
                .map(GeneralCommand::value)
                .map(values -> Arrays
                        .stream(values)
                        .map(value -> createCommand(bean, beanMethod, value, accessKey))
                        .collect(Collectors.toList()))
                .orElse(null);
    }

    private List<Command> createSubcommands(String generalCommandName, Bean bean) {
        List<Command> commands = new ArrayList<>();

        for (BeanMethod beanMethod : bean.getType().getAllDeclaredFunctionsByAnnotation(SubCommand.class)) {
            commands.addAll(createSubcommands(generalCommandName, bean, beanMethod));
        }

        return commands;
    }

    private List<Command> createSubcommands(String generalCommandName, Bean bean, BeanMethod beanMethod) {
        String accessKey = getAccessKey(beanMethod);

        return beanMethod.getAnnotation(SubCommand.class)
                .map(SubCommand::value)
                .map(Arrays::asList)
                .map(list -> list
                        .stream()
                        .map(alias -> createCommand(bean, beanMethod, appendFullAlias(generalCommandName, alias), accessKey))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private CommandInfo createCommandInfo(String name, @Nullable String accessKey) {
        return new CommandInfo(name, accessKey);
    }

    private String getAccessKey(BeanMethod beanMethod) {
        return beanMethod.getAnnotation(CommandPermission.class)
                .map(CommandPermission::value)
                .orElse(null);
    }

    public void unregisterAll() {
        registry.removeAll();
        commandAnnotationService.removeAll();
    }

    public Collection<Command> findAll() {
        return registry.values();
    }

    public void unregister(Class<?> cls) {
        registry.remove(cls);
    }

    private void prepare(Command command) {
        Method root = command.getBeanMethod().getRoot();

        commandAnnotationService.prepare(CommandReflectAnnotationContext.create(root, command.getInfo()));
    }

    private String appendFullAlias(String generalCommandName, String subcommandName) {
        return generalCommandName + subcommandName;
    }
}

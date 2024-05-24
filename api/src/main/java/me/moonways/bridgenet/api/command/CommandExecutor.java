package me.moonways.bridgenet.api.command;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.command.annotation.Command;
import me.moonways.bridgenet.api.command.annotation.MatcherExecutor;
import me.moonways.bridgenet.api.command.annotation.MentorExecutor;
import me.moonways.bridgenet.api.command.annotation.ProducerExecutor;
import me.moonways.bridgenet.api.command.children.definition.CommandMentorChild;
import me.moonways.bridgenet.api.command.children.definition.CommandProducerChild;
import me.moonways.bridgenet.api.command.exception.CommandExecutionException;
import me.moonways.bridgenet.api.command.option.CommandParameterMatcher;
import me.moonways.bridgenet.api.command.sender.EntityCommandSender;
import me.moonways.bridgenet.api.command.wrapper.WrappedCommand;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.api.inject.bean.service.BeansService;
import me.moonways.bridgenet.api.inject.processor.TypeAnnotationProcessorResult;
import me.moonways.bridgenet.api.inject.processor.persistence.GetTypeAnnotationProcessor;
import me.moonways.bridgenet.api.inject.processor.persistence.WaitTypeAnnotationProcessor;
import me.moonways.bridgenet.api.proxy.AnnotationInterceptor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Autobind
@WaitTypeAnnotationProcessor(Command.class)
public final class CommandExecutor {

    private static final String MENTOR_CHILD_NOT_FOUND_MESSAGE = "Couldn't find @MentorExecutor in command '/{}'";

    private final InternalCommandFactory factory = new InternalCommandFactory();

    @Inject
    private CommandRegistry registry;
    @Inject
    private AnnotationInterceptor interceptor;
    @Inject
    private BeansService beansService;

    @GetTypeAnnotationProcessor
    private TypeAnnotationProcessorResult<Object> commandsResult;

    @PostConstruct
    private void init() {
        beansService.onBinding(Command.class, () -> {

            List<Object> objects = commandsResult.toList();

            objects.forEach(registry::registerCommand);
        });
    }

    public void execute(@NotNull EntityCommandSender sender, @NotNull String label) throws CommandExecutionException {
        String name = factory.findNameByLabel(label);
        String[] arguments = factory.copyArgumentsOfRange(factory.findArgumentsByLabel(label));

        WrappedCommand wrapper = registry.getCommandWrapper(name);

        if (wrapper == null) {
            throw new CommandExecutionException("Label cannot be contains command was exists");
        }

        CommandDescriptor descriptor = new CommandDescriptor(name, wrapper.getPermission(), wrapper.getName(), "", Collections.emptyList());
        CommandSession mentorSession = factory.createSession(descriptor, wrapper.getHelpMessageView(), sender, arguments);

        fireOption(wrapper, mentorSession);
    }

    private void fireOption(WrappedCommand wrapper, CommandSession session) {
        EntityCommandSender sender = session.getSender();
        List<CommandParameterMatcher> optionList = wrapper
                .getOptionsList().stream().filter(option -> option.matches(session))
                .peek(commandOptionMatcher -> commandOptionMatcher.process(session))
                .collect(Collectors.toList());

        if (optionList.isEmpty()) {
            if (matchesPermission(sender, wrapper.getPermission()) && matchesBeforeExecute(session, wrapper)) { //if access is allowed
                if (session.arguments().isEmpty()) {
                    fireMentor(wrapper, session);
                    return;
                }

                fireProducer(wrapper, session, session.arguments().toStringArray());
            }
        }
    }

    private void fireProducer(WrappedCommand wrapper, CommandSession session, String[] args) {
        final EntityCommandSender sender = session.getSender();

        CommandProducerChild producerChild = wrapper.<CommandProducerChild>find(ProducerExecutor.class)
                .filter(producer -> producer.getName().equalsIgnoreCase(args[0].toLowerCase()) || producer.getAliases().contains(args[0].toLowerCase()))
                .findFirst()
                .orElse(null);

        if (producerChild == null) {
            fireMentor(wrapper, session);
            return;
        }

        String permission = producerChild.getPermission();

        if (permission == null || matchesPermission(sender, permission)) {
            CommandDescriptor descriptor = new CommandDescriptor(
                    producerChild.getName(),
                    producerChild.getPermission(),
                    producerChild.getUsage(),
                    producerChild.getDescription(),
                    producerChild.getAliases());

            CommandSession childSession = factory.createSession(descriptor, wrapper.getHelpMessageView(), sender, factory.copyArgumentsOfRange(args));

            invokeMethod(childSession, wrapper.getSource(), producerChild.getMethod());
        }
    }

    private void fireMentor(WrappedCommand wrapper, CommandSession session) {
        CommandMentorChild mentorChild = wrapper.<CommandMentorChild>find(MentorExecutor.class)
                .findFirst()
                .orElse(null);

        if (mentorChild == null) {
            log.error(MENTOR_CHILD_NOT_FOUND_MESSAGE, wrapper.getName());
            return;
        }

        invokeMethod(session, wrapper.getSource(), mentorChild.getMethod());
    }

    private boolean matchesPermission(EntityCommandSender sender, String permission) {
        if (permission == null) {
            return true;
        }

        boolean hasPermission = sender.hasPermission(permission);

        if (!hasPermission) {
            sender.sendMessage("§cYou do not have permission to execute this command");
        }

        return hasPermission;
    }

    private boolean matchesBeforeExecute(CommandSession session, WrappedCommand wrapper) {
        long unauthorizedMatchers = wrapper.find(MatcherExecutor.class)
                .filter(matcher -> Boolean.FALSE.equals(invokeMethod(session, wrapper.getSource(), matcher.getMethod())))
                .count();

        return unauthorizedMatchers == 0;
    }

    private Object invokeMethod(CommandSession session, Object source, Method method) {
        return interceptor.callProxiedMethod(source, method, new Object[]{session});
    }
}

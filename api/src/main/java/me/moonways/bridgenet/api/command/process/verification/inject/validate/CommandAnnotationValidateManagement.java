package me.moonways.bridgenet.api.command.process.verification.inject.validate;

import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.command.uses.Command;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.process.verification.inject.CommandAnnotationService;
import me.moonways.bridgenet.api.command.process.verification.inject.CommandWrapAnnotationContext;

import java.util.List;
import java.util.stream.Collectors;

@Autobind
public class CommandAnnotationValidateManagement {

    @Inject
    private CommandAnnotationService commandService;

    public CommandAnnotationValidateResult validate(CommandExecutionContext context, Command command) {
        List<CommandAnnotationValidateResult> results = getFailedResults(context, command);

        return results.stream().findFirst().orElse(CommandAnnotationValidateResult.ok());
    }

    private List<CommandAnnotationValidateResult> getFailedResults(CommandExecutionContext context, Command command) {
        return commandService.validateAll(CommandWrapAnnotationContext.create(context.getSender(), context.getLabel(), command))
                .stream()
                .filter(CommandAnnotationValidateResult::isFailure)
                .collect(Collectors.toList());
    }
}

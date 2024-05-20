package me.moonways.bridgenet.api.command.process.verification.impl.args;

import me.moonways.bridgenet.api.command.process.verification.inject.CommandAnnotationHandler;
import me.moonways.bridgenet.api.command.process.verification.inject.CommandBaseAnnotationContext;
import me.moonways.bridgenet.api.command.process.verification.inject.validate.CommandAnnotationValidateRequest;
import me.moonways.bridgenet.api.command.process.verification.inject.validate.CommandAnnotationValidateResult;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.command.CommandStructure;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.command.label.CommandLabelContext;
import me.moonways.bridgenet.api.command.label.regex.CommandRegex;
import me.moonways.bridgenet.api.command.label.regex.CommandRegexRegistry;

/**
 * Обработчик аргументов команды.
 * Обрабатывает все аргументы и валидирует их.
 */
public class CommandAnnotationArgumentHandler extends CommandAnnotationHandler<CommandStructure> {

    @Inject
    private CommandRegexRegistry regexRegistry;

    @Override
    public void prepare(CommandBaseAnnotationContext<CommandStructure> context) {
    }

    /**
     * Получить результат валидации исполнения команды.
     * @param request - запрос верификации.
     */
    @Override
    public CommandAnnotationValidateResult validate(CommandAnnotationValidateRequest<CommandStructure> request) {
        //CommandStructure commandStructure = request.getAnnotationContext().getAnnotation();
        //CommandArgument[] syntaxes = request.getAnnotationContext().getAnnotation().value();
//
        //CommandLabelContext labelContext = request.getExecutionContext().getLabel();
        //CommandLabelContext.Arguments arguments = labelContext.getArguments();
        //EntityCommandSender sender = request.getExecutionContext().getSender();
//
        //List<CommandAnnotationValidateResult> failedResults = new ArrayList<>();
//
        //for (CommandArgument syntax : syntaxes) {
        //    int position = syntax.position();
        //    if (position == - 1) continue;
//
        //    if (!hasPosition(arguments, position)) {
        //        return CommandAnnotationValidateResult.fail(sender, combineUsageAndDescription(commandStructure));
        //    }
//
        //    CommandRegexId annotationCommandRegexId = syntax.regexId();
//
        //    String regexId = annotationCommandRegexId.value();
        //    if (regexId.isEmpty()) {
        //        continue;
        //    }
//
        //    CommandAnnotationValidateResult validatedResult = validateResult(sender, arguments, position, annotationCommandRegexId.value());
        //    if (!validatedResult.isOk()) {
        //        failedResults.add(validatedResult);
        //    }
//
        //    return failedResults.isEmpty() ? CommandAnnotationValidateResult.ok() : failedResults.stream().findFirst().get();
        //}

        return CommandAnnotationValidateResult.ok();
    }

    /**
     * Соединить две строки использование/описание.
     *
     * @param helper - помощник команд.
     */
    private String combineUsageAndDescription(CommandStructure helper) {
        //return helper.usage() + " - " + helper.description();
        return "";
    }

    /**
     * Провалидировать аргумент на определённой позиции.
     *
     * @param sender - отправитель.
     * @param arguments - массив аргументов.
     * @param position - позиция аргумента.
     * @param regexId - id регекса(при наличии)
     */
    private CommandAnnotationValidateResult validateResult(EntityCommandSender sender, CommandLabelContext.Arguments arguments,
                                                             int position, String regexId) {
        if (hasRegex(regexId)) {
            CommandRegex commandRegex = regexRegistry.get(regexId);

            if (!matches(arguments, position, commandRegex.getValue())) {
                return CommandAnnotationValidateResult.fail(sender, commandRegex.getExceptionMsg());
            }
        }
        return CommandAnnotationValidateResult.ok();
    }

    /**
     * Проверить аргумент на регекс.
     *
     * @param arguments - массив аргументов.
     * @param position - позиция аргументов.
     * @param format - формат(регекс)
     */
    private boolean matches(CommandLabelContext.Arguments arguments, int position, String format) {
        return arguments.get(position).get().matches(format);
    }

    /**
     * Проверить зарегистрирован ли регекс под
     * определённым id.
     *
     * @param id - идентификатор регекса.
     */
    private boolean hasRegex(String id) {
        return regexRegistry.contains(id);
    }

    /**
     * Проверить существует ли аргумент в строке.
     *
     * @param arguments - массив аргументов.
     * @param position - позиция аргумента.
     */
    private boolean hasPosition(CommandLabelContext.Arguments arguments, int position) {
        return arguments.has(position);
    }
}

package me.moonways.bridgenet.api.command.process.verification.inject.validate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;

@Getter
@RequiredArgsConstructor
public class CommandAnnotationValidateResult {

    private final Type type;
    private final String message;

    public boolean isOk() {
        return type.equals(Type.OK);
    }

    public boolean isFailure() {
        return type.equals(Type.FAIL);
    }

    public static CommandAnnotationValidateResult ok() {
        return new CommandAnnotationValidateResult(Type.OK, null);
    }

    public static CommandAnnotationValidateResult fail(EntityCommandSender sender, String message) {
        sender.sendMessage(message);

        return new CommandAnnotationValidateResult(Type.FAIL, message);
    }

    public static CommandAnnotationValidateResult fail() {
        return new CommandAnnotationValidateResult(Type.FAIL, null);
    }

    public enum Type {

        OK,
        FAIL
    }
}

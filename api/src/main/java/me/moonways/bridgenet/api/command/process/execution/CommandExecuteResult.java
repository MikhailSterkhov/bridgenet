package me.moonways.bridgenet.api.command.process.execution;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.util.thread.Threads;

import java.util.concurrent.ScheduledExecutorService;

@RequiredArgsConstructor
@Getter
public final class CommandExecuteResult {

    private static final ScheduledExecutorService executorService = Threads.newSingleThreadScheduledExecutor();

    private final Type type;
    private final String value;

    public boolean isOk() {
        return type.equals(Type.OK);
    }

    public boolean isFail() {
        return type.equals(Type.FAIL);
    }

    public boolean isEmpty() {
        return type.equals(Type.EMPTY);
    }


    public static CommandExecuteResult okAsync(Runnable runnable) {
        executorService.execute(runnable);

        return new CommandExecuteResult(Type.OK, null);
    }

    public static CommandExecuteResult ok(EntityCommandSender sender, String reason) {
        sender.sendMessage(reason);

        return new CommandExecuteResult(Type.OK, reason);
    }

    public static CommandExecuteResult ok(EntityCommandSender sender, CommandExecuteResultMsg msg) {
        sender.sendMessage(msg.getValue());

        return new CommandExecuteResult(Type.OK, msg.getValue());
    }

    public static CommandExecuteResult ok() {
        return new CommandExecuteResult(Type.OK, null);
    }

    public static CommandExecuteResult fail(EntityCommandSender sender, String reason) {
        sender.sendMessage(reason);

        return new CommandExecuteResult(Type.FAIL, reason);
    }

    public static CommandExecuteResult fail(EntityCommandSender sender, CommandExecuteResultMsg reason) {
        sender.sendMessage(reason.getValue());

        return new CommandExecuteResult(Type.FAIL, reason.getValue());
    }

    public static CommandExecuteResult empty() {
        return new CommandExecuteResult(Type.EMPTY, "void type");
    }

    public enum Type {
        OK,
        FAIL,
        EMPTY
    }
}

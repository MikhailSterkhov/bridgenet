package me.moonways.bridgenet.api.modern_command.test;

import me.moonways.bridgenet.api.modern_command.CommandElementType;
import me.moonways.bridgenet.api.modern_command.object.ExecutionContext;
import me.moonways.bridgenet.api.modern_command.persistance.*;

@NamedCommand("test")
@Permission("test")
public class TestCommand {

    @CommandElement(CommandElementType.BASE_COMMAND)
    public void test(ExecutionContext context) {

    }

    @Aliases({"gitcoder", "stonlex"})
    @Description("Выполнение первой подкоманды")
    @Usage("/test gitcoder")
    @CommandElement(CommandElementType.SUBCOMMAND)
    public void sub_test(ExecutionContext context) {

    }
}

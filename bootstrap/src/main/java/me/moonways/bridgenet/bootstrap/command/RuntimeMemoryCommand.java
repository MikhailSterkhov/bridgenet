package me.moonways.bridgenet.bootstrap.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.moonways.bridgenet.api.command.Command;
import me.moonways.bridgenet.api.command.GeneralCommand;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.util.minecraft.ChatColor;

import java.text.SimpleDateFormat;
import java.util.function.Function;

@Command
public class RuntimeMemoryCommand {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy в HH:mm:ss.SSS");

    private MemoryDump previousDump = MemoryDump.fix();

    @GeneralCommand({"mem", "memory"})
    public void defaultCommand(CommandExecutionContext executionContext) {
        EntityCommandSender sender = executionContext.getSender();
        MemoryDump actualDump = MemoryDump.fix();


        sender.sendMessage("Замеры памяти на актуальный момент:");

        sender.sendMessage(getMemory(actualDump, "Максимально памяти (МБ)", MemoryDump::getMaxMemory));
        sender.sendMessage(getMemory(actualDump, "Свободно памяти (МБ)", MemoryDump::getFreeMemory));
        sender.sendMessage(getMemory(actualDump, "Используется памяти (МБ)", MemoryDump::getUsedMemory));

        sender.sendMessage("§7Последний замер был воспроизведен: §6" + DATE_FORMAT.format(previousDump.getTimestamp()));

        previousDump = actualDump;
    }

    private String getMemory(MemoryDump actualDump, String description, Function<MemoryDump, Long> typeGet) {
        long previousValue = typeGet.apply(previousDump);
        long actualValue = typeGet.apply(actualDump);

        ChatColor chatColor = actualValue > previousValue ? ChatColor.RED : actualValue == previousValue ? ChatColor.YELLOW : ChatColor.GREEN;

        String differenceSign = actualValue > previousValue ? "+" : actualValue == previousValue ? "" : "-";

        long difference = Math.abs(previousValue - actualValue);
        int differencePercent = difference == 0 ? 0 : Math.round(difference * 100f / Math.max(previousValue, actualValue));

        String left = String.format(" — %s: [%d MB §r-> %s%d MB§r]", description, previousValue, chatColor, actualValue);
        String right = String.format("Изменено: §7%s%d MB §r(%s%s%d%%§r)", differenceSign, Math.abs(previousValue - actualValue), chatColor, differenceSign, differencePercent);

        return String.format("%-60s %40s", left, right);
    }

    @Getter
    @AllArgsConstructor
    private static class MemoryDump {

        public static MemoryDump fix() {
            Runtime runtime = Runtime.getRuntime();

            long max = runtime.maxMemory() / 1024L / 1024L;
            long total = runtime.totalMemory() / 1024L / 1024L;
            long free = runtime.freeMemory() / 1024L / 1024L;
            long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;

            return new MemoryDump(max, total, free, used);
        }

        private long maxMemory;
        private long totalMemory;
        private long freeMemory;
        private long usedMemory;

        private final long timestamp = System.currentTimeMillis();
    }
}

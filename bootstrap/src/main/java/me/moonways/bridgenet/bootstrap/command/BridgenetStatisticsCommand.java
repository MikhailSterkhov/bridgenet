package me.moonways.bridgenet.bootstrap.command;

import me.moonways.bridgenet.api.command.Command;
import me.moonways.bridgenet.api.command.GeneralCommand;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Command
public class BridgenetStatisticsCommand {

    private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    @GeneralCommand({"bridgenet", "bn", "system"})
    public void defaultCommand(CommandExecutionContext executionContext) {
        EntityCommandSender sender = executionContext.getSender();

        sender.sendMessage("Метрика BridgeNet на актуальный момент:");

        printOperatingSystem(sender);
        printRuntime(sender);
        printThreads(sender);
    }

    private void printOperatingSystem(EntityCommandSender sender) {
        OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();

        sender.sendMessage("§eOperating System (OS):");

        sender.sendMessage(" — Система: §2%s §rверсии §2%s (arch %s)", bean.getName(), bean.getVersion(), bean.getArch());
        sender.sendMessage(" — Процессор: §7(threads: %d, cores: %d)", bean.getAvailableProcessors(), (bean.getAvailableProcessors() / 2));
    }

    private void printRuntime(EntityCommandSender sender) {
        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();

        sender.sendMessage("§eRuntime:");

        sender.sendMessage(" — Имя машины: §2" + bean.getName());
        sender.sendMessage(" — Bootstrap: §2" + bean.getBootClassPath());
        sender.sendMessage(" — Аргументы запуска: §2" + bean.getInputArguments());
        sender.sendMessage(" — Версия вендора: §2" + bean.getSpecVersion());
        sender.sendMessage(" — Версия VM: §2" + bean.getVmVersion());
        sender.sendMessage(" — Дата и время запуска: §2" + DATETIME_FORMAT.format(bean.getStartTime()));
        sender.sendMessage(" — Uptime: §2" + TIME_FORMAT.format(bean.getUptime()));
    }

    private void printThreads(EntityCommandSender sender) {
        sender.sendMessage("§eThreads:");

        List<Thread> threads = new ArrayList<>(Thread.getAllStackTraces().keySet());

        threads.removeIf(thread -> thread.getStackTrace().length <= 1 || thread.getStackTrace()[1].isNativeMethod());
        threads.sort(Comparator.comparingInt(thread -> thread.getState().ordinal()));

        sender.sendMessage(" — Всего обнаружено §2%d §rпотоков;", threads.size());

        for (Thread thread : threads) {
            printThread(sender, thread);
        }
    }

    private void printThread(EntityCommandSender sender, Thread thread) {
        StackTraceElement stackTraceElement = thread.getStackTrace()[1];

        sender.sendMessage(" — Поток §3%s§r §7(daemon: %s, state: %s)   §4[ %s ]",
                thread.getName(), thread.isDaemon(), thread.getState(), stackTraceElement.toString());
    }
}

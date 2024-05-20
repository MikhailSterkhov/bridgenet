package me.moonways.bridgenet.bootstrap.command;

import me.moonways.bridgenet.api.command.GeneralCommand;
import me.moonways.bridgenet.api.command.Command;
import me.moonways.bridgenet.api.command.uses.CommandExecutionContext;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.bootstrap.command.util.MetricRenderer;
import me.moonways.bridgenet.metrics.MetricType;

@Command
public class MetricsRenderCommand {

    @Inject
    private MetricRenderer renderer;

    @GeneralCommand({"metric", "metrics"})
    public void defaultCommand(CommandExecutionContext executionContext) {
        for (MetricType metricType : MetricType.values()) {
            renderer.sendRenderedMetricURL(metricType, executionContext.getSender());
        }
    }
}

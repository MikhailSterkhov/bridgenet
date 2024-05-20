
package me.moonways.bridgenet.bootstrap.command.util;

import me.moonways.bridgenet.api.command.uses.entity.EntityCommandSender;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.decorator.EnableDecorators;
import me.moonways.bridgenet.api.inject.decorator.persistence.Async;
import me.moonways.bridgenet.metrics.BridgenetMetricsLogger;
import me.moonways.bridgenet.metrics.MetricType;

@Autobind
@EnableDecorators
public class MetricRenderer {

    @Inject
    private BridgenetMetricsLogger bridgenetMetricsLogger;

    @Async
    public void sendRenderedMetricURL(MetricType metricType, EntityCommandSender sender) {
        String illustrationURL = bridgenetMetricsLogger.requestIllustrationURL(metricType);
        sender.sendMessage("Illustration of the §7\"%s\" §rmetric is prepared from the link: %s", metricType.getDisplayName(), illustrationURL);
    }
}
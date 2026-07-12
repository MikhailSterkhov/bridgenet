package me.moonways.rmap.transport;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

/** Конфиг транспорта RMAP (спека §9, §11). Дефолты — в builder'е. */
@Value
@Builder
public class RmapConfig {

    Access access;
    String appVersion;
    String clientName;

    @Builder.Default Duration keepAliveInterval = Duration.ofSeconds(15);
    @Builder.Default Duration idleTimeout = Duration.ofSeconds(45);
    @Builder.Default Duration handshakeTimeout = Duration.ofSeconds(10);
    @Builder.Default Duration callTimeout = Duration.ofSeconds(5);
    @Builder.Default int frameLimit = 8 * 1024 * 1024;
    @Builder.Default int preAuthFrameLimit = 4 * 1024;
    @Builder.Default int maxConcurrentHandshakes = 256;
    @Builder.Default int maxConnectionsPerRemote = 32;
    @Builder.Default int maxInFlightRequests = 256;
    @Builder.Default long outboundLimitBytes = 64L * 1024 * 1024;
}

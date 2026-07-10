package me.moonways.endpoint.reports;

import lombok.Getter;
import lombok.ToString;
import me.moonways.bridgenet.model.service.reports.Report;
import me.moonways.bridgenet.model.service.reports.ReportReason;
import me.moonways.bridgenet.services.loader.endpoint.EndpointServiceObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ToString
@Getter
public class ReportStub extends EndpointServiceObject implements Report {

    private static final long serialVersionUID = -3931465978117678586L;

    private final ReportReason reason;

    private final String whoReportedName;
    private final String intruderName;

    private final String comment;

    private final String serverName;

    private final long createdTimeMillis;

    public ReportStub(@NotNull ReportReason reason,
                      @NotNull String whoReportedName,
                      @NotNull String intruderName,
                      @Nullable String comment,
                      @NotNull String serverName,
                      long createdTimeMillis) {

        this.reason = reason;
        this.whoReportedName = whoReportedName;
        this.intruderName = intruderName;
        this.comment = comment;
        this.serverName = serverName;
        this.createdTimeMillis = createdTimeMillis;
    }
}

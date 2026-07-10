package me.moonways.bridgenet.model.service.reports;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ReportsServiceModel {

    Report createReport(@NotNull ReportReason reason,
                        @NotNull String whoReportedName,
                        @NotNull String intruderName,
                        @Nullable String comment,
                        @NotNull String whereServerName);

    Report createReport(@NotNull ReportReason reason,
                        @NotNull String whoReportedName,
                        @NotNull String intruderName,
                        @NotNull String whereServerName);

    List<Report> getTotalReports();

    List<ReportedPlayer> getTotalReportedPlayers();

    int getTotalReportedPlayersCount();

    int getTotalReportsCount();
}

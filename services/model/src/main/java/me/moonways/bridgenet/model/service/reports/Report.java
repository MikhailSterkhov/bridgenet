package me.moonways.bridgenet.model.service.reports;

public interface Report {

    ReportReason getReason();

    String getWhoReportedName();

    String getIntruderName();

    String getComment();

    String getServerName();

    long getCreatedTimeMillis();
}

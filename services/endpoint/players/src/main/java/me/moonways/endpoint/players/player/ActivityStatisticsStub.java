package me.moonways.endpoint.players.player;

import me.moonways.bridgenet.model.service.players.component.statistic.ActivityStatistics;
import me.moonways.bridgenet.model.service.players.component.statistic.Statistic;

import java.util.EnumMap;

public final class ActivityStatisticsStub implements ActivityStatistics {

    private final EnumMap<Statistic, Number> statisticsValuesMap
            = new EnumMap<>(Statistic.class);

    @Override
    public void reset() {
        statisticsValuesMap.clear();
    }

    @Override
    public void setInt(Statistic statistic, int value) {
        statisticsValuesMap.put(statistic, value);
    }

    @Override
    public void setLong(Statistic statistic, long value) {
        statisticsValuesMap.put(statistic, value);
    }

    @Override
    public int getInt(Statistic statistic) {
        return (int) statisticsValuesMap.get(statistic);
    }

    @Override
    public long getLong(Statistic statistic) {
        return (long) statisticsValuesMap.get(statistic);
    }
}

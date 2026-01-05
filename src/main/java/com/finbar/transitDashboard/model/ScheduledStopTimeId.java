package com.finbar.transitDashboard.model;

import java.io.Serializable;
import java.util.Objects;

public class ScheduledStopTimeId implements Serializable {
    private String tripId;
    private String stopId;

    public ScheduledStopTimeId() {}

    public ScheduledStopTimeId(String tripId, String stopId) {
        this.tripId = tripId;
        this.stopId = stopId;
    }

    // equals() and hashCode()
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledStopTimeId that = (ScheduledStopTimeId) o;
        return Objects.equals(tripId, that.tripId) &&
                Objects.equals(stopId, that.stopId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId, stopId);
    }
}


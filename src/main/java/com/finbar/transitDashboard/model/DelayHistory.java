package com.finbar.transitDashboard.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "delay_history")
@IdClass(DelayHistory.DelayHistoryId.class)
public class DelayHistory {

    @Column(name = "route_id")
    private String routeId;

    @Id
    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "delay_seconds")
    private Integer delaySeconds;

    @Id
    @Column(name = "timestamp")
    private Long timestamp;


    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(Integer delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public static class DelayHistoryId implements Serializable {
        public String tripId;
        public Long timestamp;

        public DelayHistoryId() {}

        public DelayHistoryId(String tripId, Long timestamp) {
            this.tripId = tripId;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DelayHistoryId that = (DelayHistoryId) o;
            return Objects.equals(tripId, that.tripId) &&
                    Objects.equals(timestamp, that.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tripId, timestamp);
        }
    }
}


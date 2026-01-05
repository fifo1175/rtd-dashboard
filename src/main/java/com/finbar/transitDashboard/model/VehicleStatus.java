package com.finbar.transitDashboard.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle_status")
public class VehicleStatus {
    @Id
    @Column(name = "vehicle_id")
    private String vehicleId;

    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "route_id")
    private String routeId;

    private Float latitude;
    private Float longitude;
    private Float bearing;

    @Column(name = "current_status")
    private String currentStatus;

    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "delay_seconds")
    private Integer delaySeconds;

    private Long timestamp;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;


    public String getDelayColor() {
        if (delaySeconds == null) return "UNKNOWN";  // No data
        if (delaySeconds <= 60) return "ON_TIME";   // On time (within 1 min)
        if (delaySeconds <= 300) return "MINOR_DELAY"; // 1-5 min late
        return "MAJOR_DELAY";                              // 5+ min late
    }

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

    public Float getBearing() {
        return bearing;
    }

    public void setBearing(Float bearing) {
        this.bearing = bearing;
    }

    public Float getLongitude() {
        return longitude;
    }

    public void setLongitude(Float longitude) {
        this.longitude = longitude;
    }

    public Float getLatitude() {
        return latitude;
    }

    public void setLatitude(Float latitude) {
        this.latitude = latitude;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public static class DelayHistoryId implements Serializable {
        private String tripId;
        private Long timestamp;

        public DelayHistoryId() {}

        public DelayHistoryId(String tripId, Long timestamp) {
            this.tripId = tripId;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DelayHistory.DelayHistoryId that = (DelayHistory.DelayHistoryId) o;
            return Objects.equals(tripId, that.tripId) &&
                    Objects.equals(timestamp, that.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tripId, timestamp);
        }
    }

}

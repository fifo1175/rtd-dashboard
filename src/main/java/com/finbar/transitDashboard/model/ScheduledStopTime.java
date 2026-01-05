package com.finbar.transitDashboard.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "scheduled_stop_times")
@IdClass(ScheduledStopTimeId.class)
public class ScheduledStopTime {

    @Id
    @Column(name = "trip_id")
    private String tripId;

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public Integer getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(Integer stopSequence) {
        this.stopSequence = stopSequence;
    }

    @Id
    @Column(name = "stop_id")
    private String stopId;

    @Column(name = "arrival_time")
    private String arrivalTime; // Format: "HH:MM:SS"

    @Column(name = "departure_time")
    private String departureTime;

    @Column(name = "stop_sequence")
    private Integer stopSequence;

    // Getters/setters

    // Composite key class

}




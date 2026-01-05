package com.finbar.transitDashboard.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduledStopTimeRepository extends JpaRepository<ScheduledStopTime, ScheduledStopTimeId> {
    ScheduledStopTime findByTripIdAndStopId(String tripId, String stopId);
}

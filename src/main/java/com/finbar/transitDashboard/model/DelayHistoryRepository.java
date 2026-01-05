package com.finbar.transitDashboard.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DelayHistoryRepository extends JpaRepository<DelayHistory, Long> {
    List<DelayHistory> findTop100ByRouteIdOrderByRecordedAtDesc(String routeId);
    List<DelayHistory> findTop1000ByOrderByRecordedAtDesc();

    List<DelayHistory> findByRecordedAtAfter(LocalDateTime localDateTime);
}

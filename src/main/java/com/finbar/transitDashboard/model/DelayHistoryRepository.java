package com.finbar.transitDashboard.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DelayHistoryRepository extends JpaRepository<DelayHistory, Long> {
    List<DelayHistory> findTop100ByRouteIdOrderByRecordedAtDesc(String routeId);
    List<DelayHistory> findTop1000ByOrderByRecordedAtDesc();

    // For prediction algorithm - limit to 50K most recent records for performance
    List<DelayHistory> findTop50000ByOrderByRecordedAtDesc();

    List<DelayHistory> findByRecordedAtAfter(LocalDateTime localDateTime);

    // Data retention - delete records older than specified date
    @Modifying
    @Transactional
    @Query("DELETE FROM DelayHistory d WHERE d.recordedAt < :cutoffDate")
    int deleteByRecordedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}

package com.finbar.transitDashboard.service;

import com.finbar.transitDashboard.model.DelayHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service responsible for maintaining database performance by cleaning up old data.
 * Keeps only the most recent 7 days of delay history to prevent database bloat
 * and ensure consistent query performance for the demo.
 */
@Service
public class DataRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(DataRetentionService.class);
    private static final int RETENTION_DAYS = 7;

    @Autowired
    private DelayHistoryRepository delayHistoryRepo;

    /**
     * Scheduled task that runs daily at 2:00 AM to clean up old delay history records.
     * Deletes all records older than 7 days to maintain optimal database size and performance.
     *
     * Schedule: 0 0 2 * * * = second minute hour day month weekday
     * Runs at 2:00:00 AM every day
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldDelayHistory() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(RETENTION_DAYS);

            logger.info("Starting scheduled cleanup of delay history older than {} days (before {})",
                       RETENTION_DAYS, cutoffDate);

            int deletedCount = delayHistoryRepo.deleteByRecordedAtBefore(cutoffDate);

            logger.info("Cleanup complete. Deleted {} old delay history records", deletedCount);

            // Log database health metrics
            long remainingRecords = delayHistoryRepo.count();
            logger.info("Remaining delay history records: {}", remainingRecords);

        } catch (Exception e) {
            logger.error("Error during delay history cleanup", e);
        }
    }

    /**
     * Manual cleanup method that can be called on-demand.
     * Useful for pre-demo optimization.
     *
     * @param days Number of days of history to retain
     * @return Number of records deleted
     */
    public int cleanupOlderThan(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        logger.info("Manual cleanup: Deleting delay history older than {} days (before {})", days, cutoffDate);

        int deletedCount = delayHistoryRepo.deleteByRecordedAtBefore(cutoffDate);

        logger.info("Manual cleanup complete. Deleted {} records", deletedCount);
        return deletedCount;
    }
}

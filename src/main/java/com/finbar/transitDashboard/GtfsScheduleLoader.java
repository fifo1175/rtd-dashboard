package com.finbar.transitDashboard;

import com.finbar.transitDashboard.model.ScheduledStopTime;
import com.finbar.transitDashboard.model.ScheduledStopTimeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class GtfsScheduleLoader {

    @Autowired
    private ScheduledStopTimeRepository scheduleRepo;

    @PostConstruct
    public void loadSchedule() {
        // Only load if database is empty
        if (scheduleRepo.count() > 0) {
            System.out.println("Schedule already loaded, skipping...");
            return;
        }

        System.out.println("Loading GTFS schedule from stop_times.txt...");

        try {
            Path path = Paths.get("gtfs_schedule/stop_times.txt");
            List<String> lines = Files.readAllLines(path);

            // Skip header
            for (int i = 1; i < lines.size(); i++) {
                String[] fields = lines.get(i).split(",");

                ScheduledStopTime sst = new ScheduledStopTime();
                sst.setTripId(fields[0].trim());
                sst.setArrivalTime(fields[1].trim());
                sst.setDepartureTime(fields[2].trim());
                sst.setStopId(fields[3].trim());
                sst.setStopSequence(Integer.parseInt(fields[4].trim()));

                scheduleRepo.save(sst);

                if (i % 10000 == 0) {
                    System.out.println("Loaded " + i + " scheduled stop times...");
                }
            }

            System.out.println("✅ Loaded " + scheduleRepo.count() + " scheduled stop times");

        } catch (Exception e) {
            System.err.println("Error loading schedule: " + e.getMessage());
        }
    }
}


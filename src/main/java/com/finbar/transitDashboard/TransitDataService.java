package com.finbar.transitDashboard;

import com.finbar.transitDashboard.model.*;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransitDataService {

    @Autowired
    private VehicleStatusRepository vehicleRepo;

    @Autowired
    private DelayHistoryRepository delayHistoryRepo;

    @Autowired
    private ScheduledStopTimeRepository stopTimeRepo;

    private VehicleStatus saveVehicleStatus(VehicleStatus vehicleStatus) {
        return vehicleRepo.save(vehicleStatus);
    }

    private List<VehicleStatus> fetchVehicleStatusList() {
        return vehicleRepo.findAll();
    }

    //@Autowired
    //private SimpMessagingTemplate websocket;

    private Map<String, Integer> tripDelays = new ConcurrentHashMap<>();

    @KafkaListener(topics = "trip-updates", groupId = "trip-update-consumer-group")
    public void processTripUpdate(byte[] data) throws Exception {
        System.out.println("TRIP UPDATE MESSAGE RECEIVED");
        try {
            FeedMessage tripUpdateFeed = FeedMessage.parseFrom(data);

            int delayedCount = 0;
            int onTimeCount = 0;
            int earlyCount = 0;

            for (FeedEntity entity : tripUpdateFeed.getEntityList()) {
                if (!entity.hasTripUpdate()) continue;

                TripUpdate update = entity.getTripUpdate();
                String tripId = update.getTrip().getTripId();
                String routeId = update.getTrip().getRouteId();

                if (update.getStopTimeUpdateCount() == 0) {
                    continue;
                }

                // ⭐ Print the FIRST stop time update to see what's in it
                TripUpdate.StopTimeUpdate firstStop = update.getStopTimeUpdate(0);

                if (!firstStop.hasArrival() || !firstStop.getArrival().hasTime()) {
                    continue;
                }

                long predictedTime = firstStop.getArrival().getTime();
                String stopId = firstStop.getStopId();

                // look up scheduled time from db
                ScheduledStopTime scheduled = stopTimeRepo.findByTripIdAndStopId(tripId, stopId);

                if (scheduled == null) {
                    System.out.println("NO schedule found for trip " + tripId + " stop " + stopId);
                    continue;
                }

                 // ⭐ Calculate delay
                int delay = calculateDelay(predictedTime, scheduled.getArrivalTime(), update.getTimestamp());

                tripDelays.put(tripId, delay);

                // Count delays
                if (delay > 180) {
                    delayedCount++;
                    System.out.println("🚨 DELAYED: Route " + routeId + " → " + delay/60.0 + " minutes");
                } else if (delay < -180) {
                    earlyCount++;
                    System.out.println("🏃 EARLY: Route " + routeId + " → " + delay/60.0 + " minutes");
                } else {
                    onTimeCount++;
                    System.out.println("✅ ON TIME: Route " + routeId + " → " + delay/60.0 + " minutes");
                }

                // Save to history
                DelayHistory delayHistory = new DelayHistory();
                delayHistory.setTripId(tripId);
                delayHistory.setRouteId(routeId);
                delayHistory.setDelaySeconds(delay);
                delayHistory.setTimestamp(update.getTimestamp());
                delayHistoryRepo.save(delayHistory);

            }

           System.out.println("\n========== SUMMARY ==========");
            System.out.println("✅ On Time: " + onTimeCount);
            System.out.println("🚨 Delayed (>3 min): " + delayedCount);
            System.out.println("🏃 Early (<-3 min): " + earlyCount);
            System.out.println("=============================\n");

        } catch (Exception ex) {
            System.err.println("Error processing trip update: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private int calculateDelay(long predictedTime, String scheduledTime, long updateTimestamp) {
        try {
            // Validate scheduled time format
            if (!scheduledTime.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                System.err.println("⚠️ Invalid scheduled time format: " + scheduledTime);
                return 0;
            }

            // Parse scheduled time
            String[] parts = scheduledTime.split(":");
            int scheduledHour = Integer.parseInt(parts[0]);
            int scheduledMinute = Integer.parseInt(parts[1]);
            int scheduledSecond = Integer.parseInt(parts[2]);

            // Sanity check
            if (scheduledMinute > 59 || scheduledSecond > 59) {
                System.err.println("⚠️ Invalid time components: " + scheduledTime);
                return 0;
            }

            ZoneId denverZone = ZoneId.of("America/Denver");

            // Get predicted time in Denver timezone
            ZonedDateTime predictedZoned = Instant.ofEpochSecond(predictedTime).atZone(denverZone);

            // ⭐ KEY FIX: Determine the service date
            // If the predicted time is after 3 AM, use today
            // If before 3 AM, the schedule is from yesterday's service
            LocalDate serviceDate = predictedZoned.toLocalDate();
            if (predictedZoned.getHour() < 3) {
                serviceDate = serviceDate.minusDays(1);
            }

            // Handle GTFS times >= 24:00:00
            int daysToAdd = scheduledHour / 24;
            scheduledHour = scheduledHour % 24;

            // Build scheduled time using service date
            ZonedDateTime scheduledZoned = ZonedDateTime.of(
                    serviceDate,
                    LocalTime.of(scheduledHour, scheduledMinute, scheduledSecond),
                    denverZone
            ).plusDays(daysToAdd);

            long scheduledEpoch = scheduledZoned.toEpochSecond();
            int delay = (int)(predictedTime - scheduledEpoch);

            // ⭐ Sanity check: if delay is huge, try adjusting by 24 hours
            if (delay > 43200) { // More than 12 hours late
                // Probably service date was yesterday
                scheduledZoned = scheduledZoned.plusDays(1);
                scheduledEpoch = scheduledZoned.toEpochSecond();
                delay = (int)(predictedTime - scheduledEpoch);
            } else if (delay < -43200) { // More than 12 hours early
                // Probably service date was tomorrow
                scheduledZoned = scheduledZoned.minusDays(1);
                scheduledEpoch = scheduledZoned.toEpochSecond();
                delay = (int)(predictedTime - scheduledEpoch);
            }

            // Final sanity check
            if (Math.abs(delay) > 7200) { // Still more than 2 hours off
                System.err.println("⚠️ Unreasonable delay after adjustment: " + delay + " seconds");
                System.err.println("   Trip has scheduled time: " + scheduledTime);
                System.err.println("   Predicted: " + predictedZoned);
                System.err.println("   Scheduled: " + scheduledZoned);
                return 0; // Skip this bad calculation
            }

            return delay;

        } catch (Exception e) {
            System.err.println("Error calculating delay: " + e.getMessage());
            return 0;
        }
    }


    @KafkaListener(topics = "vehicle-positions", groupId = "vehicle-position-consumer-group")
    public void processVehiclePosition(byte[] data) throws Exception {
        System.out.println("VEHICLE POSITION MESSAGE RECEIVED");
        List<VehiclePosition> vehiclePositionList;
        Thread.sleep(2000);
        try {
            FeedMessage vehicleStatusFeed = FeedMessage.parseFrom(data);
            vehiclePositionList = vehicleStatusFeed.getEntityList().stream()
                    .filter(FeedEntity::hasVehicle)
                    .map(FeedEntity::getVehicle)
                    .toList();

            for (VehiclePosition vehicle: vehiclePositionList) {
                if (!vehicle.hasVehicle() || !vehicle.hasTrip() || !vehicle.hasPosition()) {
                    System.out.println("Skipping vehicle with missing data");
                    continue;
                }
                VehicleStatus status = new VehicleStatus();
                status.setVehicleId(vehicle.getVehicle().getId());
                status.setTripId(vehicle.getTrip().getTripId());
                status.setRouteId(vehicle.getTrip().getRouteId());
                status.setLatitude(vehicle.getPosition().getLatitude());
                status.setLongitude(vehicle.getPosition().getLongitude());
                status.setStopId(vehicle.getStopId());
                status.setTimestamp(vehicle.getTimestamp());

                Integer delay = tripDelays.get(vehicle.getTrip().getTripId());
                status.setDelaySeconds(delay);

                vehicleRepo.save(status);
            }

            Long repoSize = vehicleRepo.count();
            System.out.println("------------------------------------------");
            System.out.println("vehicle_status TABLE SIZE: " + repoSize);
            System.out.println("------------------------------------------");

        } catch (Exception ex) {
            System.err.println("Error processing vehicle position: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

}

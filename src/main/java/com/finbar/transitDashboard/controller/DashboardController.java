package com.finbar.transitDashboard.controller;

import com.finbar.transitDashboard.model.VehicleStatus;
import com.finbar.transitDashboard.model.VehicleStatusRepository;
import com.finbar.transitDashboard.model.DelayHistory;
import com.finbar.transitDashboard.model.DelayHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private VehicleStatusRepository vehicleRepo;

    @Autowired
    private DelayHistoryRepository delayHistoryRepo;

    // Endpoint 1: Get all current vehicles with delays
    @GetMapping("/vehicles/current")
    public List<Map<String, Object>> getCurrentVehicles() {
        List<VehicleStatus> vehicles = vehicleRepo.findAll();

        return vehicles.stream().map(v -> {
            Map<String, Object> map = new HashMap<>();
            map.put("vehicle_id", v.getVehicleId());
            map.put("route_id", v.getRouteId());
            map.put("latitude", v.getLatitude());
            map.put("longitude", v.getLongitude());
            map.put("delay_seconds", v.getDelaySeconds());
            map.put("delay_minutes", v.getDelaySeconds() != null ? v.getDelaySeconds() / 60.0 : 0);
            map.put("delay_category", getDelayCategory(v.getDelaySeconds()));
            return map;
        }).collect(Collectors.toList());
    }

    // Endpoint 2: Get delay summary statistics
    @GetMapping("/delays/summary")
    public Map<String, Object> getDelaySummary() {
        List<VehicleStatus> all = vehicleRepo.findAll();

        long total = all.size();
        long onTime = all.stream().filter(v ->
                v.getDelaySeconds() != null && Math.abs(v.getDelaySeconds()) <= 180).count();
        long minorDelay = all.stream().filter(v ->
                v.getDelaySeconds() != null && v.getDelaySeconds() > 180 && v.getDelaySeconds() <= 600).count();
        long majorDelay = all.stream().filter(v ->
                v.getDelaySeconds() != null && v.getDelaySeconds() > 600).count();
        long early = all.stream().filter(v ->
                v.getDelaySeconds() != null && v.getDelaySeconds() < -180).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("total_vehicles", total);
        summary.put("on_time", onTime);
        summary.put("minor_delay", minorDelay);
        summary.put("major_delay", majorDelay);
        summary.put("early", early);
        summary.put("on_time_percentage", total > 0 ? Math.round(onTime * 100.0 / total) : 0);

        return summary;
    }

    // Endpoint 3: Get delayed vehicles only (for operations focus)
    @GetMapping("/vehicles/delayed")
    public List<Map<String, Object>> getDelayedVehicles(
            @RequestParam(defaultValue = "180") int minDelaySeconds
    ) {
        List<VehicleStatus> all = vehicleRepo.findAll();

        return all.stream()
            .filter(v -> v.getDelaySeconds() != null && v.getDelaySeconds() > minDelaySeconds)
            .map(v -> {
                Map<String, Object> map = new HashMap<>();
                map.put("vehicle_id", v.getVehicleId());
                map.put("route_id", v.getRouteId());
                map.put("latitude", v.getLatitude());
                map.put("longitude", v.getLongitude());
                // Math.round returns a long
                map.put("delay_minutes", Math.round(v.getDelaySeconds() / 60.0));
                return map;
            })
            // Change the casts here to Long
            .sorted((a, b) -> ((Long)b.get("delay_minutes")).compareTo((Long)a.get("delay_minutes")))
            .collect(Collectors.toList());
    }

    // Endpoint 4: Get delay history for a specific route
    @GetMapping("/delays/route/{routeId}")
    public List<Map<String, Object>> getRouteDelayHistory(
            @PathVariable String routeId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<DelayHistory> history = delayHistoryRepo
                .findTop100ByRouteIdOrderByRecordedAtDesc(routeId);

        return history.stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", h.getRecordedAt());
            map.put("delay_seconds", h.getDelaySeconds());
            map.put("delay_minutes", h.getDelaySeconds() / 60.0);
            return map;
        }).collect(Collectors.toList());
    }

    // Endpoint 5: Get predictions (simple heuristic for demo)
    @GetMapping("/predictions")
    public List<Map<String, Object>> getPredictions() {
        List<VehicleStatus> all = vehicleRepo.findAll();
        List<Map<String, Object>> predictions = new ArrayList<>();

        for (VehicleStatus v : all) {
            // Simple prediction: if currently delayed 3+ min, predict will be 5+ min delayed soon
            if (v.getDelaySeconds() != null && v.getDelaySeconds() >= 180) {
                Map<String, Object> prediction = new HashMap<>();
                prediction.put("route_id", v.getRouteId());
                prediction.put("vehicle_id", v.getVehicleId());
                prediction.put("current_delay_min", Math.round(v.getDelaySeconds() / 60.0));
                prediction.put("predicted_delay_min", Math.round(v.getDelaySeconds() / 60.0) + 2);
                prediction.put("confidence", "70%");
                prediction.put("action", "Consider deploying backup bus");
                predictions.add(prediction);
            }
        }

        return predictions;
    }

    // Endpoint 6: Get top problem routes
    @GetMapping("/delays/top-routes")
    public List<Map<String, Object>> getTopProblemRoutes() {
        List<DelayHistory> recent = delayHistoryRepo
                .findTop1000ByOrderByRecordedAtDesc();

        // Group by route and calculate average delay
        Map<String, List<Integer>> routeDelays = new HashMap<>();
        for (DelayHistory h : recent) {
            routeDelays.computeIfAbsent(h.getRouteId(), k -> new ArrayList<>())
                    .add(h.getDelaySeconds());
        }

        List<Map<String, Object>> topRoutes = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : routeDelays.entrySet()) {
            double avgDelay = entry.getValue().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0);

            if (avgDelay > 180) { // Only routes with avg delay > 3 min
                Map<String, Object> route = new HashMap<>();
                route.put("route_id", entry.getKey());
                route.put("avg_delay_minutes", Math.round(avgDelay / 60.0));
                route.put("incident_count", entry.getValue().size());
                topRoutes.add(route);
            }
        }

        // Sort by average delay
        topRoutes.sort((a, b) ->
                ((Long)b.get("avg_delay_minutes")).compareTo((Long)a.get("avg_delay_minutes"))
        );

        return topRoutes.stream().limit(10).collect(Collectors.toList());
    }

    @GetMapping("/delays/patterns")
    public List<Map<String, Object>> getDelayPatterns() {
        List<DelayHistory> last7Days = delayHistoryRepo
                .findByRecordedAtAfter(LocalDateTime.now().minusDays(7));

        // Group by route + hour of day
        Map<String, Map<Integer, List<Integer>>> patterns = new HashMap<>();

        for (DelayHistory h : last7Days) {
            String route = h.getRouteId();
            int hour = h.getRecordedAt().getHour();

            patterns.computeIfAbsent(route, k -> new HashMap<>())
                    .computeIfAbsent(hour, k -> new ArrayList<>())
                    .add(h.getDelaySeconds());
        }

        // Calculate averages
        List<Map<String, Object>> result = new ArrayList<>();
        for (String route : patterns.keySet()) {
            for (Integer hour : patterns.get(route).keySet()) {
                List<Integer> delays = patterns.get(route).get(hour);
                double avgDelay = delays.stream().mapToInt(Integer::intValue).average().orElse(0);

                if (avgDelay > 180) { // Only show if avg > 3 min
                    Map<String, Object> pattern = new HashMap<>();
                    pattern.put("route_id", route);
                    pattern.put("hour", hour);
                    pattern.put("avg_delay_minutes", Math.round(avgDelay / 60.0));
                    pattern.put("frequency", delays.size());
                    result.add(pattern);
                }
            }
        }

        // Sort by average delay
        result.sort((a, b) ->
                ((Long)b.get("avg_delay_minutes")).compareTo((Long)a.get("avg_delay_minutes"))
        );

        return result;
    }

    @PostMapping("/admin/cleanup-corrupted-data")
    public Map<String, Object> cleanupCorruptedData() {
        // Delete vehicles with unrealistic delays (outside -2 hours to +2 hours)
        List<VehicleStatus> allVehicles = vehicleRepo.findAll();
        List<VehicleStatus> corruptedVehicles = allVehicles.stream()
            .filter(v -> v.getDelaySeconds() != null &&
                        (v.getDelaySeconds() < -7200 || v.getDelaySeconds() > 7200))
            .toList();
        vehicleRepo.deleteAll(corruptedVehicles);

        // Delete delay history with unrealistic delays
        List<DelayHistory> allHistory = delayHistoryRepo.findAll();
        List<DelayHistory> corruptedHistory = allHistory.stream()
            .filter(h -> h.getDelaySeconds() < -7200 || h.getDelaySeconds() > 7200)
            .toList();
        delayHistoryRepo.deleteAll(corruptedHistory);

        Map<String, Object> result = new HashMap<>();
        result.put("deleted_vehicles", corruptedVehicles.size());
        result.put("deleted_history_records", corruptedHistory.size());
        result.put("message", "Corrupted data cleaned successfully");

        return result;
    }

    @GetMapping("/alerts/active")
    public List<Map<String, Object>> getActiveAlerts() {
        List<VehicleStatus> all = vehicleRepo.findAll();
        List<Map<String, Object>> alerts = new ArrayList<>();

        for (VehicleStatus v : all) {
            // Filter: Only delays between 10 min and 2 hours (7200 sec) are realistic
            if (v.getDelaySeconds() != null &&
                v.getDelaySeconds() > 600 &&
                v.getDelaySeconds() < 7200) {

                long delayMinutes = Math.round(v.getDelaySeconds() / 60.0);
                Map<String, Object> alert = new HashMap<>();
                alert.put("route_id", v.getRouteId());
                alert.put("delay_minutes", delayMinutes);
                alert.put("severity", v.getDelaySeconds() > 900 ? "critical" : "warning");
                alert.put("message", "Route " + v.getRouteId() + " is " + delayMinutes + " minutes delayed");
                alert.put("action", "Consider deploying backup bus");
                alerts.add(alert);

                // Simulate sending alert (in production, this would be email/SMS)
                System.out.println("⚠️ ALERT: " + alert.get("message"));
            }
        }

        return alerts;
    }

    private String getDelayCategory(Integer delaySeconds) {
        if (delaySeconds == null) return "unknown";
        if (delaySeconds < -180) return "early";
        if (delaySeconds <= 180) return "on_time";
        if (delaySeconds <= 600) return "minor_delay";
        return "major_delay";
    }
}


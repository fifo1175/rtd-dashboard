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

        // Only return vehicles that were updated in the last 5 minutes (actively running)
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

        return vehicles.stream()
                .filter(v -> v.getLastUpdated() != null && v.getLastUpdated().isAfter(fiveMinutesAgo))
                .map(v -> {
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

        // Only count vehicles that were updated in the last 5 minutes (actively running)
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<VehicleStatus> activeVehicles = all.stream()
                .filter(v -> v.getLastUpdated() != null && v.getLastUpdated().isAfter(fiveMinutesAgo))
                .filter(v -> v.getDelaySeconds() != null) // Exclude UNKNOWN status
                .toList();

        long total = activeVehicles.size();
        long onTime = activeVehicles.stream().filter(v ->
                Math.abs(v.getDelaySeconds()) <= 180).count();
        long minorDelay = activeVehicles.stream().filter(v ->
                v.getDelaySeconds() > 180 && v.getDelaySeconds() <= 600).count();
        long majorDelay = activeVehicles.stream().filter(v ->
                v.getDelaySeconds() > 600).count();
        long early = activeVehicles.stream().filter(v ->
                v.getDelaySeconds() < -180).count();

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

    // Endpoint 5: Get predictions (ML-inspired algorithm using historical patterns)
    @GetMapping("/predictions")
    public List<Map<String, Object>> getPredictions() {
        List<VehicleStatus> all = vehicleRepo.findAll();
        List<Map<String, Object>> predictions = new ArrayList<>();

        // Get recent history for trend analysis (last 30 minutes)
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        List<DelayHistory> recentHistory = delayHistoryRepo.findByRecordedAtAfter(thirtyMinutesAgo);

        // PERFORMANCE OPTIMIZATION: Limit to 50K most recent records (~3-4 hours of data)
        // This provides sufficient historical context while keeping memory usage low
        // and query times fast even with millions of database records
        List<DelayHistory> weekHistory = delayHistoryRepo.findTop50000ByOrderByRecordedAtDesc();

        // Group history by trip ID for trend analysis
        Map<String, List<DelayHistory>> tripHistory = recentHistory.stream()
            .filter(h -> h.getTripId() != null)
            .collect(Collectors.groupingBy(DelayHistory::getTripId));

        // Pre-group week history by route for fast lookup
        Map<String, List<DelayHistory>> routeWeekHistory = weekHistory.stream()
            .filter(h -> h.getRouteId() != null)
            .collect(Collectors.groupingBy(DelayHistory::getRouteId));

        for (VehicleStatus v : all) {
            // Only predict for vehicles with delays >= 3 min
            if (v.getDelaySeconds() != null && v.getDelaySeconds() >= 180) {

                // Analyze historical trend for this trip (or empty list if no history)
                List<DelayHistory> history = tripHistory.getOrDefault(v.getTripId(), new ArrayList<>());

                // Calculate delay trend (getting worse, stable, or improving)
                double trendFactor = calculateDelayTrend(history, v.getDelaySeconds());

                // Get historical pattern for this route at this time of day (pass pre-fetched data)
                List<DelayHistory> routeHistory = routeWeekHistory.getOrDefault(v.getRouteId(), new ArrayList<>());
                double routePatternFactor = getRoutePatternFactor(routeHistory);

                // Predict future delay (20 minutes ahead)
                long currentDelayMin = Math.round(v.getDelaySeconds() / 60.0);
                long predictedDelayMin = Math.round(currentDelayMin + trendFactor + routePatternFactor);

                // Ensure prediction is realistic (not negative, not absurdly high)
                predictedDelayMin = Math.max(0, Math.min(predictedDelayMin, 120));

                // Calculate confidence based on historical data quality (pass pre-fetched data)
                int confidence = calculateConfidence(history, routeHistory);

                // Generate action recommendation based on severity
                String action = generateActionRecommendation(currentDelayMin, predictedDelayMin, trendFactor);

                Map<String, Object> prediction = new HashMap<>();
                prediction.put("route_id", v.getRouteId());
                prediction.put("vehicle_id", v.getVehicleId());
                prediction.put("current_delay_min", currentDelayMin);
                prediction.put("predicted_delay_min", predictedDelayMin);
                prediction.put("confidence", confidence + "%");
                prediction.put("action", action);
                predictions.add(prediction);
            }
        }

        // Sort by predicted severity (worst first)
        predictions.sort((a, b) ->
            ((Long)b.get("predicted_delay_min")).compareTo((Long)a.get("predicted_delay_min"))
        );

        return predictions;
    }

    /**
     * Calculate delay trend based on recent history
     * Returns expected change in delay over next 20 minutes
     */
    private double calculateDelayTrend(List<DelayHistory> history, int currentDelay) {
        if (history.size() < 2) {
            // Not enough data - assume slight worsening
            return 1.5;
        }

        // Sort by time (oldest first)
        List<DelayHistory> sorted = history.stream()
            .sorted(Comparator.comparing(DelayHistory::getRecordedAt))
            .collect(Collectors.toList());

        // Calculate rate of change (delay difference over time)
        DelayHistory oldest = sorted.get(0);
        DelayHistory newest = sorted.get(sorted.size() - 1);

        long timeDiffMinutes = java.time.Duration.between(
            oldest.getRecordedAt(),
            newest.getRecordedAt()
        ).toMinutes();

        if (timeDiffMinutes == 0) {
            return 1.0;
        }

        double delayChange = (newest.getDelaySeconds() - oldest.getDelaySeconds()) / 60.0;
        double ratePerMinute = delayChange / timeDiffMinutes;

        // Project 20 minutes into the future
        return ratePerMinute * 20;
    }

    /**
     * Get route-specific pattern factor based on historical delays at this time
     * PERFORMANCE: Now takes pre-fetched history to avoid repeated DB queries
     */
    private double getRoutePatternFactor(List<DelayHistory> routeHistory) {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        // Filter for similar hour (±1 hour window) from pre-fetched data
        List<Integer> similarTimeDelays = routeHistory.stream()
            .filter(h -> Math.abs(h.getRecordedAt().getHour() - currentHour) <= 1)
            .map(DelayHistory::getDelaySeconds)
            .collect(Collectors.toList());

        if (similarTimeDelays.isEmpty()) {
            return 0.5; // Slight default worsening
        }

        // If this route typically gets worse at this time, factor that in
        double avgHistoricalDelay = similarTimeDelays.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0) / 60.0;

        // Return a small adjustment based on typical pattern
        return Math.min(3.0, Math.max(-2.0, avgHistoricalDelay / 10.0));
    }

    /**
     * Calculate confidence score based on data quality and consistency
     * PERFORMANCE: Now takes pre-fetched history to avoid repeated DB queries
     * Returns percentage (0-100)
     */
    private int calculateConfidence(List<DelayHistory> tripHistory, List<DelayHistory> routeHistory) {
        int baseConfidence = 50; // Start at 50%

        // Factor 1: Amount of historical data available
        if (tripHistory.size() >= 5) {
            baseConfidence += 20; // Good amount of recent data
        } else if (tripHistory.size() >= 2) {
            baseConfidence += 10; // Some recent data
        }

        // Factor 2: Consistency of delay pattern (low variance = high confidence)
        if (tripHistory.size() >= 3) {
            List<Integer> delays = tripHistory.stream()
                .map(DelayHistory::getDelaySeconds)
                .collect(Collectors.toList());

            double avg = delays.stream().mapToInt(Integer::intValue).average().orElse(0);
            double variance = delays.stream()
                .mapToDouble(d -> Math.pow(d - avg, 2))
                .average()
                .orElse(0);
            double stdDev = Math.sqrt(variance);

            // Low standard deviation = more consistent = higher confidence
            if (stdDev < 60) { // Less than 1 min variance
                baseConfidence += 15;
            } else if (stdDev < 180) { // Less than 3 min variance
                baseConfidence += 5;
            }
        }

        // Factor 3: Route-specific historical data availability (use pre-fetched data)
        long routeHistoryCount = routeHistory.size();

        if (routeHistoryCount > 100) {
            baseConfidence += 10; // Rich historical data
        } else if (routeHistoryCount > 20) {
            baseConfidence += 5; // Decent historical data
        }

        // Cap at 95% (never 100% certain)
        return Math.min(95, baseConfidence);
    }

    /**
     * Generate contextual action recommendations based on delay severity and trend
     */
    private String generateActionRecommendation(long currentDelay, long predictedDelay, double trend) {
        long delayIncrease = predictedDelay - currentDelay;

        // Critical: Major delay getting worse
        if (predictedDelay >= 15 && trend > 3) {
            return "URGENT: Deploy backup bus immediately and notify passengers";
        }

        // Severe: Large delay or rapidly worsening
        if (predictedDelay >= 12 || delayIncrease >= 5) {
            return "Deploy backup bus and update passenger notifications";
        }

        // Moderate worsening: Prepare response
        if (trend > 2) {
            return "Monitor closely - prepare backup bus for potential deployment";
        }

        // Improving trend: Just monitor
        if (trend < 0) {
            return "Delay improving - continue monitoring";
        }

        // Stable but delayed: Standard response
        if (predictedDelay >= 8) {
            return "Consider deploying backup bus if delay persists";
        }

        // Minor delay: Monitor only
        return "Monitor situation - no immediate action required";
    }

    // Endpoint 6: Get top problem routes (real-time route-level aggregation)
    // Shows routes with multiple delayed vehicles RIGHT NOW (not historical)
    @GetMapping("/delays/top-routes")
    public List<Map<String, Object>> getTopProblemRoutes() {
        List<VehicleStatus> all = vehicleRepo.findAll();

        // Only consider vehicles that were updated in the last 5 minutes (actively running)
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<VehicleStatus> activeVehicles = all.stream()
                .filter(v -> v.getLastUpdated() != null && v.getLastUpdated().isAfter(fiveMinutesAgo))
                .filter(v -> v.getDelaySeconds() != null && v.getDelaySeconds() > 600) // Delayed vehicles (>10 min)
                .toList();

        // Group by route and calculate statistics
        Map<String, List<VehicleStatus>> routeVehicles = new HashMap<>();
        for (VehicleStatus v : activeVehicles) {
            routeVehicles.computeIfAbsent(v.getRouteId(), k -> new ArrayList<>()).add(v);
        }

        List<Map<String, Object>> topRoutes = new ArrayList<>();
        for (Map.Entry<String, List<VehicleStatus>> entry : routeVehicles.entrySet()) {
            List<VehicleStatus> vehicles = entry.getValue();

            // Only show routes with 2+ delayed vehicles (indicates systemic issue)
            if (vehicles.size() >= 2) {
                double avgDelay = vehicles.stream()
                        .mapToInt(VehicleStatus::getDelaySeconds)
                        .average()
                        .orElse(0);

                Map<String, Object> route = new HashMap<>();
                route.put("route_id", entry.getKey());
                route.put("avg_delay_minutes", Math.round(avgDelay / 60.0));
                route.put("incident_count", vehicles.size()); // Number of delayed vehicles on this route
                topRoutes.add(route);
            }
        }

        // Sort by number of affected vehicles (descending), then by average delay
        topRoutes.sort((a, b) -> {
            int countCompare = ((Integer)b.get("incident_count")).compareTo((Integer)a.get("incident_count"));
            if (countCompare != 0) return countCompare;
            return ((Long)b.get("avg_delay_minutes")).compareTo((Long)a.get("avg_delay_minutes"));
        });

        return topRoutes.stream().limit(10).collect(Collectors.toList());
    }

    @GetMapping("/delays/patterns")
    public List<Map<String, Object>> getDelayPatterns() {
        // PERFORMANCE OPTIMIZATION: Use 50K record limit instead of 7-day time query
        List<DelayHistory> last7Days = delayHistoryRepo.findTop50000ByOrderByRecordedAtDesc();

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

    @GetMapping("/delays/hotspots")
    public List<Map<String, Object>> getDelayHotspots() {
        // Denver metro area bounds to filter out bad data
        final double MIN_LAT = 39.5;
        final double MAX_LAT = 40.2;
        final double MIN_LON = -105.3;
        final double MAX_LON = -104.5;

        // Get all delay history records to ensure full hourly coverage
        // In production with millions of records, this would be replaced with a time-based query
        List<DelayHistory> recentHistory = delayHistoryRepo.findAll();

        // Get current vehicle positions to map routes to locations
        List<VehicleStatus> currentVehicles = vehicleRepo.findAll();

        // Create a map of route -> average location (only for vehicles within Denver metro)
        Map<String, double[]> routeLocations = new HashMap<>();
        Map<String, Integer> routeLocationCounts = new HashMap<>();

        for (VehicleStatus v : currentVehicles) {
            if (v.getRouteId() != null && v.getLatitude() != null && v.getLongitude() != null) {
                // Filter out vehicles outside Denver metro area
                if (v.getLatitude() >= MIN_LAT && v.getLatitude() <= MAX_LAT &&
                    v.getLongitude() >= MIN_LON && v.getLongitude() <= MAX_LON) {
                    String route = v.getRouteId();
                    double[] loc = routeLocations.getOrDefault(route, new double[]{0.0, 0.0});
                    loc[0] += v.getLatitude();
                    loc[1] += v.getLongitude();
                    routeLocations.put(route, loc);
                    routeLocationCounts.put(route, routeLocationCounts.getOrDefault(route, 0) + 1);
                }
            }
        }

        // Calculate average locations for each route
        for (String route : routeLocations.keySet()) {
            double[] loc = routeLocations.get(route);
            int count = routeLocationCounts.get(route);
            loc[0] /= count;
            loc[1] /= count;
        }

        // Group delays by route and hour
        Map<String, Map<Integer, List<Integer>>> routeHourDelays = new HashMap<>();

        for (DelayHistory h : recentHistory) {
            String route = h.getRouteId();
            if (route != null && h.getDelaySeconds() != null && h.getDelaySeconds() > 180) {
                int hour = h.getRecordedAt().getHour();
                routeHourDelays.computeIfAbsent(route, k -> new HashMap<>())
                               .computeIfAbsent(hour, k -> new ArrayList<>())
                               .add(h.getDelaySeconds());
            }
        }

        // Build result with location data
        List<Map<String, Object>> result = new ArrayList<>();

        for (String route : routeHourDelays.keySet()) {
            // Skip routes without location data
            if (!routeLocations.containsKey(route)) continue;

            double[] loc = routeLocations.get(route);

            // Filter out routes with locations outside Denver metro bounds
            if (loc[0] < MIN_LAT || loc[0] > MAX_LAT || loc[1] < MIN_LON || loc[1] > MAX_LON) {
                continue;
            }

            Map<Integer, List<Integer>> hourDelays = routeHourDelays.get(route);

            // Calculate overall average and hourly breakdown
            List<Integer> allDelays = new ArrayList<>();
            Map<String, Double> hourlyBreakdown = new HashMap<>();

            for (Integer hour : hourDelays.keySet()) {
                List<Integer> delays = hourDelays.get(hour);
                allDelays.addAll(delays);
                double avgHourDelay = delays.stream().mapToInt(Integer::intValue).average().orElse(0);
                hourlyBreakdown.put(String.valueOf(hour), Math.round(avgHourDelay / 60.0 * 10) / 10.0);
            }

            double avgDelay = allDelays.stream().mapToInt(Integer::intValue).average().orElse(0);

            if (avgDelay > 180) { // Only include if avg > 3 min
                Map<String, Object> hotspot = new HashMap<>();
                hotspot.put("route_id", route);
                hotspot.put("location_lat", loc[0]);
                hotspot.put("location_lon", loc[1]);
                hotspot.put("avg_delay", Math.round(avgDelay / 60.0 * 10) / 10.0);
                hotspot.put("occurrence_count", allDelays.size());
                hotspot.put("hourly_breakdown", hourlyBreakdown);
                result.add(hotspot);
            }
        }

        // Sort by average delay
        result.sort((a, b) ->
            Double.compare((Double)b.get("avg_delay"), (Double)a.get("avg_delay"))
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

        // Only consider vehicles that were updated in the last 5 minutes (actively running)
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<VehicleStatus> activeVehicles = all.stream()
                .filter(v -> v.getLastUpdated() != null && v.getLastUpdated().isAfter(fiveMinutesAgo))
                .filter(v -> v.getDelaySeconds() != null && v.getDelaySeconds() > 600) // Major delay (10+ min)
                .toList();

        List<Map<String, Object>> alerts = new ArrayList<>();

        for (VehicleStatus v : activeVehicles) {
            long delayMinutes = Math.round(v.getDelaySeconds() / 60.0);
            Map<String, Object> alert = new HashMap<>();
            alert.put("route_id", v.getRouteId());
            alert.put("vehicle_id", v.getVehicleId());
            alert.put("delay_minutes", delayMinutes);
            alert.put("severity", v.getDelaySeconds() > 900 ? "critical" : "warning");
            alert.put("message", "Route " + v.getRouteId() + " is " + delayMinutes + " minutes delayed");
            alert.put("action", "Consider deploying backup bus");
            alerts.add(alert);
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


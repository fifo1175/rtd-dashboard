# Problem Routes Calculation - Detailed Explanation

## Algorithm Overview

The Problem Routes panel uses the **most recent 1,000 delay records** from the database, regardless of time period.

### Step-by-Step Process

```java
// 1. Fetch the 1000 most recent delay records
List<DelayHistory> recent = delayHistoryRepo
    .findTop1000ByOrderByRecordedAtDesc();

// 2. Group these 1000 records by route_id
Map<String, List<Integer>> routeDelays = new HashMap<>();
for (DelayHistory h : recent) {
    routeDelays.computeIfAbsent(h.getRouteId(), k -> new ArrayList<>())
        .add(h.getDelaySeconds());
}

// 3. Calculate average delay for each route
for (Map.Entry<String, List<Integer>> entry : routeDelays.entrySet()) {
    double avgDelay = entry.getValue().stream()
        .mapToInt(Integer::intValue)
        .average()
        .orElse(0);
    
    // 4. Filter: Only routes with avg delay > 3 minutes
    if (avgDelay > 180) {
        // Include this route as a "problem route"
    }
}

// 5. Sort by average delay (worst first)
// 6. Return top 10 routes
```

---

## Time Frame Analysis

### Current Reality (Based on Live Data)

**Time Span:** The last 1000 delay records cover approximately **1-2 minutes** of real-time data!

**Why so short?**
- RTD has ~1,600 active vehicles reporting delays
- Delays are recorded every 30 seconds from GTFS feed
- This creates approximately **800-1000 new delay records per minute**
- Therefore, 1000 records = about 60-90 seconds of recent history

**Example from current database:**
```
Oldest of last 1000: 2026-01-06 14:56:27
Newest record:       2026-01-06 14:57:29
Time span:           1.03 minutes (62 seconds)
Unique routes:       104 different routes
```

---

## What This Means

### The "Average Delay" is NOT Historical

The displayed metrics like **"Route 15: 16 min avg"** can be misleading:

**It does NOT mean:**
- ❌ Average delay over the past week
- ❌ Average delay over the past day
- ❌ Average delay over the past hour

**It ACTUALLY means:**
- ✅ Average delay across the last ~1000 data points
- ✅ Which represents roughly **the last 60-90 seconds** of data
- ✅ Essentially a **near-real-time snapshot** of current delays

### "Incident Count" Explained

When you see **"142 delays recorded"** for a route, it means:
- That route had **142 delay records** in the last 1000 system-wide records
- Since 1000 records ≈ 1-2 minutes, this route had 142 delays recorded in the last ~90 seconds
- This indicates a **high-frequency route** with many active vehicles

---

## Distribution Across Routes

From a typical snapshot of the last 1000 records:

| Route | Count in Last 1000 | What This Means |
|-------|-------------------|-----------------|
| 101H  | 36 records        | Very active route, many vehicles reporting |
| 101E  | 36 records        | Very active route, many vehicles reporting |
| 16    | 33 records        | Active route with frequent updates |
| 15L   | 33 records        | Active route with frequent updates |
| 15    | 30 records        | Active route |

**Interpretation:**
- Routes with high record counts in the last 1000 are running more vehicles
- Each vehicle reports delays every ~30 seconds
- A route with 30 records likely has 1-2 vehicles reporting over the 90-second window

---

## Why This Design Choice?

### Advantages:
1. **Real-time responsiveness** - Shows current problems, not historical averages
2. **Always fresh data** - No stale information from hours ago
3. **Automatic recency** - No need to filter by timestamps
4. **Performance** - Simple limit query is very fast

### Disadvantages:
1. **Misleading terminology** - "Average" implies longer time period
2. **High volatility** - Can change dramatically minute-to-minute
3. **Not truly "average"** - More of a current snapshot
4. **Inconsistent time windows** - Busy vs. quiet times produce different spans

---

## What the User Interface Says vs. Reality

### Dashboard Display:
```
Route 15L: 16 min avg
142 delays recorded
```

### What Users Might Think:
- "Route 15L has averaged 16 minutes of delays over some time period"
- "There have been 142 separate delay incidents"

### What It Actually Means:
- "In the last ~90 seconds of data, Route 15L's delays averaged 16 minutes"
- "Route 15L had 142 data points in the most recent 1000 system-wide records"
- "Route 15L has multiple vehicles currently experiencing ~16 min delays"

---

## Alternative Time-Based Calculation

If you wanted a more traditional "average delay over last X hours", the query would be:

```java
// Get delays from last 4 hours
LocalDateTime fourHoursAgo = LocalDateTime.now().minusHours(4);
List<DelayHistory> recent = delayHistoryRepo
    .findByRecordedAtAfter(fourHoursAgo);

// Then group by route and calculate averages
// This would show: "Average delay over the last 4 hours"
```

**Tradeoff:**
- ✅ More accurate "average" over a defined time period
- ✅ Less volatile, more stable metric
- ❌ Slower query (must scan time range, not just limit 1000)
- ❌ Might include stale data from hours ago

---

## Recommendation: Clarify the UI

To avoid user confusion, the Problem Routes panel could display:

**Current Version:**
```
Route 15L: 16 min avg
142 delays recorded
```

**Clearer Version:**
```
Route 15L: 16 min delay (current)
142 recent data points
Last updated: 1 min ago
```

Or add a subtitle:
```
Problem Routes (Real-time Snapshot)
Based on last 1000 delay records (~90 seconds)
```

This would set accurate expectations that it's showing **current delays**, not historical averages.

---

## Summary

**Question:** How are problem routes calculated?  
**Answer:** By taking the 1000 most recent delay records, grouping by route, calculating average delay per route, and showing the top 10 routes with avg delay > 3 minutes.

**Question:** Over what time frame is the average delay?  
**Answer:** The last **~60-90 seconds** (or ~1-2 minutes), NOT a longer historical period. It's essentially a real-time snapshot of which routes are currently experiencing delays.

**Data Freshness:** Updates every 30 seconds as new delay data flows in from Kafka.

**Practical Meaning:** This panel shows "which routes are delayed RIGHT NOW" rather than "which routes have been problematic over the past day/week."

# RTD Proactive Operations Dashboard - Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RTD GTFS REALTIME FEEDS                             │
│  ┌──────────────────────────────┐  ┌──────────────────────────────────┐    │
│  │   Vehicle Positions Feed     │  │    Trip Updates Feed             │    │
│  │  (Real-time bus locations)   │  │  (Delay information)             │    │
│  │  - latitude/longitude        │  │  - delay_seconds                 │    │
│  │  - bearing, speed            │  │  - trip_id, route_id             │    │
│  │  Updated every 30 seconds    │  │  - stop predictions              │    │
│  └──────────────┬───────────────┘  └──────────────┬───────────────────┘    │
└─────────────────┼──────────────────────────────────┼──────────────────────┘
                  │                                   │
                  │         Apache Kafka Cluster      │
                  ├───────────────────────────────────┤
                  │  Topic: rtd-vehicle-positions     │
                  │  Topic: rtd-trip-updates          │
                  └───────────────┬───────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT APPLICATION                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         KAFKA CONSUMERS                                │  │
│  │  ┌────────────────────────────┐  ┌─────────────────────────────────┐ │  │
│  │  │  VehiclePositionConsumer   │  │   TripUpdateConsumer            │ │  │
│  │  │  - Processes locations     │  │   - Processes delay data        │ │  │
│  │  │  - Extracts coordinates    │  │   - Calculates delays           │ │  │
│  │  │  - Maps to routes          │  │   - Records timestamps          │ │  │
│  │  └────────────┬───────────────┘  └─────────────┬───────────────────┘ │  │
│  └───────────────┼──────────────────────────────────┼─────────────────────┘  │
│                  │                                   │                        │
│                  ▼                                   ▼                        │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          DATA PERSISTENCE LAYER                        │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │                      SQLite Database                             │  │  │
│  │  │  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐│  │  │
│  │  │  │  vehicle_status  │  │  delay_history   │  │ gtfs_schedule  ││  │  │
│  │  │  │  ───────────────│  │  ───────────────│  │  ───────────── ││  │  │
│  │  │  │  • vehicle_id   │  │  • id           │  │  • routes.txt  ││  │  │
│  │  │  │  • route_id     │  │  • route_id     │  │  • trips.txt   ││  │  │
│  │  │  │  • trip_id      │  │  • trip_id      │  │  • stops.txt   ││  │  │
│  │  │  │  • latitude     │  │  • stop_id      │  │  • stop_times  ││  │  │
│  │  │  │  • longitude    │  │  • delay_sec    │  │  • calendar    ││  │  │
│  │  │  │  • delay_sec    │  │  • timestamp    │  │                ││  │  │
│  │  │  │  • bearing      │  │  • recorded_at  │  │                ││  │  │
│  │  │  │  • timestamp    │  │                 │  │                ││  │  │
│  │  │  │  • last_updated │  │  (Time-series)  │  │  (Static ref)  ││  │  │
│  │  │  │  (Current state)│  │                 │  │                ││  │  │
│  │  │  └──────────────────┘  └──────────────────┘  └────────────────┘│  │  │
│  │  │                                                                   │  │  │
│  │  │  File: rtd_dashboard.db (with WAL mode for concurrency)          │  │  │
│  │  │  Connection Pool: HikariCP (max 10 connections)                  │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │                                   │                                     │  │
│  │                                   │ JPA/Hibernate ORM                   │  │
│  │                                   │                                     │  │
│  └───────────────────────────────────┼─────────────────────────────────────┘  │
│                                      │                                        │
│  ┌───────────────────────────────────┼─────────────────────────────────────┐  │
│  │                          REPOSITORY LAYER                               │  │
│  │  ┌──────────────────────┐  ┌─────────────────────┐  ┌────────────────┐│  │
│  │  │ VehicleStatusRepo    │  │ DelayHistoryRepo    │  │ GTFSSchedule   ││  │
│  │  │ - findAll()          │  │ - findByRecorded    │  │ - loadRoutes() ││  │
│  │  │ - findByRouteId()    │  │   AtAfter()         │  │ - loadStops()  ││  │
│  │  └──────────────────────┘  │ - findTop1000By...  │  └────────────────┘│  │
│  │                             │ - countByRouteId()  │                     │  │
│  │                             └─────────────────────┘                     │  │
│  └─────────────────────────────────┬───────────────────────────────────────┘  │
│                                    │                                          │
│  ┌─────────────────────────────────┼───────────────────────────────────────┐  │
│  │                       BUSINESS LOGIC / CONTROLLERS                       │  │
│  │                                  │                                        │  │
│  │  ┌───────────────────────────────────────────────────────────────────┐  │  │
│  │  │              DashboardController (REST API)                        │  │  │
│  │  │  ┌─────────────────────────────────────────────────────────────┐  │  │  │
│  │  │  │  GET /api/vehicles/current                                  │  │  │  │
│  │  │  │  - Returns all active vehicles with current locations       │  │  │  │
│  │  │  │                                                              │  │  │  │
│  │  │  │  GET /api/delays/summary                                    │  │  │  │
│  │  │  │  - Aggregates: on_time, minor_delay, major_delay counts    │  │  │  │
│  │  │  │                                                              │  │  │  │
│  │  │  │  GET /api/delays/top-routes                                 │  │  │  │
│  │  │  │  - Analyzes last 1000 incidents, groups by route           │  │  │  │
│  │  │  │  - Returns routes with avg delay > 3 min                   │  │  │  │
│  │  │  │                                                              │  │  │  │
│  │  │  │  GET /api/predictions  ⭐ ADVANCED ALGORITHM                │  │  │  │
│  │  │  │  ┌──────────────────────────────────────────────────────┐  │  │  │  │
│  │  │  │  │ 1. Fetch current vehicles (delay >= 3 min)          │  │  │  │  │
│  │  │  │  │ 2. Get 30-min recent history (trend analysis)       │  │  │  │  │
│  │  │  │  │ 3. Get 7-day historical patterns (route behavior)   │  │  │  │  │
│  │  │  │  │ 4. For each vehicle:                                │  │  │  │  │
│  │  │  │  │    a. Calculate delay trend (rate of change)        │  │  │  │  │
│  │  │  │  │    b. Get route pattern factor (time-of-day)        │  │  │  │  │
│  │  │  │  │    c. Predict delay in 20 min                       │  │  │  │  │
│  │  │  │  │    d. Calculate confidence (50-95%)                 │  │  │  │  │
│  │  │  │  │    e. Generate action recommendation                │  │  │  │  │
│  │  │  │  │ 5. Sort by severity, return top predictions         │  │  │  │  │
│  │  │  │  └──────────────────────────────────────────────────────┘  │  │  │  │
│  │  │  │                                                              │  │  │  │
│  │  │  │  GET /api/delays/patterns                                   │  │  │  │
│  │  │  │  - Groups 7-day history by route + hour                     │  │  │  │
│  │  │  │  - Identifies delay hotspots by time of day                 │  │  │  │
│  │  │  │                                                              │  │  │  │
│  │  │  │  GET /api/alerts/active                                     │  │  │  │
│  │  │  │  - Filters vehicles with delay > 10 min, < 2 hours         │  │  │  │
│  │  │  └──────────────────────────────────────────────────────────────┘  │  │  │
│  │  │                                                                     │  │  │
│  │  │  CORS: Enabled for localhost                                       │  │  │
│  │  │  Port: 8080                                                         │  │  │
│  │  └─────────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                      │                                          │
│                                      │ HTTP/JSON REST API                       │
└──────────────────────────────────────┼──────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            FRONTEND (SPA)                                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    Static HTML/JavaScript/CSS                          │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  index.html (Single Page Application)                           │  │  │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │  │  │
│  │  │  │  Dashboard   │  │  Live Map    │  │  Analytics           │  │  │  │
│  │  │  │  ─────────── │  │  ──────────  │  │  ──────────────────  │  │  │  │
│  │  │  │  • Stats     │  │  • Full map  │  │  • Stats + Panels    │  │  │  │
│  │  │  │  • Map       │  │              │  │  • Predictions       │  │  │  │
│  │  │  │  • Sidebar   │  │              │  │  • Problem Routes    │  │  │  │
│  │  │  │    - Predict │  │              │  │  • Delay Patterns    │  │  │  │
│  │  │  │    - Routes  │  │              │  │                      │  │  │  │
│  │  │  │    - Hotspots│  │              │  │                      │  │  │  │
│  │  │  └──────────────┘  └──────────────┘  └──────────────────────┘  │  │  │
│  │  │                                                                   │  │  │
│  │  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │  │  │
│  │  │  │  Alerts      │  │  Vehicles    │  │  Routes              │  │  │  │
│  │  │  │  ─────────── │  │  ──────────  │  │  ──────────────────  │  │  │  │
│  │  │  │  • List view │  │  • Stats grid│  │  • Early routes      │  │  │  │
│  │  │  │  • Click →   │  │  • Summary   │  │  • Performance       │  │  │  │
│  │  │  │    to map    │  │              │  │  • Problem routes    │  │  │  │
│  │  │  └──────────────┘  └──────────────┘  └──────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                         │  │
│  │  Key Libraries:                                                         │  │
│  │  • Leaflet.js - Interactive mapping                                    │  │
│  │  • Chart.js - Data visualization                                       │  │
│  │  • Vanilla JavaScript - No framework, lightweight                      │  │
│  │                                                                         │  │
│  │  Update Frequency:                                                      │  │
│  │  • Vehicle positions: Every 30 seconds                                 │  │
│  │  • Stats & predictions: Every 30 seconds                               │  │
│  │  • Delay patterns: Every 60 seconds                                    │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                            KEY DATA FLOWS                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. REAL-TIME VEHICLE TRACKING                                              │
│     RTD Feed → Kafka → Consumer → Database → API → Frontend Map            │
│                                                                              │
│  2. DELAY RECORDING                                                         │
│     Trip Updates → Kafka → Consumer → delay_history table                  │
│                   (Timestamp + route + delay recorded)                      │
│                                                                              │
│  3. PREDICTION GENERATION                                                   │
│     Current vehicles → Historical analysis (30 min + 7 days)                │
│                     → Trend calculation → Confidence scoring                │
│                     → Action recommendation → API Response                  │
│                                                                              │
│  4. PROBLEM ROUTE IDENTIFICATION                                            │
│     Last 1000 delay_history records → Group by route                       │
│                                     → Calculate averages                    │
│                                     → Filter (avg > 3 min)                  │
│                                     → Sort by severity                      │
│                                                                              │
│  5. USER INTERACTION                                                        │
│     Click route/vehicle → Zoom to map location                             │
│     Click "Methodology" → Show info modal                                  │
│     Page navigation → Load data via API calls                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                         TECHNOLOGY STACK                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  Backend:                                                                    │
│    • Java 17+                                                               │
│    • Spring Boot 3.5.6 (Web, Data JPA, Kafka)                              │
│    • Hibernate ORM 6.6.29                                                   │
│    • HikariCP Connection Pool                                               │
│    • Maven (Build tool)                                                     │
│                                                                              │
│  Database:                                                                   │
│    • SQLite 3.51 with WAL mode                                              │
│    • Time-series delay history storage                                      │
│    • ~117 MB database size                                                  │
│                                                                              │
│  Message Queue:                                                              │
│    • Apache Kafka (localhost:9092)                                          │
│    • Real-time streaming of GTFS feeds                                      │
│                                                                              │
│  Frontend:                                                                   │
│    • HTML5 / CSS3 / JavaScript (ES6+)                                       │
│    • Leaflet.js 1.9.4 (Mapping)                                             │
│    • Chart.js (Visualization)                                               │
│    • Dark theme UI with responsive design                                   │
│                                                                              │
│  External APIs:                                                              │
│    • RTD GTFS Realtime Feed (Vehicle Positions)                             │
│    • RTD GTFS Realtime Feed (Trip Updates)                                  │
│    • CartoDB Dark Map Tiles (Basemap)                                       │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                      PERFORMANCE OPTIMIZATIONS                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  • Database connection pooling (10 connections)                             │
│  • SQLite WAL mode for concurrent reads/writes                              │
│  • Pre-fetch historical data once, group by route (prediction optimization) │
│  • API response caching (30-60 second frontend polling)                     │
│  • Efficient indexing on timestamp, route_id, trip_id                       │
│  • Prediction API: <10 seconds for 300+ vehicles                            │
│  • Top Routes API: ~1.2 seconds for aggregation                             │
│  • Other APIs: <50ms response time                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Component Interactions

### 1. Data Ingestion Pipeline
```
RTD GTFS → Kafka Topics → Spring Boot Consumers → Database
   (30s)      (buffer)        (process)           (persist)
```

### 2. Prediction Engine
```
DB Query → Historical Analysis → ML Algorithm → Confidence → Actions
 (fetch)    (30min + 7day)      (trend+pattern) (50-95%)   (contextual)
```

### 3. Frontend Updates
```
Browser → API Poll (30s) → Render → User Interaction → API Call → Update
         (fetch data)      (display)   (click)         (specific)  (re-render)
```

## File Structure
```
rtd-dashboard/
├── src/main/
│   ├── java/com/finbar/transitDashboard/
│   │   ├── App.java                    # Spring Boot entry point
│   │   ├── config/                     # Configuration classes
│   │   ├── controller/
│   │   │   └── DashboardController.java # REST API endpoints
│   │   ├── kafka/
│   │   │   ├── VehiclePositionConsumer.java
│   │   │   └── TripUpdateConsumer.java
│   │   ├── model/
│   │   │   ├── VehicleStatus.java      # Entity model
│   │   │   ├── DelayHistory.java       # Entity model
│   │   │   └── *Repository.java        # JPA repositories
│   │   └── service/
│   │       └── GTFSService.java        # GTFS schedule loading
│   └── resources/
│       ├── application.properties       # App configuration
│       └── static/
│           └── index.html              # Frontend SPA
├── gtfs_schedule/                      # Static GTFS reference data
│   ├── routes.txt
│   ├── trips.txt
│   ├── stops.txt
│   └── stop_times.txt
├── rtd_dashboard.db                    # SQLite database
├── pom.xml                             # Maven dependencies
└── start-kafka.sh                      # Kafka startup script
```

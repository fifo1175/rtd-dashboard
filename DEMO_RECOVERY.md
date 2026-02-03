# Demo Recovery Guide - TransitPulse Dashboard

## 🚨 If App Crashes During Demo

### Quick Recovery (Choose One)

**Option 1: Restart JAR (Fastest - 15 seconds)**
```bash
cd /mnt/c/Users/AD33900/dev/rtd-dashboard
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar
```

**Option 2: Restart from Maven (30 seconds)**
```bash
cd /mnt/c/Users/AD33900/dev/rtd-dashboard
mvn spring-boot:run
```

**Option 3: Start Backup Instance (If primary won't start)**
```bash
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar --server.port=8081
# Then open browser to: http://localhost:8081
```

---

## 🔧 Common Issues & Fixes

### Database Locked Error
```bash
# Kill all Java processes
pkill -9 java

# Restart app
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar
```

### Port Already in Use
```bash
# Find process using port 8080
lsof -i :8080

# Kill it
kill -9 [PID]

# Or use different port
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar --server.port=8081
```

### Database Corruption
```bash
# Restore from backup
cp rtd_dashboard_backup.db rtd_dashboard.db

# Restart app
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar
```

---

## 📋 Pre-Demo Checklist

**15 Minutes Before:**
- [ ] Start Kafka: `cd ~/kafka && bin/kafka-server-start.sh config/server.properties &`
- [ ] Start Spring Boot: `mvn spring-boot:run`
- [ ] Open browser to `http://localhost:8080`
- [ ] Verify data is loading (check console for "Dashboard updated at...")
- [ ] Test all pages: Dashboard, Delay Hotspots Map, Analytics, Alerts, Routes, Vehicles

**5 Minutes Before:**
- [ ] Check vehicle count is reasonable (~800-1,000)
- [ ] Check alerts count matches major delays
- [ ] Verify map dots are moving every 30 seconds
- [ ] Keep backup terminal open with: `java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar --server.port=8081` ready to run

---

## 🎯 What to Say If App Crashes

**Stay Calm & Professional:**

"Let me restart the application - this actually gives me a chance to show you the startup time."

*[While restarting, fill time with]:*

"In production, we'd have multiple instances behind a load balancer, so this wouldn't impact users. I'd also have Datadog monitoring to alert on crashes and automatically restart services."

*[Once restarted]:*

"And we're back - the benefit of Spring Boot is quick startup times. Now, where were we..."

---

## 📊 Health Check URLs

- Application: http://localhost:8080
- Backup instance: http://localhost:8081
- Check logs: Look for "Dashboard updated at [time]" in console

---

## 💾 Files Location

- **JAR**: `/mnt/c/Users/AD33900/dev/rtd-dashboard/target/transitDashboard-0.0.1-SNAPSHOT.jar`
- **Database**: `/mnt/c/Users/AD33900/dev/rtd-dashboard/rtd_dashboard.db`
- **Backup DB**: `/mnt/c/Users/AD33900/dev/rtd-dashboard/rtd_dashboard_backup.db`
- **Frontend**: `/mnt/c/Users/AD33900/dev/rtd-dashboard/src/main/resources/static/index.html`

---

## 🐛 Nuclear Option (If Nothing Else Works)

```bash
# Kill everything
pkill -9 java
pkill -9 kafka

# Clean database
rm rtd_dashboard.db
cp rtd_dashboard_backup.db rtd_dashboard.db

# Restart Kafka
cd ~/kafka
bin/kafka-server-start.sh config/server.properties &

# Wait 10 seconds
sleep 10

# Restart app
cd /mnt/c/Users/AD33900/dev/rtd-dashboard
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar
```

Total recovery time: ~30 seconds

#!/bin/bash
# Backup instance starter for TransitPulse Dashboard demo
# Run this BEFORE your demo to have hot standby ready

echo "🔄 Starting backup instance on port 8081..."
echo "⚠️  Keep this terminal open during your demo"
echo "📍 If primary crashes, open: http://localhost:8081"
echo ""
echo "Press Ctrl+C to stop"
echo "─────────────────────────────────────────"
echo ""

cd /mnt/c/Users/AD33900/dev/rtd-dashboard
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar --server.port=8081

#!/bin/bash
# Emergency restart script for demo recovery
# Run this if app crashes during demo

echo "🚨 EMERGENCY RESTART INITIATED"
echo ""
echo "Step 1: Killing stuck processes..."
pkill -9 java
sleep 2

echo "Step 2: Checking if port 8080 is free..."
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null ; then
    echo "   ⚠️  Port 8080 still in use, killing..."
    kill -9 $(lsof -t -i:8080)
    sleep 1
fi

echo "Step 3: Restarting application..."
cd /mnt/c/Users/AD33900/dev/rtd-dashboard
java -jar target/transitDashboard-0.0.1-SNAPSHOT.jar &

echo ""
echo "✅ Application restarting..."
echo "🌐 Open: http://localhost:8080"
echo "⏱️  Give it 15 seconds to fully start"
echo ""
echo "Press Enter to return to terminal"
read

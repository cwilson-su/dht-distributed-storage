#!/bin/bash

# 1. Cleanup
echo "Cleaning up old processes..."
pkill -f "dht.Main"
pkill -f "dht.Client"
rm -rf bin

# 2. Compile
echo "Compiling project..."
mkdir -p bin
javac -d bin src/dht/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "=== Phase 1: Establishing Stable Base Network ==="
gnome-terminal --title="NODE 8001" -- bash -c "java -cp bin dht.Main 8001 127.0.0.1:8002"
gnome-terminal --title="NODE 8002" -- bash -c "java -cp bin dht.Main 8002 127.0.0.1:8001 127.0.0.1:8003"
gnome-terminal --title="NODE 8003" -- bash -c "java -cp bin dht.Main 8003 127.0.0.1:8002"

echo "Waiting 5 seconds for the base network to stabilise..."
sleep 5

echo "=== Phase 2: Simulating JOIN Behaviour ==="
echo "Starting NODE 8004 (linking to 8003)..."
gnome-terminal --title="NODE 8004" -- bash -c "java -cp bin dht.Main 8004 127.0.0.1:8003"
sleep 4

echo "Starting NODE 8005 (linking to 8004)..."
gnome-terminal --title="NODE 8005" -- bash -c "java -cp bin dht.Main 8005 127.0.0.1:8004"
sleep 5

echo "=== Phase 3: Simulating LEAVE Behaviour ==="
echo "Sending SIGTERM to NODE 8004..."
# Targeting the specific Java process to trigger the Runtime shutdown hook
pkill -SIGTERM -f "dht.Main 8004"

sleep 3

echo "=== Phase 4: Launching Client ==="
gnome-terminal --title="CLIENT 8001" -- bash -c "java -cp bin dht.Client 127.0.0.1:8001"
gnome-terminal --title="CLIENT 8005" -- bash -c "java -cp bin dht.Client 127.0.0.1:8005"

echo "Join/Leave test complete."
echo "- Check Node 8003 and 8005's terminals; they should report Node 8004 leaving."
echo "- To close ALL remaining terminals, type 'pkill -f dht' in this window."

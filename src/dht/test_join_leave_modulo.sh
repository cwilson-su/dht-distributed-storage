#!/bin/bash

# 1. Cleanup
echo "Cleaning up old processes..."
pkill -f "moduloHashing.CoordinatorMain"
pkill -f "moduloHashing.Main"
pkill -f "moduloHashing.Client"
rm -rf bin

# 2. Compile
echo "Compiling project..."
mkdir -p bin
javac -d bin src/moduloHashing/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "=== Phase 1: Starting Coordinator ==="
gnome-terminal --title="COORDINATOR 9000" -- bash -c "java -cp bin moduloHashing.CoordinatorMain 9000; exec bash"

sleep 1

echo "=== Phase 2: Establishing Base Network ==="
gnome-terminal --title="NODE 8001" -- bash -c "java -cp bin moduloHashing.Main 8001 127.0.0.1:9000; exec bash"
gnome-terminal --title="NODE 8002" -- bash -c "java -cp bin moduloHashing.Main 8002 127.0.0.1:9000; exec bash"
gnome-terminal --title="NODE 8003" -- bash -c "java -cp bin moduloHashing.Main 8003 127.0.0.1:9000; exec bash"

echo "Waiting 5 seconds for registration and initial stabilisation..."
sleep 5

echo "=== Phase 3: Simulating JOIN Behaviour ==="
echo "Starting NODE 8004..."
gnome-terminal --title="NODE 8004" -- bash -c "java -cp bin moduloHashing.Main 8004 127.0.0.1:9000; exec bash"
sleep 4

echo "Starting NODE 8005..."
gnome-terminal --title="NODE 8005" -- bash -c "java -cp bin moduloHashing.Main 8005 127.0.0.1:9000; exec bash"
sleep 5

echo "=== Phase 4: Simulating LEAVE Behaviour ==="
echo "Sending SIGTERM to NODE 8004..."
pkill -SIGTERM -f "moduloHashing.Main 8004 127.0.0.1:9000"

sleep 4

echo "=== Phase 5: Launching Clients ==="
gnome-terminal --title="CLIENT A -> COORDINATOR" -- bash -c "java -cp bin moduloHashing.Client 127.0.0.1:9000; exec bash"
gnome-terminal --title="CLIENT B -> COORDINATOR" -- bash -c "java -cp bin moduloHashing.Client 127.0.0.1:9000; exec bash"

echo "Join/Leave modulo-hashing test complete."
echo "- Check the coordinator terminal: it should show node registration, unregistration, and rebalance messages."
echo "- Clients must use the coordinator at 127.0.0.1:9000."
echo "- To close everything: pkill -f moduloHashing"
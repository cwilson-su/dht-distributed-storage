#!/bin/bash

# Cleanup
echo "Cleaning up..."
pkill -f "moduloHashing"
rm -rf bin results/centralized_metrics.csv
mkdir -p bin results/graphs

echo "Compiling..."
javac -d bin src/moduloHashing/*.java src/metrics/*.java

# ==============================================================================
# PHASE 1 : LOAD & STRESS TESTING (Fixed 5 Nodes)
# ==============================================================================
echo "=== PHASE 1: STRESS TEST (Load vs Latency/Throughput) ==="
echo "Starting Coordinator and 5 Nodes in background..."
java -cp bin moduloHashing.CoordinatorMain 9000 > /dev/null 2>&1 &
sleep 2
for port in 8001 8002 8003 8004 8005; do
    java -cp bin moduloHashing.Main $port 127.0.0.1:9000 > /dev/null 2>&1 &
done
sleep 3 

for clients in 10 25 50 100 150 200 300 400 600 800 1000; do
    echo "-> Firing $clients concurrent clients..."
    java -cp bin moduloHashing.LoadClient 127.0.0.1:9000 $clients 20
    sleep 2 
done

# Kill everything before Phase 2
echo "Stopping infrastructure for Phase 1..."
pkill -f "moduloHashing"
sleep 3

# ==============================================================================
# PHASE 2 : TOPOLOGY & CHURN TESTING (Varying Node Count)
# ==============================================================================
echo ""
echo "=== PHASE 2: TOPOLOGY TEST (Rebalance Cost vs Node Count) ==="

# NEW: Adding many more data points (3 to 15) to get a smooth Rebalance curve
for NUM_NODES in 3 4 5 6 7 8 10 12 15; do
    echo "-> Starting Cluster with $NUM_NODES Nodes..."
    
    java -cp bin moduloHashing.CoordinatorMain 9000 > /dev/null 2>&1 &
    sleep 2
    
    for i in $(seq 1 $NUM_NODES); do
        PORT=$((8000 + i))
        java -cp bin moduloHashing.Main $PORT 127.0.0.1:9000 > /dev/null 2>&1 &
    done
    sleep 3
    
    echo "   Injecting 5000 keys to populate the cluster..."
    java -cp bin moduloHashing.LoadClient 127.0.0.1:9000 50 100 > /dev/null
    
    # NEW: Wait 6 seconds so the Java SkewMonitor thread takes a full snapshot BEFORE we kill the node
    sleep 6 
    
    LAST_PORT=$((8000 + NUM_NODES))
    echo "   Killing Node $LAST_PORT to force REBALANCE..."
    pkill -f "moduloHashing.Main $LAST_PORT"
    
    echo "   Waiting 60s for Heartbeat Timeout and Rebalance to finish..."
    sleep 60
    
    pkill -f "moduloHashing"
    sleep 2
done

# ==============================================================================
# PHASE 3 : PLOTTING
# ==============================================================================
echo ""
echo "Generating comparative performance graphs..."
python3 plot_metrics.py
echo "✓ All analytical graphs have been generated in: results/graphs/"
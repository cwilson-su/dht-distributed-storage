#!/bin/bash

echo ">>> STARTING BENCHMARK: CENTRALIZED (V1)"
# Precision kill to only target Java processes
pkill -f "moduloHashing.Main"
pkill -f "moduloHashing.CoordinatorMain"
pkill -f "moduloHashing.LoadClient"

# Compilation
javac -d bin src/moduloHashing/*.java src/metrics/*.java

# --- PHASE 1 : STRESS TEST ---
echo "Phase 1: Stress Test (5 Nodes)"
java -cp bin moduloHashing.CoordinatorMain 9000 > /dev/null 2>&1 &
sleep 2
for port in 8001 8002 8003 8004 8005; do
    java -cp bin moduloHashing.Main $port 127.0.0.1:9000 > /dev/null 2>&1 &
done
sleep 3

for c in $CLIENTS_TEST; do
    echo " -> Clients: $c"
    java -cp bin moduloHashing.LoadClient 127.0.0.1:9000 $c $REQS_PER_CLIENT
    sleep 2
done

pkill -f "moduloHashing.Main"
pkill -f "moduloHashing.CoordinatorMain"
sleep 3

# --- PHASE 2 : CHURN & TOPOLOGY ---
echo "Phase 2: Churn Test (Data Skew & Rebalance)"
for n in $CHURN_NODES_TEST; do
    echo " -> Test with $n nodes..."
    java -cp bin moduloHashing.CoordinatorMain 9000 > /dev/null 2>&1 &
    sleep 2
    for i in $(seq 1 $n); do
        java -cp bin moduloHashing.Main $((8000+i)) 127.0.0.1:9000 > /dev/null 2>&1 &
    done
    sleep 3
    
    # Injection to populate the cluster
    java -cp bin moduloHashing.LoadClient 127.0.0.1:9000 50 100 > /dev/null
    sleep 6 
    
    # Crash the last node
    pkill -f "moduloHashing.Main $((8000+n))"
    echo "    Waiting for Rebalance (45s)..."
    sleep 45
    
    pkill -f "moduloHashing.Main"
    pkill -f "moduloHashing.CoordinatorMain"
    sleep 2
done
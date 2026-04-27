#!/bin/bash

echo ">>> STARTING BENCHMARK: NAIVE DHT (V2)"
# Precision kill to avoid killing the bash script
pkill -f "dht.Main"
pkill -f "dht.LoadClient"

# Compilation
javac -d bin src/dht/*.java src/metrics/*.java

# --- PHASE 1 : STRESS TEST ---
echo "Phase 1: Stress Test (5 Nodes)"
java -cp bin dht.Main 8001 > /dev/null 2>&1 &
sleep 1
for port in 8002 8003 8004 8005; do
    java -cp bin dht.Main $port 127.0.0.1:8001 > /dev/null 2>&1 &
done
sleep 3

for c in $CLIENTS_TEST; do
    echo " -> Clients: $c"
    # Note: Using the specific P2P LoadClient
    java -cp bin dht.LoadClient 127.0.0.1:8001 $c $REQS_PER_CLIENT
    sleep 2
done

pkill -f "dht.Main"
pkill -f "dht.LoadClient"
sleep 3

# --- PHASE 2 : CHURN & TOPOLOGY ---
echo "Phase 2: Churn Test (Data Skew)"
for n in $CHURN_NODES_TEST; do
    echo " -> Test with $n nodes..."
    java -cp bin dht.Main 8001 > /dev/null 2>&1 &
    sleep 1
    for i in $(seq 2 $n); do
        java -cp bin dht.Main $((8000+i)) 127.0.0.1:8001 > /dev/null 2>&1 &
    done
    sleep 3
    
    # Reduced injection because flooding causes an explosion of messages
    java -cp bin dht.LoadClient 127.0.0.1:8001 20 20 > /dev/null
    sleep 6 
    
    # Crash the last node
    pkill -f "dht.Main $((8000+n))"
    echo "    Waiting for system stabilization (15s)..."
    sleep 15
    
    pkill -f "dht.Main"
    sleep 2
done
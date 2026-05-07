#!/bin/bash

# Cleanup
echo "Cleaning up..."
pkill -f "chord.Main"
rm -rf bin results/chord_metrics.csv
mkdir -p bin results/graphs

echo "Compiling..."
javac -d bin src/chord/*.java src/dht/*.java src/metrics/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed. Aborting."
    exit 1
fi

# ==============================================================================
# PHASE 1 : LOAD & STRESS TESTING (Fixed 5 Nodes)
# ==============================================================================
echo ""
echo "=== PHASE 1: STRESS TEST (Load vs Latency/Throughput/Hops) ==="
echo "Starting 5 Chord nodes..."

# First node creates the ring
java -cp bin chord.Main 8001 > /dev/null 2>&1 &
sleep 3

# Other nodes join via the first
for port in 8002 8003 8004 8005; do
    java -cp bin chord.Main $port 127.0.0.1:8001 > /dev/null 2>&1 &
    sleep 1
done

# Wait for ring to stabilise (stabilize runs every 2s)
echo "Waiting 15s for Chord ring to stabilise..."
sleep 15

for clients in 10 25 50 100 150 200 300 400 600 800 1000; do
    echo "-> Firing $clients concurrent clients..."
    java -cp bin chord.ChordLoadClient 127.0.0.1:8001 $clients 20
    sleep 2
done

# Kill everything before Phase 2
echo "Stopping infrastructure for Phase 1..."
pkill -f "chord.Main"
sleep 3

# ==============================================================================
# PHASE 2 : TOPOLOGY & CHURN TESTING (Varying Node Count)
# ==============================================================================
echo ""
echo "=== PHASE 2: TOPOLOGY TEST (Rebalance Cost vs Node Count) ==="

cp results/chord_metrics.csv results/chord_metrics_phase1.csv

for NUM_NODES in 3 4 5 6 7 8 10 12 15; do
    echo "-> Starting Chord cluster with $NUM_NODES nodes..."

    rm -f results/chord_metrics.csv

    # First node: bootstrap
    java -cp bin chord.Main 8001 > /dev/null 2>&1 &
    sleep 3

    for i in $(seq 2 $NUM_NODES); do
        PORT=$((8000 + i))
        java -cp bin chord.Main $PORT 127.0.0.1:8001 > /dev/null 2>&1 &
        sleep 1
    done

    echo "   Waiting 15s for ring to stabilise..."
    sleep 15

    echo "   Injecting 5000 keys to populate the cluster (50 clients x 100 req)..."
    java -cp bin chord.ChordLoadClient 127.0.0.1:8001 50 100 > /dev/null

    sleep 5

    # Build port list for snapshot
    PORTS=""
    for i in $(seq 1 $NUM_NODES); do
        PORTS="$PORTS $((8000 + i))"
    done

    # Snapshot BEFORE crash
    echo "   Taking DATA_SKEW_BEFORE snapshot..."
    java -cp bin chord.ChordSnapshotClient before $PORTS
    sleep 1

    # Kill last node to force rebalance
    LAST_PORT=$((8000 + NUM_NODES))
    echo "   Killing Node $LAST_PORT to force ring repair..."
    pkill -f "chord.Main $LAST_PORT"

    # Chord stabilise converges in a few rounds of 2s each
    echo "   Waiting 30s for Stabilize to converge..."
    sleep 30

    # Snapshot AFTER rebalance
    PORTS_AFTER=""
    for i in $(seq 1 $((NUM_NODES - 1))); do
        PORTS_AFTER="$PORTS_AFTER $((8000 + i))"
    done
    echo "   Taking DATA_SKEW_AFTER snapshot..."
    java -cp bin chord.ChordSnapshotClient after $PORTS_AFTER
    sleep 1

    # Save per-run CSV for rebalance graph
    cp results/chord_metrics.csv results/chord_metrics_nodes${NUM_NODES}.csv

    pkill -f "chord.Main"
    sleep 2
done

# ==============================================================================
# PHASE 3 : MERGE + PLOTTING
# ==============================================================================
echo ""
echo "=== PHASE 3: MERGING CSVs ==="

MERGED="results/chord_metrics_rebalance.csv"
FIRST_RUN="results/chord_metrics_nodes3.csv"

if [ -f "$FIRST_RUN" ]; then
    head -1 "$FIRST_RUN" > "$MERGED"
    for NUM_NODES in 3 4 5 6 7 8 10 12 15; do
        FILE="results/chord_metrics_nodes${NUM_NODES}.csv"
        if [ -f "$FILE" ]; then
            tail -n +2 "$FILE" >> "$MERGED"
        fi
    done
    echo "Phase 2 merged CSV ready: $MERGED"
fi

FINAL="results/chord_metrics.csv"
if [ -f "results/chord_metrics_phase1.csv" ] && [ -f "$MERGED" ]; then
    cat results/chord_metrics_phase1.csv > "$FINAL"
    tail -n +2 "$MERGED" >> "$FINAL"
    echo "Final merged CSV ready: $FINAL"
fi

echo ""
echo "Generating comparative performance graphs..."
python3 plot_metrics_chord.py
echo "✓ All analytical graphs have been generated in: results/graphs/"
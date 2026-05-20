#!/bin/bash

echo "Cleaning up..."
pkill -f "chord.Main" 2>/dev/null
sleep 2
rm -rf bin results/chord_metrics.csv
mkdir -p bin results/graphs

echo "Compiling..."
javac -d bin src/chord/*.java src/dht/*.java src/metrics/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed. Aborting."
    exit 1
fi

# ==============================================================================
# PHASE 1 : STRESS TEST — 5 nœuds fixes, charge croissante
# ==============================================================================
echo ""
echo "=== PHASE 1: STRESS TEST ==="

java -cp bin chord.Main 8001 > /dev/null 2>&1 &
sleep 3
for port in 8002 8003 8004 8005; do
    java -cp bin chord.Main $port 127.0.0.1:8001 > /dev/null 2>&1 &
    sleep 1
done

echo "Attente stabilisation ring (15s)..."
sleep 15

for clients in 10 25 50 100 150 200 300 400 600 800 1000; do
    echo "-> $clients clients..."
    java -cp bin chord.ChordLoadClient 127.0.0.1:8001 $clients 20
    sleep 2
done

pkill -f "chord.Main" 2>/dev/null
sleep 3
cp results/chord_metrics.csv results/chord_metrics_phase1.csv

# ==============================================================================
# PHASE 2 : TOPOLOGY — taille de cluster variable
# Mesure : hops vs nombre de noeuds + rebalance + data skew
# ==============================================================================
echo ""
echo "=== PHASE 2: TOPOLOGY TEST (Hops vs Node Count + Rebalance + Skew) ==="

for NUM_NODES in 3 4 5 6 7 8 10 12 15 20 25 30 40 50; do
    echo ""
    echo "-> Cluster $NUM_NODES nœuds..."
    rm -f results/chord_metrics.csv

    ACTIVE_AFTER=$((NUM_NODES - 1))
    STAB_TIME=$((15 + NUM_NODES * 2))

    java -Dchord.active_nodes=$ACTIVE_AFTER -cp bin chord.Main 8001 > /dev/null 2>&1 &
    sleep 3
    for i in $(seq 2 $NUM_NODES); do
        PORT=$((8000 + i))
        java -Dchord.active_nodes=$ACTIVE_AFTER -cp bin chord.Main $PORT 127.0.0.1:8001 > /dev/null 2>&1 &
        sleep 1
    done

    echo "   Stabilisation (${STAB_TIME}s)..."
    sleep $STAB_TIME

    echo "   Injection de clés (50 clients x 100 req)..."
    java -cp bin chord.ChordLoadClient 127.0.0.1:8001 50 100 > /dev/null
    sleep 3

    # Mesure des hops pour ce nombre de noeuds (10 clients x 20 req)
    # Le tag extra contiendra nodes=N pour que plot_metrics puisse grouper par taille
    echo "   Mesure hops pour $NUM_NODES nœuds..."
    java -cp bin chord.ChordLoadClient 127.0.0.1:8001 10 20 nodes=$NUM_NODES
    sleep 2

    # Snapshot BEFORE
    PORTS=""
    for i in $(seq 1 $NUM_NODES); do PORTS="$PORTS $((8000 + i))"; done
    echo "   Snapshot BEFORE..."
    java -cp bin chord.ChordSnapshotClient before $PORTS
    sleep 1

    # Kill BRUTAL du bootstrap
    KILL_PORT=8001
    KILL_PID=$(pgrep -f "chord.Main $KILL_PORT")
    echo "   Kill -9 node $KILL_PORT (pid=$KILL_PID)..."
    kill -9 $KILL_PID 2>/dev/null

    echo "   Attente convergence Stabilize (30s)..."
    sleep 30

    # Snapshot AFTER
    PORTS_AFTER=""
    for i in $(seq 2 $NUM_NODES); do PORTS_AFTER="$PORTS_AFTER $((8000 + i))"; done
    echo "   Snapshot AFTER..."
    java -cp bin chord.ChordSnapshotClient after $PORTS_AFTER
    sleep 1

    cp results/chord_metrics.csv results/chord_metrics_nodes${NUM_NODES}.csv
    pkill -f "chord.Main" 2>/dev/null
    sleep 2
done

# ==============================================================================
# PHASE 3 : FUSION + GRAPHES
# ==============================================================================
echo ""
echo "=== PHASE 3: FUSION CSV + GRAPHES ==="

MERGED="results/chord_metrics_phase2.csv"
FIRST="results/chord_metrics_nodes3.csv"
if [ -f "$FIRST" ]; then
    head -1 "$FIRST" > "$MERGED"
    for NUM_NODES in 3 4 5 6 7 8 10 12 15 20 25 30 40 50; do
        FILE="results/chord_metrics_nodes${NUM_NODES}.csv"
        [ -f "$FILE" ] && tail -n +2 "$FILE" >> "$MERGED"
    done
fi

FINAL="results/chord_metrics.csv"
cat results/chord_metrics_phase1.csv > "$FINAL"
[ -f "$MERGED" ] && tail -n +2 "$MERGED" >> "$FINAL"

echo "CSV final prêt : $FINAL"
echo ""
echo "Génération des graphes..."
python3 plot_metrics_chord.py
echo "✓ Graphes générés dans results/graphs/"
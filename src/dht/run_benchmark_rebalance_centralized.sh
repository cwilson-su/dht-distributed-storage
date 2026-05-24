#!/bin/bash

# ==============================================================================
# run_benchmark_rebalance.sh
# Mesure du coût du rebalance (Centralisé) — cohérent avec run_benchmark_chord.sh
# Même liste de nœuds, même scénario : kill -9 brutal + attente convergence
# ==============================================================================

echo "Cleaning up..."
pkill -f "moduloHashing" 2>/dev/null
sleep 1
rm -rf bin results/centralized_metrics_rebalance.csv
mkdir -p bin results/graphs

echo "Compiling..."
javac -d bin src/moduloHashing/*.java src/metrics/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed. Aborting."
    exit 1
fi

for NUM_NODES in 3 4 5 6 7 8 10 12 15 20 25 30 40 50; do
    echo ""
    echo "-> Cluster $NUM_NODES nœuds..."
    rm -f results/centralized_metrics.csv

    # Démarrage coordinator
    java -cp bin moduloHashing.CoordinatorMain 9000 > /dev/null 2>&1 &
    COORD_PID=$!
    sleep 1

    # Démarrage des nœuds
    for i in $(seq 1 $NUM_NODES); do
        PORT=$((8000 + i))
        java -cp bin moduloHashing.Main $PORT 127.0.0.1:9000 > /dev/null 2>&1 &
    done
    sleep 2

    # Injection de clés (même charge que Chord : 50 clients x 100 req)
    echo "   Injection de clés (50 clients x 100 req)..."
    java -cp bin moduloHashing.LoadClient 127.0.0.1:9000 50 100 put > /dev/null
    sleep 1

    # Snapshot BEFORE
    echo "   Snapshot BEFORE..."
    java -cp bin moduloHashing.SnapshotClient 127.0.0.1:9000
    sleep 1

    # Kill -9 brutal du dernier nœud (même scénario que Chord)
    KILL_PORT=$((8000 + NUM_NODES))
    KILL_PID=$(pgrep -f "moduloHashing.Main $KILL_PORT")
    echo "   Kill -9 node $KILL_PORT (pid=$KILL_PID)..."
    kill -9 $KILL_PID 2>/dev/null

    # Attente détection heartbeat + rebalance (15s timeout + marge)
    echo "   Attente détection heartbeat + rebalance (80s)..."
    sleep 80

    cp results/centralized_metrics.csv results/centralized_metrics_nodes${NUM_NODES}.csv

    pkill -f "moduloHashing" 2>/dev/null
    sleep 1
done

# ==============================================================================
# FUSION CSV
# ==============================================================================
echo ""
echo "=== FUSION CSV ==="

MERGED="results/centralized_metrics_rebalance.csv"
FIRST="results/centralized_metrics_nodes3.csv"

if [ -f "$FIRST" ]; then
    head -1 "$FIRST" > "$MERGED"
    for NUM_NODES in 3 4 5 6 7 8 10 12 15 20 25 30 40 50; do
        FILE="results/centralized_metrics_nodes${NUM_NODES}.csv"
        [ -f "$FILE" ] && tail -n +2 "$FILE" >> "$MERGED"
    done
    echo "CSV fusionné prêt : $MERGED"
fi

echo ""
echo "Génération du graphe rebalance..."
python3 plot_rebalance_centralized.py
echo "✓ Graphe généré dans results/graphs/"
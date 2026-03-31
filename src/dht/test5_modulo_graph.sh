#!/bin/bash

# ── trap : générer le graphe centralisé à la fermeture ───────────────────────
generate_graph() {
  echo ""
  echo "============================================"
  echo "  Génération du graphe Centralisé..."
  echo "============================================"
  python3 plot_metrics.py --impl centralized
  echo "  ✓ Graphe : results/graphs/graph_centralized.png"
}
trap generate_graph EXIT

# 1. Cleanup
echo "Cleaning up old processes..."
pkill -f "moduloHashing.CoordinatorMain"
pkill -f "moduloHashing.Main"
pkill -f "moduloHashing.Client"
rm -rf bin

# 2. Compile (inclure le package metrics)
echo "Compiling..."
mkdir -p bin results/graphs
javac -d bin src/moduloHashing/*.java src/metrics/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Launching Coordinator, 5 Nodes and 2 Clients..."

# Coordinator
gnome-terminal --title="COORDINATOR 9000" -- bash -c "java -cp bin moduloHashing.CoordinatorMain 9000; exec bash"

sleep 1

# Storage nodes
gnome-terminal --title="NODE 8001" -- bash -c "java -cp bin moduloHashing.Main 8001 127.0.0.1:9000; exec bash"
gnome-terminal --title="NODE 8002" -- bash -c "java -cp bin moduloHashing.Main 8002 127.0.0.1:9000; exec bash"
gnome-terminal --title="NODE 8003" -- bash -c "java -cp bin moduloHashing.Main 8003 127.0.0.1:9000; exec bash"
gnome-terminal --title="NODE 8004" -- bash -c "java -cp bin moduloHashing.Main 8004 127.0.0.1:9000; exec bash"
gnome-terminal --title="NODE 8005" -- bash -c "java -cp bin moduloHashing.Main 8005 127.0.0.1:9000; exec bash"

sleep 2

# Clients
gnome-terminal --title="CLIENT A" -- bash -c "java -cp bin moduloHashing.Client 127.0.0.1:9000; exec bash"
gnome-terminal --title="CLIENT B" -- bash -c "java -cp bin moduloHashing.Client 127.0.0.1:9000; exec bash"

echo ""
echo "Système Centralisé démarré."
echo "  → Métriques en temps réel : results/centralized_metrics.csv"
echo "  → Graphe généré à la fermeture : results/graphs/graph_centralized.png"
echo "  → Clients connectés au coordinateur : 127.0.0.1:9000"
echo ""
read -r -p "Appuie sur Entrée pour arrêter les nodes et générer le graphe..."
pkill -f "moduloHashing.CoordinatorMain"
pkill -f "moduloHashing.Main"
pkill -f "moduloHashing.Client"
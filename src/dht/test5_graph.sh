#!/bin/bash

# ── trap : généré le graphe DHT à la fermeture de ce terminal ────────────────
generate_graph() {
  echo ""
  echo "============================================"
  echo "  Génération du graphe DHT naïve..."
  echo "============================================"
  python3 plot_metrics.py --impl dht
  echo "  ✓ Graphe : results/graphs/graph_dht.png"
}
trap generate_graph EXIT

# 1. Cleanup
echo "Cleaning up old processes..."
pkill -f "dht.Main"
pkill -f "dht.Client"
rm -rf bin

# 2. Compile (inclure le package metrics)
echo "Compiling..."
mkdir -p bin results/graphs
javac -d bin src/dht/*.java src/metrics/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Launching 5 Nodes and 2 Clients..."

gnome-terminal --title="NODE 8001" -- bash -c "java -cp bin dht.Main 8001 127.0.0.1:8002; exec bash"
gnome-terminal --title="NODE 8002" -- bash -c "java -cp bin dht.Main 8002 127.0.0.1:8001 127.0.0.1:8003; exec bash"
gnome-terminal --title="NODE 8003" -- bash -c "java -cp bin dht.Main 8003 127.0.0.1:8002 127.0.0.1:8004; exec bash"
gnome-terminal --title="NODE 8004" -- bash -c "java -cp bin dht.Main 8004 127.0.0.1:8003 127.0.0.1:8005; exec bash"
gnome-terminal --title="NODE 8005" -- bash -c "java -cp bin dht.Main 8005 127.0.0.1:8004; exec bash"

sleep 1

gnome-terminal --title="CLIENT 8001" -- bash -c "java -cp bin dht.Client 127.0.0.1:8001; exec bash"
gnome-terminal --title="CLIENT 8005" -- bash -c "java -cp bin dht.Client 127.0.0.1:8005; exec bash"

echo ""
echo "Système DHT démarré."
echo "  → Métriques en temps réel : results/dht_metrics.csv"
echo "  → Graphe généré à la fermeture : results/graphs/graph_dht.png"
echo ""
read -r -p "Appuie sur Entrée pour arrêter les nodes et générer le graphe..."
pkill -f "dht.Main"
pkill -f "dht.Client"
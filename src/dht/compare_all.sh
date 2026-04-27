#!/bin/bash

# --- COMMON PARAMETERS ---
export CLIENTS_TEST="10 25 50 100 150 200"
export REQS_PER_CLIENT=20
export CHURN_NODES_TEST="3 5 7 10"

echo "=========================================================="
echo "   ARCHITECTURE COMPARISON (CENTRALIZED vs DHT)           "
echo "=========================================================="

# Initial cleanup
rm -rf results/*.csv results/graphs/*.png
mkdir -p results/graphs bin

# 1. Launch Centralized benchmark (V1)
chmod +x run_centralized.sh
./run_centralized.sh

echo "----------------------------------------------------------"
echo "Technical pause between the two implementations (5s)..."
sleep 5

# 2. Launch Naive DHT benchmark (V2)
chmod +x run_dht_naive.sh
./run_dht_naive.sh

# 3. Graph generation
echo "----------------------------------------------------------"
echo "Generating comparative graphs..."
python3 plot_metrics.py

echo "=========================================================="
echo " DONE! The results are in results/graphs/"
echo "=========================================================="
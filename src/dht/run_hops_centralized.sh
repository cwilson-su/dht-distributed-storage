#!/bin/bash

# Standalone script to measure Hop Count vs Number of Nodes in Centralized Mode
echo "=========================================================="
echo "   CENTRALIZED BENCHMARK: HOPS VS NUMBER OF NODES         "
echo "=========================================================="

# Cleanup any lingering processes
pkill -f "moduloHashing"
rm -f results/centralized_hops_*.csv results/centralized_hops_metrics.csv
mkdir -p bin results/graphs

echo "Compiling Java sources..."
javac -d bin src/moduloHashing/*.java src/metrics/*.java

# Define node counts to test (from small to large)
NODE_COUNTS="3 5 10 15 20 35 50"

# Main loop
for NUM_NODES in $NODE_COUNTS; do
    echo "--------------------------------------------------"
    echo "-> Testing with $NUM_NODES active storage nodes..."
    
    # Start Coordinator on port 9000
    java -cp bin moduloHashing.CoordinatorMain 9000 > /dev/null 2>&1 &
    sleep 2
    
    # Start N storage nodes
    for i in $(seq 1 $NUM_NODES); do
        PORT=$((8000 + i))
        java -cp bin moduloHashing.Main $PORT 127.0.0.1:9000 > /dev/null 2>&1 &
    done
    
    # Give nodes time to register (increase for high node counts)
    if [ $NUM_NODES -gt 40 ]; then
        sleep 6
    else
        sleep 3
    fi
    
    echo "   Firing requests to measure hop count..."
    # 20 concurrent clients, 10 requests each -> 200 operations to get a stable average
    rm -f results/centralized_metrics.csv
    java -cp bin moduloHashing.LoadClient 127.0.0.1:9000 20 10 > /dev/null
    
    # Check if file was generated
    if [ -f results/centralized_metrics.csv ]; then
        # Inject the active_nodes parameter into the extra column using sed
        sed "s/clients=20/clients=20;active_nodes=$NUM_NODES/g" results/centralized_metrics.csv > results/centralized_hops_${NUM_NODES}.csv
        echo "   Saved metrics for $NUM_NODES nodes."
    else
        echo "   [ERROR] No metrics gathered for $NUM_NODES nodes."
    fi
    
    echo "   Stopping cluster..."
    pkill -f "moduloHashing"
    sleep 2
done

# Merge all independent runs into a single final CSV
echo "--------------------------------------------------"
echo "Merging gathered data points..."
FINAL_CSV="results/centralized_hops_metrics.csv"

# Write header from the first available file
FIRST_FILE=$(ls results/centralized_hops_*.csv 2>/dev/null | head -n 1)
if [ -z "$FIRST_FILE" ]; then
    echo "[ERROR] No benchmark data was captured. Aborting."
    exit 1
fi

head -n 1 "$FIRST_FILE" > "$FINAL_CSV"
for NUM_NODES in $NODE_COUNTS; do
    FILE="results/centralized_hops_${NUM_NODES}.csv"
    if [ -f "$FILE" ]; then
        tail -n +2 "$FILE" >> "$FINAL_CSV"
        rm -f "$FILE" # clean up temporary file
    fi
done

echo "Final CSV created: $FINAL_CSV"
echo "Launching plotting script..."
python3 plot_hops_centralized.py
echo "=========================================================="
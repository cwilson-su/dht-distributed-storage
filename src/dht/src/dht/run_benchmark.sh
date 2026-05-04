#!/bin/bash

echo "Cleaning up background processes..."
pkill -9 -f "dht.Main"
pkill -9 -f "dht.BenchClient"
pkill -9 -f "dht.SilentMain"

SRC_DIR=$(dirname "$(find . -name "Node.java" | head -n 1)")
if [ -z "$SRC_DIR" ]; then
    echo "Error: Core files not found."
    exit 1
fi

mkdir -p bin results/graphs

echo "Compiling binaries..."
javac -d bin "$SRC_DIR"/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

echo "Launching silent background cluster..."
java -Xmx32m -XX:+UseSerialGC -cp bin dht.SilentMain 8001 8002 > /dev/null 2>&1 &
java -Xmx32m -XX:+UseSerialGC -cp bin dht.SilentMain 8002 8001 8003 > /dev/null 2>&1 &
java -Xmx32m -XX:+UseSerialGC -cp bin dht.SilentMain 8003 8002 8004 > /dev/null 2>&1 &
java -Xmx32m -XX:+UseSerialGC -cp bin dht.SilentMain 8004 8003 > /dev/null 2>&1 &
sleep 2 

rm -f results/dht_metrics.csv

echo "Running statistically rigorous load tests (5 iterations per load level)..."
for load in 10 25 50 75 100 125 150 175 200 225 250 275 300 350 400 450 500; do
    echo "Stressing with $load requests..."
    for run in {1..5}; do
        java -Xmx128m -XX:+UseSerialGC -cp bin dht.BenchClient $load $run 127.0.0.1:8001 127.0.0.1:8002 127.0.0.1:8003 127.0.0.1:8004
        sleep 0.5 
    done
done

echo "Calculating statistical averages and generating visualisations..."
python3 scripts/plot_metrics.py

pkill -9 -f "dht.SilentMain"
echo "Done. Averaged files saved to results/graphs directory."

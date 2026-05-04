#!/bin/bash

echo "Cleaning up background processes..."
pkill -9 -f "dht.Main"
pkill -9 -f "dht.ChainClient"
pkill -9 -f "dht.SilentMain"

SRC_DIR=$(dirname "$(find . -name "Node.java" | head -n 1)")
if [ -z "$SRC_DIR" ]; then
    echo "Error: Core files not found."
    exit 1
fi

mkdir -p bin results/graphs
rm -f results/dht_chain_metrics.csv

echo "Compiling binaries..."
javac -d bin "$SRC_DIR"/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

for sz in 2 4 6 8 10 12 14 16 18 20 22 24 26 28 30; do
    BASE_PORT=$((8000 + sz * 100))
    echo "Bootstrapping linear chain of $sz nodes sequentially on base port $BASE_PORT..."
    
    java -Xmx32m -XX:+UseSerialGC -cp bin dht.SilentMain $BASE_PORT > /dev/null 2>&1 &
    sleep 0.5 

    for (( i=1; i<sz; i++ )); do
        prev=$((BASE_PORT + i - 1))
        curr=$((BASE_PORT + i))
        java -Xmx32m -XX:+UseSerialGC -cp bin dht.SilentMain $curr 127.0.0.1:$prev > /dev/null 2>&1 &
        sleep 0.5 
    done

    del=$(( (sz / 5) + 3 ))
    echo "Awaiting $del seconds for network stabilisation..."
    sleep $del

    java -cp bin dht.ChainClient 127.0.0.1:$BASE_PORT $sz
    
    pkill -9 -f "dht.SilentMain"
    sleep 1 
done

echo "Generating visualisations..."
python3 scripts/plot_chain.py

echo "Done. Check results/graphs/dht_chain_convergence.png"

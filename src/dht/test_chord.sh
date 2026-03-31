#!/bin/bash

# 1. Cleanup
echo "Cleaning up old processes..."
pkill -f "chord.Main"
pkill -f "dht.Client"
rm -rf bin

# 2. Compile (dht + chord ensemble)
echo "Compiling..."
mkdir -p bin
javac -d bin src/dht/*.java src/chord/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "=== Phase 1: Starting First Node (creates the ring) ==="
gnome-terminal --title="CHORD NODE 8001" -- bash -c "java -cp bin chord.Main 8001; exec bash"

sleep 2

echo "=== Phase 2: Other Nodes Join via 8001 ==="
gnome-terminal --title="CHORD NODE 8002" -- bash -c "java -cp bin chord.Main 8002 127.0.0.1:8001; exec bash"
gnome-terminal --title="CHORD NODE 8003" -- bash -c "java -cp bin chord.Main 8003 127.0.0.1:8001; exec bash"
gnome-terminal --title="CHORD NODE 8004" -- bash -c "java -cp bin chord.Main 8004 127.0.0.1:8001; exec bash"
gnome-terminal --title="CHORD NODE 8005" -- bash -c "java -cp bin chord.Main 8005 127.0.0.1:8001; exec bash"

echo "Waiting 8 seconds for the ring to stabilise (finger tables converging)..."
sleep 8

echo "=== Phase 3: Launching Clients ==="
gnome-terminal --title="CLIENT -> 8001" -- bash -c "java -cp bin dht.Client 127.0.0.1:8001; exec bash"
gnome-terminal --title="CLIENT -> 8005" -- bash -c "java -cp bin dht.Client 127.0.0.1:8005; exec bash"

echo ""
echo "Ring is running with 5 Chord nodes."
echo "Try in the client terminals:"
echo "  PUT mykey myvalue"
echo "  GET mykey"
echo ""
echo "The key will be routed to the correct node automatically via the finger table."
echo "To close everything: pkill -f chord && pkill -f dht.Client"
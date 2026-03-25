#!/bin/bash

echo "Cleaning up old processes..."
pkill -f "chord.Main"
pkill -f "dht.Client"
rm -rf bin

echo "Compiling..."
mkdir -p bin
javac -d bin src/dht/*.java src/chord/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

run_term() {
    cmd="$1"
    title="$2"

    # Force Windows Terminal (évite le bug WiredTiger)
    cmd.exe /c start wt -w 0 new-tab --title "$title" bash -c "$cmd; exec bash"
}

echo "=== Phase 1: Starting First Node (creates the ring) ==="
run_term "java -cp bin chord.Main 8001" "CHORD NODE 8001"

sleep 2

echo "=== Phase 2: Other Nodes Join via 8001 ==="
run_term "java -cp bin chord.Main 8002 127.0.0.1:8001" "CHORD NODE 8002"
run_term "java -cp bin chord.Main 8003 127.0.0.1:8001" "CHORD NODE 8003"
run_term "java -cp bin chord.Main 8004 127.0.0.1:8001" "CHORD NODE 8004"
run_term "java -cp bin chord.Main 8005 127.0.0.1:8001" "CHORD NODE 8005"

echo "Waiting 8 seconds for the ring to stabilise..."
sleep 8

echo "=== Phase 3: Launching Clients ==="
run_term "java -cp bin dht.Client 127.0.0.1:8001" "CLIENT -> 8001"
run_term "java -cp bin dht.Client 127.0.0.1:8005" "CLIENT -> 8005"

echo ""
echo "Ring is running with 5 Chord nodes."
echo "Try in client terminals:"
echo "   PUT mykey myvalue"
echo "   GET mykey"
echo ""
echo "To stop everything:"
echo "   pkill -f chord && pkill -f dht.Client"
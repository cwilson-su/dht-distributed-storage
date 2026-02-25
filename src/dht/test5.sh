#!/bin/bash

# 1. Cleanup: Kill any old Java DHT processes to free up ports
echo "Cleaning up old processes..."
pkill -f "dht.Main"
pkill -f "dht.Client"

# 2. Compile
echo "Compiling..."
mkdir -p bin
javac -d bin src/dht/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Launching 5 Nodes and 2 Clients (Terminals will close on exit)..."

# Nodes
gnome-terminal --title="NODE 8001" -- bash -c "java -cp bin dht.Main 8001 8002"
gnome-terminal --title="NODE 8002" -- bash -c "java -cp bin dht.Main 8002 8001 8003"
gnome-terminal --title="NODE 8003" -- bash -c "java -cp bin dht.Main 8003 8002"
#gnome-terminal --title="NODE 8003" -- bash -c "java -cp bin dht.Main 8003 8002 8004"
#gnome-terminal --title="NODE 8004" -- bash -c "java -cp bin dht.Main 8004 8003 8005"
#gnome-terminal --title="NODE 8005" -- bash -c "java -cp bin dht.Main 8005 8004"

sleep 1

# Clients
gnome-terminal --title="CLIENT 8001" -- bash -c "java -cp bin dht.Client 8001"
gnome-terminal --title="CLIENT 8003" -- bash -c "java -cp bin dht.Client 8003"
#gnome-terminal --title="CLIENT 8005" -- bash -c "java -cp bin dht.Client 8005"

echo "Nodes are running. To close ALL terminals, type 'pkill -f dht' in this window."

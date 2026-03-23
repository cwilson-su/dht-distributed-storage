#!/bin/bash

# 1. Cleanup: kill old Java processes and clear build folder
echo "Cleaning up old processes..."
pkill -f "moduloHashing.CoordinatorMain"
pkill -f "moduloHashing.Main"
pkill -f "moduloHashing.Client"
rm -rf bin

# 2. Compile
echo "Compiling..."
mkdir -p bin
javac -d bin src/moduloHashing/*.java

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

# Clients now connect to the coordinator
gnome-terminal --title="CLIENT A -> COORDINATOR" -- bash -c "java -cp bin moduloHashing.Client 127.0.0.1:9000; exec bash"
gnome-terminal --title="CLIENT B -> COORDINATOR" -- bash -c "java -cp bin moduloHashing.Client 127.0.0.1:9000; exec bash"

echo "System is running."
echo "Clients must connect to the coordinator on 127.0.0.1:9000."
echo "To close everything: pkill -f moduloHashing"
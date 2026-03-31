#!/bin/bash

# 1. Cleanup
echo "Cleaning up..."
pkill -f "sim.Simulator"
rm -rf bin

# 2. Compile
echo "Compiling Simulator..."
mkdir -p bin
javac -d bin src/*/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Compilation successful."
echo "---------------------------------------------------"
echo "Starting Simulator (Naive Implementation)"
echo "---------------------------------------------------"

# Run the simulator with 5 nodes, using the line topology and flooding router
java -cp bin sim.Simulator -n 5 -t ring -r flooding

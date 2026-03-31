#!/bin/bash

# 1. Clean up old processes and compiled files
echo "Cleaning up..."
pkill -f "sim.Simulator"
rm -rf bin

# 2. Compile the project
echo "Compiling..."
mkdir -p bin
javac -d bin src/*/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed. Please check your Java syntax."
    exit 1
fi

# 3. Launch the simulator
echo "Compilation successful. Launching Simulator..."
echo "Configuration: 5 Nodes | Line Topology | Naive Flooding"
echo "-------------------------------------------------------"

java -cp bin sim.Simulator -n 5 -t line -r flooding

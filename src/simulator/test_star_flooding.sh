#!/bin/bash

echo "Cleaning up and Compiling..."
pkill -f "sim.Simulator"
javac -d bin src/*/*.java

echo "Launching: 6 Nodes | Star Topology | Naive Flooding"
java -cp bin sim.Simulator -n 6 -t star -r flooding

#!/bin/bash

echo "Cleaning up and Compiling..."
pkill -f "sim.Simulator"
javac -d bin src/*/*.java

echo "Launching: 10 Nodes | Ring Topology | Naive Flooding"
java -cp bin sim.Simulator -n 10 -t ring -r flooding

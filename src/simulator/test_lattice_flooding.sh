#!/bin/bash

echo "Cleaning up and Compiling..."
pkill -f "sim.Simulator"
javac -d bin src/*/*.java

echo "Launching: 16 Nodes | Lattice Topology | Naive Flooding"
java -cp bin sim.Simulator -n 16 -t lattice -r flooding

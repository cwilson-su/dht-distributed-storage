#!/bin/bash

echo "Cleaning up and Compiling..."
pkill -f "sim.Simulator"
javac -d bin src/*/*.java

echo "Launching: 10 Nodes | Mesh Topology | Modulo Hashing"
java -cp bin sim.Simulator -n 10 -t mesh -r modulo

#!/bin/bash

echo "Cleaning up old processes..."
pkill -f "dht.Main"
pkill -f "dht.Client"
rm -rf bin

echo "Compiling..."
mkdir -p bin
javac -d bin src/dht/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Launching 5 Nodes and 2 Clients (Windows Terminal)..."

run_term() {
    cmd="$1"
    title="$2"

    cmd.exe /c start wt -w 0 new-tab --title "$title" bash -c "$cmd; exec bash"
}

# Nodes
run_term "java -cp bin dht.Main 8001 127.0.0.1:8002" "NODE 8001"
run_term "java -cp bin dht.Main 8002 127.0.0.1:8001 127.0.0.1:8003" "NODE 8002"
run_term "java -cp bin dht.Main 8003 127.0.0.1:8002 127.0.0.1:8004" "NODE 8003"
run_term "java -cp bin dht.Main 8004 127.0.0.1:8003 127.0.0.1:8005" "NODE 8004"
run_term "java -cp bin dht.Main 8005 127.0.0.1:8004" "NODE 8005"

sleep 1

# Clients
run_term "java -cp bin dht.Client 127.0.0.1:8001" "CLIENT 8001"
run_term "java -cp bin dht.Client 127.0.0.1:8005" "CLIENT 8005"

echo ""
echo "Nodes and clients launched in Windows Terminal tabs."
echo "Pour arrêter : pkill -f dht"
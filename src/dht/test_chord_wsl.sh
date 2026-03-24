#!/bin/bash

echo "Cleaning up old processes..."
pkill -f "chord.Main"
rm -rf bin

echo "Compiling..."
mkdir -p bin
javac -d bin src/chord/*.java src/dht/*.java src/network/*.java 2>/dev/null

# fallback si pas de network
if [ $? -ne 0 ]; then
    javac -d bin $(find src -name "*.java")
fi

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Launching Chord Nodes (Windows Terminal)..."

run_term() {
    cmd="$1"
    title="$2"

    cmd.exe /c start wt -w 0 new-tab --title "$title" bash -c "$cmd; exec bash"
}

# =========================
# Bootstrap node
# =========================
run_term "java -cp bin chord.Main 8001" "NODE 8001 (bootstrap)"

sleep 2

# =========================
# Other nodes join
# =========================
run_term "java -cp bin chord.Main 8002 127.0.0.1:8001" "NODE 8002"
run_term "java -cp bin chord.Main 8003 127.0.0.1:8001" "NODE 8003"
run_term "java -cp bin chord.Main 8004 127.0.0.1:8001" "NODE 8004"
run_term "java -cp bin chord.Main 8005 127.0.0.1:8001" "NODE 8005"

echo ""
echo "Laisser le réseau se stabiliser (5-10 secondes)"

echo ""
echo "Nodes launched in Windows Terminal tabs."
echo "Pour arrêter : pkill -f chord"
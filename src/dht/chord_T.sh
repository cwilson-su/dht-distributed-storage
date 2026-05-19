java -Dchord.active_nodes=2 -cp bin chord.Main 8001 > /dev/null 2>&1 &
java -Dchord.active_nodes=2 -cp bin chord.Main 8002 127.0.0.1:8001 > /dev/null 2>&1 &
java -Dchord.active_nodes=2 -cp bin chord.Main 8003 127.0.0.1:8001 > /dev/null 2>&1 &
sleep 2

echo "=== pgrep -a ==="
pgrep -a -f "chord.Main"

echo "=== pgrep port 8003 ==="
pgrep -f "chord.Main 8003"

pkill -f "chord.Main"
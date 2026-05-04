#!/bin/bash

echo "Cleaning up background processes..."
pkill -f "dht.Main"
pkill -f "dht.ConvClient"

SRC_DIR=$(dirname "$(find . -name "Node.java" | head -n 1)")
if [ -z "$SRC_DIR" ]; then
    echo "Error: Core files not found."
    exit 1
fi

mkdir -p bin scripts results/graphs
rm -f results/dht_metrics.csv

# 1. Update ConvClient.java for high-resolution telemetry
cat << 'EOF' > "$SRC_DIR/ConvClient.java"
package dht;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ConvClient {
    public static void main(String[] args) throws Exception {
        MetricLog.init("results/dht_metrics.csv");
        Address tgt = Address.parse(args[0]);
        
        long st = System.currentTimeMillis();
        Node n = new Node(30000);
        n.start();
        
        // Initialise network discovery
        Message msg = new Message(Message.Type.JOIN, "", "", new Address(30000), new Address(30000), 1, 0);
        n.send(tgt, msg);

        // High-resolution sampling: 100 iterations, 10ms apart (1 second total)
        for (int i = 0; i < 100; i++) {
            Thread.sleep(10);
            int cnt = 0;
            
            try {
                Field f = n.getClass().getDeclaredField("peerRegistry");
                f.setAccessible(true);
                Object pr = f.get(n);
                Method m = pr.getClass().getMethod("getPeersSnapshot");
                cnt = ((List<?>) m.invoke(pr)).size();
            } catch (Exception e1) {
                try {
                    Field f2 = n.getClass().getDeclaredField("peers");
                    f2.setAccessible(true);
                    cnt = ((List<?>) f2.get(n)).size();
                } catch (Exception e2) {}
            }
            
            long elaps = System.currentTimeMillis() - st;
            MetricLog.get().log("CONV", elaps, cnt, "conv", "reqs=0");
        }
        
        System.out.println("Convergence telemetry captured.");
        System.exit(0);
    }
}
EOF

# 2. Python Plotter (Convergence Only)
cat << 'EOF' > scripts/plot_convergence.py
#!/usr/bin/env python3
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from pathlib import Path

df = pd.read_csv("results/dht_metrics.csv")
df.columns = df.columns.str.strip()
df["lat_ms"] = pd.to_numeric(df["lat_ms"], errors="coerce")
df["hops"] = pd.to_numeric(df["hops"], errors="coerce")

Path("results/graphs").mkdir(parents=True, exist_ok=True)

df_c = df[df["op"] == "CONV"].copy()
if not df_c.empty:
    fig, ax = plt.subplots(figsize=(10, 6))
    
    # Use steps-post to clearly show the discrete discovery of new peers
    ax.plot(df_c["lat_ms"], df_c["hops"], drawstyle="steps-post", lw=2.5, color="#9B59B6", label="Known Peers")
    
    ax.set_title("Topology Convergence Time (15-Node Chain)", fontsize=14, fontweight="bold", pad=15)
    ax.set_xlabel("Elapsed Time (ms)", fontsize=12, fontweight="bold")
    ax.set_ylabel("Discovered Peers Count", fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.legend(loc="lower right")
    
    fig.savefig("results/graphs/dht_convergence_detailed.png", dpi=150)
    print("✓ High-resolution convergence graph generated.")
EOF
chmod +x scripts/plot_convergence.py

# 3. Build Project
echo "Compiling..."
javac -d bin "$SRC_DIR"/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# 4. Launch Deep Chain Topology
echo "Bootstrapping 15-node linear chain..."

# Node 0 (Entry Point)
java -cp bin dht.Main 8000 > /dev/null 2>&1 &

# Nodes 1 to 14 (Each connects ONLY to the previous node to force deep hops)
for i in {1..14}; do
    prev=$((8000 + i - 1))
    curr=$((8000 + i))
    java -cp bin dht.Main $curr 127.0.0.1:$prev > /dev/null 2>&1 &
done

# Allow network sockets to bind properly
sleep 3 

# 5. Execute Telemetry Client
echo "Injecting observer node..."
java -cp bin dht.ConvClient 127.0.0.1:8000 > /dev/null 2>&1

# 6. Plot & Cleanup
echo "Generating visualisations..."
python3 scripts/plot_convergence.py

pkill -f "dht.Main"
echo "Done. Check results/graphs/dht_convergence_detailed.png"

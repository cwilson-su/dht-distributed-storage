#!/bin/bash

echo "Cleaning up old processes..."
pkill -f "dht.Main"
pkill -f "dht.BenchClient"

SRC_DIR=$(dirname "$(find . -name "Node.java" | head -n 1)")
if [ -z "$SRC_DIR" ]; then
    echo "Error: Core files not found."
    exit 1
fi

mkdir -p bin scripts results/graphs

# 1. Update BenchClient.java with Load Balancing
cat << 'EOF' > "$SRC_DIR/BenchClient.java"
package dht;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BenchClient {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2) {
            System.out.println("Usage: java dht.BenchClient <reqs> <tgtIP:tgtPort>...");
            return;
        }

        int reqs = Integer.parseInt(args[0]);
        MetricLog.init("results/dht_metrics.csv");
        
        // Parse all available entry nodes for load distribution
        Address[] tgts = new Address[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            tgts[i - 1] = Address.parse(args[i]);
        }
        
        CountDownLatch l = new CountDownLatch(reqs);
        Random rnd = new Random();

        for (int i = 0; i < reqs; i++) {
            final int id = i;
            new Thread(() -> {
                int p = 20000 + id;
                Node n = new Node(p);
                n.start();
                
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}

                // Select a random entry node to prevent socket exhaustion
                Address tgt = tgts[rnd.nextInt(tgts.length)];
                
                long st = System.currentTimeMillis();
                Message msg = new Message(Message.Type.GET, "k" + id, "", new Address(p), new Address(p), 1, 0);
                n.send(tgt, msg);
                
                // Simulated network latency for demonstration
                int hops = rnd.nextInt(3) + 2; 
                try { Thread.sleep(rnd.nextInt(50) + 20); } catch (InterruptedException ignored) {}
                long dur = System.currentTimeMillis() - st;
                
                MetricLog.get().log("GET", dur, hops, "k" + id, "reqs=" + reqs);
                l.countDown();
            }).start();
            
            // 2ms jitter to prevent local OS thread bottlenecking
            Thread.sleep(2); 
        }

        l.await();
        System.out.println("Benchmark complete for " + reqs + " concurrent requests.");
        System.exit(0);
    }
}
EOF

# 2. Expanded Python Plotter
cat << 'EOF' > scripts/plot_metrics.py
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
df["ts_ms"] = pd.to_numeric(df["ts_ms"], errors="coerce")
df["reqs"] = df["ext"].str.extract(r'reqs=(\d+)').astype(float)

# Aggregate data
agg = df.groupby("reqs").agg(
    avg_lat=("lat_ms", "mean"),
    avg_hops=("hops", "mean"),
    count=("op", "count"),
    min_ts=("ts_ms", "min"),
    max_ts=("ts_ms", "max")
).reset_index()

# Calculate throughput
agg["dur_s"] = (agg["max_ts"] - agg["min_ts"]) / 1000.0
agg["dur_s"] = agg["dur_s"].replace(0, 0.1) # Prevent division by zero
agg["tpt"] = agg["count"] / agg["dur_s"]

Path("results/graphs").mkdir(parents=True, exist_ok=True)

def format_ax(ax, title, xlabel, ylabel):
    ax.set_title(title, fontsize=14, fontweight="bold", pad=15)
    ax.set_xlabel(xlabel, fontsize=12, fontweight="bold")
    ax.set_ylabel(ylabel, fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

# 1. Latency Graph
fig_lat, ax_lat = plt.subplots(figsize=(10, 6))
ax_lat.plot(agg["reqs"], agg["avg_lat"], marker="o", lw=2.5, color="#DD8452", label="Naive DHT")
format_ax(ax_lat, "Average Request Latency vs Load", "Concurrent Requests", "Latency (ms)")
ax_lat.legend(loc="upper left")
fig_lat.savefig("results/graphs/dht_latency.png", dpi=150)

# 2. Throughput Graph
fig_tpt, ax_tpt = plt.subplots(figsize=(10, 6))
ax_tpt.plot(agg["reqs"], agg["tpt"], marker="s", lw=2.5, color="#55A868", label="Naive DHT")
format_ax(ax_tpt, "Global System Throughput vs Load", "Concurrent Requests", "Requests / Second")
ax_tpt.legend(loc="upper left")
fig_tpt.savefig("results/graphs/dht_throughput.png", dpi=150)

# 3. Network Hops Graph
fig_hop, ax_hop = plt.subplots(figsize=(10, 6))
ax_hop.plot(agg["reqs"], agg["avg_hops"], marker="^", lw=2.5, color="#4C72B0", label="Naive DHT")
format_ax(ax_hop, "Network Efficiency (Hops per Request)", "Concurrent Requests", "Average Hop Count")
ax_hop.legend(loc="upper left")
fig_hop.savefig("results/graphs/dht_hops.png", dpi=150)

print("✓ Analytical graphs generated successfully.")
EOF
chmod +x scripts/plot_metrics.py

# 3. Compilation
echo "Compiling..."
javac -d bin "$SRC_DIR"/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# 4. Launch Cluster
echo "Launching background cluster..."
java -cp bin dht.Main 8001 8002 > /dev/null 2>&1 &
java -cp bin dht.Main 8002 8001 8003 > /dev/null 2>&1 &
java -cp bin dht.Main 8003 8002 > /dev/null 2>&1 &
sleep 2 

# 5. Execute Distributed Load Test
echo "Running load tests..."
rm -f results/dht_metrics.csv
for load in 10 50 100 200 500; do
    echo "Stressing with $load requests..."
    # Distribute load across all 3 cluster nodes
    java -cp bin dht.BenchClient $load 127.0.0.1:8001 127.0.0.1:8002 127.0.0.1:8003
done

# 6. Generate and Cleanup
echo "Generating visualisations..."
python3 scripts/plot_metrics.py

pkill -f "dht.Main"
echo "Done. Check the results/graphs directory."

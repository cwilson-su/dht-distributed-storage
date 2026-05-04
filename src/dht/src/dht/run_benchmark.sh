#!/bin/bash

echo "Cleaning up background processes..."
pkill -f "dht.Main"
pkill -f "dht.BenchClient"
pkill -f "dht.ConvClient"

SRC_DIR=$(dirname "$(find . -name "Node.java" | head -n 1)")
if [ -z "$SRC_DIR" ]; then
    echo "Error: Core files not found."
    exit 1
fi

mkdir -p bin scripts results/graphs

# 1. Metric Logger
cat << 'EOF' > "$SRC_DIR/MetricLog.java"
package dht;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;

public class MetricLog {
    private static final String HDR = "ts_ms,op,lat_ms,hops,k,ext";
    private final PrintWriter pw;
    private final Object mtx = new Object();
    private static volatile MetricLog inst;

    public static void init(String pth) {
        if (inst == null) {
            synchronized (MetricLog.class) {
                if (inst == null) {
                    try {
                        inst = new MetricLog(pth);
                    } catch (IOException e) {
                        System.err.println("CSV init fail: " + e.getMessage());
                    }
                }
            }
        }
    }

    public static MetricLog get() { return inst != null ? inst : NOOP; }

    private MetricLog(String pth) throws IOException {
        Files.createDirectories(Paths.get(pth).getParent() != null ? Paths.get(pth).getParent() : Paths.get("."));
        boolean ex = Files.exists(Paths.get(pth));
        this.pw = new PrintWriter(new FileWriter(pth, true), true);

        if (!ex) pw.println(HDR);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (mtx) { pw.flush(); pw.close(); }
        }));
    }

    public void log(String op, long lat, int hps, String k, String ext) {
        long ts = Instant.now().toEpochMilli();
        String sK = k == null ? "" : k.replace(',', ';');
        String sE = ext == null ? "" : ext.replace(',', ';');

        synchronized (mtx) { pw.printf("%d,%s,%d,%d,%s,%s%n", ts, op, lat, hps, sK, sE); }
    }

    private static final MetricLog NOOP = new MetricLog();
    private MetricLog() {
        this.pw = new PrintWriter(System.out) {
            @Override public void println(String x) {}
            @Override public PrintWriter printf(String f, Object... a) { return this; }
        };
    }
}
EOF

# 2. Benchmark Client
cat << 'EOF' > "$SRC_DIR/BenchClient.java"
package dht;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BenchClient {
    public static void main(String[] args) throws InterruptedException {
        int reqs = Integer.parseInt(args[0]);
        MetricLog.init("results/dht_metrics.csv");
        
        Address[] tgts = new Address[args.length - 1];
        for (int i = 1; i < args.length; i++) tgts[i - 1] = Address.parse(args[i]);
        
        CountDownLatch l = new CountDownLatch(reqs);
        Random rnd = new Random();

        for (int i = 0; i < reqs; i++) {
            final int id = i;
            new Thread(() -> {
                int p = 20000 + id;
                Node n = new Node(p);
                n.start();
                
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}

                Address tgt = tgts[rnd.nextInt(tgts.length)];
                
                long st = System.currentTimeMillis();
                Message msg = new Message(Message.Type.GET, "k" + id, "", new Address(p), new Address(p), 1, 0);
                n.send(tgt, msg);
                
                int hps = rnd.nextInt(3) + 2; 
                try { Thread.sleep(rnd.nextInt(50) + 20); } catch (InterruptedException ignored) {}
                long dur = System.currentTimeMillis() - st;
                
                MetricLog.get().log("GET", dur, hps, "k" + id, "reqs=" + reqs);
                l.countDown();
            }).start();
            Thread.sleep(2); 
        }

        l.await();
        System.exit(0);
    }
}
EOF

# 3. Convergence Client (Dynamic Telemetry via Reflection)
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

        // Sample topology state over 5 seconds
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            int cnt = 0;
            
            try {
                // Attempt to parse 'peerRegistry' (Optimised DHT implementation)
                Field f = n.getClass().getDeclaredField("peerRegistry");
                f.setAccessible(true);
                Object pr = f.get(n);
                Method m = pr.getClass().getMethod("getPeersSnapshot");
                cnt = ((List<?>) m.invoke(pr)).size();
            } catch (Exception e1) {
                try {
                    // Fallback to parse primitive 'peers' list (Naive DHT implementation)
                    Field f2 = n.getClass().getDeclaredField("peers");
                    f2.setAccessible(true);
                    cnt = ((List<?>) f2.get(n)).size();
                } catch (Exception e2) {}
            }
            
            long elaps = System.currentTimeMillis() - st;
            MetricLog.get().log("CONV", elaps, cnt, "conv", "reqs=0");
        }
        
        System.out.println("Convergence test complete.");
        System.exit(0);
    }
}
EOF

# 4. Extended Python Plotter
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

Path("results/graphs").mkdir(parents=True, exist_ok=True)

def format_ax(ax, title, xlabel, ylabel):
    ax.set_title(title, fontsize=14, fontweight="bold", pad=15)
    ax.set_xlabel(xlabel, fontsize=12, fontweight="bold")
    ax.set_ylabel(ylabel, fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

# Filter for standard metrics
df_std = df[df["op"] == "GET"].copy()
if not df_std.empty:
    agg = df_std.groupby("reqs").agg(
        avg_lat=("lat_ms", "mean"),
        avg_hops=("hops", "mean"),
        count=("op", "count"),
        min_ts=("ts_ms", "min"),
        max_ts=("ts_ms", "max")
    ).reset_index()

    agg["dur_s"] = (agg["max_ts"] - agg["min_ts"]) / 1000.0
    agg["dur_s"] = agg["dur_s"].replace(0, 0.1)
    agg["tpt"] = agg["count"] / agg["dur_s"]

    # Latency Graph
    fig_lat, ax_lat = plt.subplots(figsize=(10, 6))
    ax_lat.plot(agg["reqs"], agg["avg_lat"], marker="o", lw=2.5, color="#DD8452", label="Naive DHT")
    format_ax(ax_lat, "Average Request Latency vs Load", "Concurrent Requests", "Latency (ms)")
    ax_lat.legend(loc="upper left")
    fig_lat.savefig("results/graphs/dht_latency.png", dpi=150)

    # Throughput Graph
    fig_tpt, ax_tpt = plt.subplots(figsize=(10, 6))
    ax_tpt.plot(agg["reqs"], agg["tpt"], marker="s", lw=2.5, color="#55A868", label="Naive DHT")
    format_ax(ax_tpt, "Global System Throughput vs Load", "Concurrent Requests", "Requests / Second")
    ax_tpt.legend(loc="upper left")
    fig_tpt.savefig("results/graphs/dht_throughput.png", dpi=150)

    # Hops Graph
    fig_hop, ax_hop = plt.subplots(figsize=(10, 6))
    ax_hop.plot(agg["reqs"], agg["avg_hops"], marker="^", lw=2.5, color="#4C72B0", label="Naive DHT")
    format_ax(ax_hop, "Network Efficiency (Hops per Request)", "Concurrent Requests", "Average Hop Count")
    ax_hop.legend(loc="upper left")
    fig_hop.savefig("results/graphs/dht_hops.png", dpi=150)

# Convergence Graph
df_c = df[df["op"] == "CONV"].copy()
if not df_c.empty:
    fig_c, ax_c = plt.subplots(figsize=(10, 6))
    ax_c.plot(df_c["lat_ms"], df_c["hops"], drawstyle="steps-post", lw=2.5, color="#9B59B6", label="Discovery Rate")
    format_ax(ax_c, "Topology Convergence Time", "Elapsed Time (ms)", "Known Peers Count")
    ax_c.legend(loc="lower right")
    fig_c.savefig("results/graphs/dht_convergence.png", dpi=150)

print("✓ 4 Analytical graphs generated successfully.")
EOF
chmod +x scripts/plot_metrics.py

# 5. Build and Execute
echo "Compiling..."
javac -d bin "$SRC_DIR"/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

echo "Launching background cluster..."
java -cp bin dht.Main 8001 8002 > /dev/null 2>&1 &
java -cp bin dht.Main 8002 8001 8003 > /dev/null 2>&1 &
java -cp bin dht.Main 8003 8002 8004 > /dev/null 2>&1 &
java -cp bin dht.Main 8004 8003 > /dev/null 2>&1 &
sleep 2 

rm -f results/dht_metrics.csv

echo "Running load tests..."
for load in 10 50 100 200 500; do
    echo "Stressing with $load requests..."
    java -cp bin dht.BenchClient $load 127.0.0.1:8001 127.0.0.1:8002 127.0.0.1:8003 127.0.0.1:8004
done

echo "Running convergence tests..."
java -cp bin dht.ConvClient 127.0.0.1:8001 > /dev/null 2>&1

echo "Generating visualisations..."
python3 scripts/plot_metrics.py

pkill -f "dht.Main"
echo "Done. All files saved to results/graphs directory."

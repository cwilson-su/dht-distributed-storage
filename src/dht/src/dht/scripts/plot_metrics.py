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

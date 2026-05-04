#!/usr/bin/env python3
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from pathlib import Path

df = pd.read_csv("results/dht_metrics.csv")
df.columns = df.columns.str.strip()
df["lat_ms"] = pd.to_numeric(df["lat_ms"], errors="coerce")
df["clients"] = df["ext"].str.extract(r'clients=(\d+)').astype(float)

agg = df.groupby("clients")["lat_ms"].mean().reset_index()

fig, ax = plt.subplots(figsize=(10, 6))
ax.plot(agg["clients"], agg["lat_ms"], marker="o", lw=2.5, color="#DD8452", label="Naive DHT")
ax.set_title("Average Request Latency vs Load", fontsize=14, fontweight="bold", pad=15)
ax.set_xlabel("Concurrent Clients", fontsize=12, fontweight="bold")
ax.set_ylabel("Latency (ms)", fontsize=12, fontweight="bold")
ax.grid(True, linestyle="--", alpha=0.6)
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.legend(loc="upper left")

Path("results/graphs").mkdir(parents=True, exist_ok=True)
fig.savefig("results/graphs/dht_latency.png", dpi=150)
print("Graph generated at results/graphs/dht_latency.png")

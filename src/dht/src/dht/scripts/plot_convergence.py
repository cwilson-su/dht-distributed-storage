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

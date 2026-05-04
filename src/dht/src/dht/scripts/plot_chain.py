#!/usr/bin/env python3
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from pathlib import Path

df = pd.read_csv("results/dht_chain_metrics.csv")
df.columns = df.columns.str.strip()
df["lat_ms"] = pd.to_numeric(df["lat_ms"], errors="coerce")
df["hops"] = pd.to_numeric(df["hops"], errors="coerce")

# Create the specific subdirectories for PNG and PDF
png_dir = Path("results/graphs/png")
pdf_dir = Path("results/graphs/pdf")
png_dir.mkdir(parents=True, exist_ok=True)
pdf_dir.mkdir(parents=True, exist_ok=True)

df_c = df[df["op"] == "CHAIN_CONV"].copy()
if not df_c.empty:
    agg = df_c.groupby("hops")["lat_ms"].mean().reset_index()
    
    fig, ax = plt.subplots(figsize=(10, 6))
    
    # Render line in Orange
    ax.plot(agg["hops"], agg["lat_ms"], marker="o", lw=2.5, markersize=8, color="orange", label="Convergence Delay")
    
    ax.set_title("Total Convergence Time vs Linear Chain Size", fontsize=14, fontweight="bold", pad=15)
    ax.set_xlabel("Number of Nodes in Chain", fontsize=12, fontweight="bold")
    ax.set_ylabel("Convergence Time (ms)", fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.legend(loc="upper left")
    
    fig.savefig(png_dir / "dht_chain_convergence.png", dpi=150, bbox_inches="tight")
    fig.savefig(pdf_dir / "dht_chain_convergence.pdf", bbox_inches="tight")
    print("✓ Chain convergence graph generated in PNG and PDF formats.")

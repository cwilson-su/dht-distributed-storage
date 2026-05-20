#!/usr/bin/env python3
"""
plot_hops_centralized.py — Standalone script to plot Hops vs Number of Nodes.
"""

import os
from pathlib import Path
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

CSV_PATH = "results/centralized_hops_metrics.csv"
OUTPUT_IMAGE = "results/graphs/compare_hops_nodes.png"

def load_and_plot():
    path = Path(CSV_PATH)
    if not path.exists() or path.stat().st_size == 0:
        print(f"[ERROR] Final CSV file not found or empty at {CSV_PATH}")
        return

    # Load data
    df = pd.read_csv(CSV_PATH)
    df.columns = df.columns.str.strip()
    
    # Parse data columns
    df["hop_count"] = pd.to_numeric(df["hop_count"], errors="coerce")
    df["extra"] = df["extra"].fillna("")
    
    # Extract the custom active_nodes attribute injected via bash
    df["active_nodes"] = df["extra"].str.extract(r'active_nodes=(\d+)').astype(float)
    
    # Filter valid rows with operations and node count
    hops_df = df[df["hop_count"].notnull() & df["active_nodes"].notnull()]
    
    if hops_df.empty:
        print("[ERROR] No valid rows with 'active_nodes' and 'hop_count' found in CSV.")
        return

    # Compute mean hop count grouped by the cluster size
    hop_agg = hops_df.groupby("active_nodes")["hop_count"].mean().reset_index()
    hop_agg = hop_agg.sort_values("active_nodes")
    
    # Create single isolated plot
    fig, ax = plt.subplots(figsize=(10, 6))
    
    # Plot curve
    ax.plot(hop_agg["active_nodes"], hop_agg["hop_count"], 
            marker="^", color="#4C72B0", lw=2.5, markersize=8, label="Centralisé")
    
    # Format axes
    ax.set_title("Network Efficiency: Hop Count vs Cluster Scale\n(Centralized Modulo Hashing Architecture)", 
                 fontsize=14, fontweight="bold", pad=15)
    ax.set_xlabel("Number of Active Nodes in Cluster (Scale)", fontsize=12, fontweight="bold")
    ax.set_ylabel("Average Hop Count per Request", fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    
    # Visual cleanup
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    # Start Y axis at 0 to show the honest horizontal line
    ax.set_ylim(bottom=0, top=max(hop_agg["hop_count"].max() + 1, 4))
    ax.legend(loc="upper left")
    
    # Ensure X ticks match exactly tested values
    ax.set_xticks(hop_agg["active_nodes"].unique())
    
    # Save chart
    os.makedirs(os.path.dirname(OUTPUT_IMAGE), exist_ok=True)
    plt.tight_layout()
    fig.savefig(OUTPUT_IMAGE, dpi=150)
    print(f"✓ Standalone hops graph successfully generated: {OUTPUT_IMAGE}")

if __name__ == "__main__":
    print("=== Centralized Hops Plotting Engine ===")
    load_and_plot()
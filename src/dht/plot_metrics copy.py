#!/usr/bin/env python3
"""
plot_metrics.py — Generates 5 Comparative Analytical Graphs for all implementations.
"""

import os
from pathlib import Path
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

CONFIGS = {
    "centralized": {"csv": "results/centralized_metrics.csv", "color": "#4C72B0", "label": "Centralized"},
    "dht":         {"csv": "results/dht_metrics.csv",         "color": "#DD8452", "label": "Naive DHT"},
    "chord":       {"csv": "results/chord_metrics.csv",       "color": "#55A868", "label": "Chord"},
}

OUTPUT_DIR = Path("results/graphs")

def load_data(csv_path: str) -> pd.DataFrame:
    path = Path(csv_path)
    if not path.exists() or path.stat().st_size == 0:
        return pd.DataFrame()

    df = pd.read_csv(csv_path)
    df.columns = df.columns.str.strip()
    df["timestamp_ms"] = pd.to_numeric(df["timestamp_ms"], errors="coerce")
    df["latency_ms"]   = pd.to_numeric(df["latency_ms"],   errors="coerce")
    df["hop_count"]    = pd.to_numeric(df["hop_count"],    errors="coerce")
    df["operation"]    = df["operation"].str.strip().str.upper()
    df["extra"]        = df["extra"].fillna("")
    
    # Extract tags
    df["keys_count"]   = df["extra"].str.extract(r'keys_count=(\d+)').astype(float)
    df["node"]         = df["extra"].str.extract(r'node=(\d+)')
    df["clients"]      = df["extra"].str.extract(r'clients=(\d+)').astype(float)
    df["active_nodes"] = df["extra"].str.extract(r'active_nodes=(\d+)').astype(float)
    
    return df.dropna(subset=["timestamp_ms"])

def format_ax(ax, title, xlabel, ylabel):
    ax.set_title(title, fontsize=13, fontweight="bold", pad=12)
    ax.set_xlabel(xlabel, fontsize=10)
    ax.set_ylabel(ylabel, fontsize=10)
    ax.yaxis.grid(True, linestyle="--", alpha=0.6)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

def plot_comparisons():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    datasets = {}
    for impl, cfg in CONFIGS.items():
        df = load_data(cfg["csv"])
        if not df.empty:
            datasets[impl] = df
            print(f"Loaded data for: {cfg['label']}")

    if not datasets:
        print("No CSV files found. Run the benchmark first.")
        return

    # Initialize Figures
    fig_lat, ax_lat = plt.subplots(figsize=(9, 6))
    fig_tpt, ax_tpt = plt.subplots(figsize=(9, 6))
    fig_hop, ax_hop = plt.subplots(figsize=(9, 6))
    fig_reb, ax_reb = plt.subplots(figsize=(9, 6))
    fig_skew, ax_skew = plt.subplots(figsize=(11, 6))

    has_reb_data = False
    skew_bar_width = 0.25
    skew_x_indices = None
    node_labels = None

    for idx, (impl, df) in enumerate(datasets.items()):
        color = CONFIGS[impl]["color"]
        label = CONFIGS[impl]["label"]

        # --- 1. Load Testing Data ---
        bench_reqs = df[df["clients"].notna() & df["operation"].isin(["PUT", "GET", "DELETE"])]
        if not bench_reqs.empty:
            scenarios = bench_reqs.groupby("clients")
            client_counts, avg_latencies, throughputs, avg_hops = [], [], [], []

            for num_clients, grp in sorted(scenarios, key=lambda x: x[0]):
                client_counts.append(int(num_clients))
                avg_latencies.append(grp["latency_ms"].mean())
                avg_hops.append(grp["hop_count"].mean())
                
                duration_sec = (grp["timestamp_ms"].max() - grp["timestamp_ms"].min()) / 1000.0
                throughputs.append(len(grp) / duration_sec if duration_sec > 0 else 0)

            ax_lat.plot(client_counts, avg_latencies, marker='o', color=color, linewidth=2, label=label)
            ax_tpt.plot(client_counts, throughputs, marker='s', color=color, linewidth=2, label=label)
            ax_hop.plot(client_counts, avg_hops, marker='^', color=color, linewidth=2, label=label)
            
            if idx == 0:
                for ax in [ax_lat, ax_tpt, ax_hop]:
                    ax.set_xticks(client_counts)
                    ax.tick_params(axis='x', rotation=45)

        # --- 2. Rebalance Cost vs Active Nodes ---
        reb_data = df[(df["operation"] == "REBALANCE") & df["active_nodes"].notna()]
        if not reb_data.empty:
            has_reb_data = True
            reb_grouped = reb_data.groupby("active_nodes")["latency_ms"].mean().reset_index().sort_values("active_nodes")
            ax_reb.plot(reb_grouped["active_nodes"], reb_grouped["latency_ms"], marker='D', color=color, linewidth=2, markersize=8, label=label)
            if idx == 0:
                ax_reb.set_xticks(reb_grouped["active_nodes"])

       # --- 3. Data Skew (Before vs After Crash) ---
        skew_before = df[df["operation"] == "DATA_SKEW_BEFORE"].copy()
        skew_after  = df[df["operation"] == "DATA_SKEW_AFTER"].copy()
        
        if not skew_after.empty and not skew_before.empty:
            # 1. Find the timestamp of the very last AFTER event (the 15-node test)
            last_after_ts = skew_after["timestamp_ms"].max()
            final_after_records = skew_after[skew_after["timestamp_ms"] > last_after_ts - 5000]
            final_after = final_after_records.sort_values("timestamp_ms").groupby("node").last().reset_index()

            # 2. Identify the dead node (the one that drops to 0 or is missing in AFTER)
            dead_node = None
            for n in final_after["node"].unique():
                if float(final_after[final_after["node"] == n]["keys_count"].iloc[-1]) == 0:
                    dead_node = n
                    break
            
            # 3. Find the EXACT moment right before the dead node crashed to avoid "Ghost Keys"
            if dead_node and not skew_before[skew_before["node"] == dead_node].empty:
                last_dead_ts = skew_before[skew_before["node"] == dead_node]["timestamp_ms"].max()
                
                # Take the cohesive snapshot of ALL nodes at that exact second (+/- 2 seconds)
                snapshot_cycle = skew_before[(skew_before["timestamp_ms"] >= last_dead_ts - 2000) & (skew_before["timestamp_ms"] <= last_dead_ts + 2000)]
                final_before = snapshot_cycle.sort_values("timestamp_ms").groupby("node").last().reset_index()
            else:
                # Fallback if no dead node found
                min_after_ts = final_after_records["timestamp_ms"].min()
                valid_before = skew_before[skew_before["timestamp_ms"] < min_after_ts]
                final_before = valid_before[valid_before["timestamp_ms"] > min_after_ts - 25000].sort_values("timestamp_ms").groupby("node").last().reset_index()

            if not final_before.empty:
                skew_merged = pd.merge(final_before, final_after, on="node", how="outer", suffixes=('_before', '_after'))
                skew_merged["keys_count_before"] = skew_merged["keys_count_before"].fillna(0)
                skew_merged["keys_count_after"] = skew_merged["keys_count_after"].fillna(0)
                
                # Calculate total keys in the system
                total_keys = int(skew_merged["keys_count_before"].sum())
                
                if skew_x_indices is None:
                    skew_x_indices = np.arange(len(skew_merged))
                    node_labels = [f"Node {int(n)}" for n in skew_merged["node"]]
                
                offset = (idx - 1) * skew_bar_width if len(datasets) == 3 else (idx - 0.5) * skew_bar_width
                
                # Plot "Before" bars (Hatched)
                ax_skew.bar(skew_x_indices - 0.15, skew_merged["keys_count_before"], width=0.3, color=color, alpha=0.5, edgecolor="white", hatch='//', label=f"{label} (Before)")
                
                # Plot "After" bars (Solid)
                ax_skew.bar(skew_x_indices + 0.15, skew_merged["keys_count_after"], width=0.3, color=color, alpha=0.9, edgecolor="white", label=f"{label} (After)")
                
                for i, row in skew_merged.iterrows():
                    val_b = row["keys_count_before"]
                    val_a = row["keys_count_after"]
                    if val_b > 0: ax_skew.text(i - 0.15, val_b, f"{int(val_b)}", ha='center', va='bottom', fontsize=8)
                    if val_a > 0: ax_skew.text(i + 0.15, val_a, f"{int(val_a)}", ha='center', va='bottom', fontsize=8, fontweight='bold')

                # Update the title to include the total keys dynamically
                ax_skew.set_title(f"Final Key Distribution (Data Skew)\nTotal Keys in System: EXACTLY {total_keys}", fontsize=13, fontweight="bold", pad=12)

    # --- Formatting & Saving ---
    format_ax(ax_lat, "Average Request Latency vs Load", "Concurrent Clients", "Latency (ms)")
    ax_lat.legend(loc="upper left")
    fig_lat.tight_layout()
    fig_lat.savefig(OUTPUT_DIR / "compare_latency.png", dpi=150)

    format_ax(ax_tpt, "Global System Throughput vs Load", "Concurrent Clients", "Requests / Second")
    ax_tpt.legend(loc="upper left")
    fig_tpt.tight_layout()
    fig_tpt.savefig(OUTPUT_DIR / "compare_throughput.png", dpi=150)

    format_ax(ax_hop, "Network Efficiency (Hops per Request)", "Concurrent Clients", "Average Hop Count")
    ax_hop.legend(loc="upper left")
    fig_hop.tight_layout()
    fig_hop.savefig(OUTPUT_DIR / "compare_hops.png", dpi=150)

    if has_reb_data:
        format_ax(ax_reb, "Rebalance Cost vs Node Count", "Number of Active Storage Nodes", "Rebalance Duration (ms)")
        ax_reb.legend(loc="upper left")
        fig_reb.tight_layout()
        fig_reb.savefig(OUTPUT_DIR / "compare_rebalance.png", dpi=150)

    format_ax(ax_skew, "Final Key Distribution (Data Skew)", "Storage Nodes", "Stored Keys Count")
    if skew_x_indices is not None:
        ax_skew.set_xticks(skew_x_indices)
        ax_skew.set_xticklabels(node_labels, rotation=30)
    ax_skew.legend(loc="upper left")
    fig_skew.tight_layout()
    fig_skew.savefig(OUTPUT_DIR / "compare_skew.png", dpi=150)

    plt.close('all')
    print("✓ All 5 comparative graphs successfully generated in: results/graphs/")

if __name__ == "__main__":
    print("=== PSAR Comparative Performance Plotter ===")
    plot_comparisons()
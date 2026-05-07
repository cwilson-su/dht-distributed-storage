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
    "chord":       {"csv": "results/chord_metrics.csv",       "color": "#2ECC71", "label": "Chord"},
}


SKEW_CONFIGS = {
    "chord":       {"csv": "results/chord_metrics_nodes15.csv",       "color": "#2ECC71", "label": "Chord"},
}

OUTPUT_DIR = Path("results/graphs")

def load_data(csv_path: str) -> pd.DataFrame:
    path = Path(csv_path)
    if not path.exists() or path.stat().st_size == 0:
        return pd.DataFrame()

    df = pd.read_csv(csv_path)
    df.columns = df.columns.str.strip()

    if "timestamp_ms" not in df.columns:
        print(f"  [WARNING] Skipping {csv_path}: missing expected columns (got: {list(df.columns)})")
        return pd.DataFrame()

    df["timestamp_ms"] = pd.to_numeric(df["timestamp_ms"], errors="coerce")
    df["latency_ms"]   = pd.to_numeric(df["latency_ms"],   errors="coerce")
    df["hop_count"]    = pd.to_numeric(df["hop_count"],    errors="coerce")
    df["operation"]    = df["operation"].str.strip().str.upper()
    df["extra"]        = df["extra"].fillna("")

    df["clients"]      = df["extra"].str.extract(r'clients=(\d+)').astype(float)
    df["active_nodes"] = df["extra"].str.extract(r'active_nodes=(\d+)').astype(float)
    df["keys_count"]   = df["extra"].str.extract(r'keys_count=(\d+)').astype(float)
    df["node"]         = df["extra"].str.extract(r'node=(\d+)')

    # For Chord: PUT_HOP rows carry the hop count; merge them with PUT rows for hop graph
    # We treat PUT_HOP as a PUT with hop_count, ignoring latency (which is 0 there)
    df.loc[df["operation"] == "PUT_HOP", "operation"] = "PUT"

    return df

def format_ax(ax, title, xlabel, ylabel):
    ax.set_title(title, fontsize=14, fontweight="bold", pad=15)
    ax.set_xlabel(xlabel, fontsize=12, fontweight="bold")
    ax.set_ylabel(ylabel, fontsize=12, fontweight="bold")
    ax.grid(True, linestyle="--", alpha=0.6)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

if __name__ == "__main__":
    print("=== PSAR Comparative Performance Plotter ===")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    datasets = {}
    for arch, cfg in CONFIGS.items():
        df = load_data(cfg["csv"])
        if not df.empty:
            datasets[arch] = (df, cfg["color"], cfg["label"])
            print(f"Loaded data for: {cfg['label']}")

    if not datasets:
        print("No data found! Run the benchmark scripts first.")
        exit(1)

    fig_lat, ax_lat = plt.subplots(figsize=(10, 6))
    fig_tpt, ax_tpt = plt.subplots(figsize=(10, 6))
    fig_hop, ax_hop = plt.subplots(figsize=(10, 6))
    fig_reb, ax_reb = plt.subplots(figsize=(10, 6))
    fig_skew, ax_skew = plt.subplots(figsize=(12, 6))

    has_reb_data = False
    skew_x_indices = None
    node_labels = []
    skew_bar_width = 0.8 / len(datasets) if len(datasets) > 0 else 0.8

    for idx, (arch, (df, color, label)) in enumerate(datasets.items()):

        # --- 1. Latency & Throughput (Stress Test) ---
        # For latency: use PUT and GET rows that have a real latency_ms > 0
        stress_df = df[df["clients"].notnull() & (df["latency_ms"] > 0)]
        if not stress_df.empty:
            agg = stress_df.groupby("clients").agg(
                avg_lat=("latency_ms", "mean"),
                count=("operation", "count"),
                min_ts=("timestamp_ms", "min"),
                max_ts=("timestamp_ms", "max")
            ).reset_index()

            agg["duration_s"] = (agg["max_ts"] - agg["min_ts"]) / 1000.0
            agg["duration_s"] = agg["duration_s"].replace(0, 0.1)
            agg["throughput"] = agg["count"] / agg["duration_s"]

            agg = agg.sort_values("clients")
            ax_lat.plot(agg["clients"], agg["avg_lat"], marker="o", lw=2.5, markersize=8, color=color, label=label)
            ax_tpt.plot(agg["clients"], agg["throughput"], marker="s", lw=2.5, markersize=8, color=color, label=label)

        # --- 2. Hops ---
        hops_df = df[df["hop_count"].notnull() & (df["hop_count"] > 0)]
        if not hops_df.empty:
            if "clients" in hops_df.columns and not hops_df["clients"].dropna().empty:
                hop_agg = hops_df.groupby("clients")["hop_count"].mean().reset_index()
                ax_hop.plot(hop_agg["clients"], hop_agg["hop_count"], marker="^", lw=2.5, markersize=8, color=color, label=label)
            else:
                avg_h = hops_df["hop_count"].mean()
                ax_hop.axhline(y=avg_h, color=color, linestyle="--", lw=2.5, label=f"{label} (Avg: {avg_h:.1f})")

        # --- 3. Rebalance Cost ---
        reb_data = df[(df["operation"] == "REBALANCE") & df["active_nodes"].notnull()]
        if not reb_data.empty:
            has_reb_data = True
            reb_agg = reb_data.groupby("active_nodes")["latency_ms"].mean().reset_index().sort_values("active_nodes")
            ax_reb.plot(reb_agg["active_nodes"], reb_agg["latency_ms"], marker="D", lw=2.5, markersize=8, color=color, label=label)

        # --- 4. Data Skew (Before vs After Crash) ---
        skew_before = df[df["operation"] == "DATA_SKEW_BEFORE"].copy()
        skew_after  = df[df["operation"] == "DATA_SKEW_AFTER"].copy()

        if not skew_after.empty and not skew_before.empty:
            final_after = skew_after.sort_values("timestamp_ms").groupby("node").last().reset_index()

            first_after_ts = skew_after["timestamp_ms"].min()
            valid_before = skew_before[skew_before["timestamp_ms"] < first_after_ts]

            if not valid_before.empty:
                final_before = valid_before.sort_values("timestamp_ms").groupby("node").last().reset_index()

                skew_merged = pd.merge(final_before, final_after, on="node", how="outer", suffixes=('_before', '_after'))
                skew_merged["keys_count_before"] = skew_merged["keys_count_before"].fillna(0)
                skew_merged["keys_count_after"]  = skew_merged["keys_count_after"].fillna(0)

                total_before = int(skew_merged["keys_count_before"].sum())
                total_after  = int(skew_merged["keys_count_after"].sum())

                if skew_x_indices is None:
                    skew_x_indices = np.arange(len(skew_merged))
                    node_labels = [f"Node {int(n)}" for n in skew_merged["node"]]

                offset = (idx - 1) * skew_bar_width if len(datasets) == 3 else (idx - 0.5) * skew_bar_width

                ax_skew.bar(skew_x_indices - 0.15 + offset, skew_merged["keys_count_before"],
                            width=0.3, color=color, alpha=0.5, edgecolor="white", hatch='//',
                            label=f"{label} (Before — {total_before} keys)")
                ax_skew.bar(skew_x_indices + 0.15 + offset, skew_merged["keys_count_after"],
                            width=0.3, color=color, alpha=0.9, edgecolor="white",
                            label=f"{label} (After — {total_after} keys)")

                for i, row in skew_merged.iterrows():
                    val_b = row["keys_count_before"]
                    val_a = row["keys_count_after"]
                    if val_b > 0:
                        ax_skew.text(i - 0.15 + offset, val_b, f"{int(val_b)}",
                                     ha='center', va='bottom', fontsize=8)
                    if val_a > 0:
                        ax_skew.text(i + 0.15 + offset, val_a, f"{int(val_a)}",
                                     ha='center', va='bottom', fontsize=8, fontweight='bold')

                ax_skew.set_title(
                    f"Key Distribution Before vs After Node Crash\n"
                    f"Before: {total_before} keys — After: {total_after} keys",
                    fontsize=13, fontweight="bold", pad=12)

# --- Formatting & Saving ---
    format_ax(ax_lat, "Average Request Latency vs Load", "Concurrent Clients", "Latency (ms)")
    ax_lat.legend(loc="upper left")
    fig_lat.tight_layout()
    fig_lat.savefig(OUTPUT_DIR / "compare_latency_chord.png", dpi=150)

    format_ax(ax_tpt, "Global System Throughput vs Load", "Concurrent Clients", "Requests / Second")
    ax_tpt.legend(loc="upper left")
    fig_tpt.tight_layout()
    fig_tpt.savefig(OUTPUT_DIR / "compare_throughput_chord.png", dpi=150)

    format_ax(ax_hop, "Network Efficiency (Hops per Request)", "Concurrent Clients", "Average Hop Count")
    ax_hop.legend(loc="upper left")
    fig_hop.tight_layout()
    fig_hop.savefig(OUTPUT_DIR / "compare_hops_chord.png", dpi=150)

    if has_reb_data:
        format_ax(ax_reb, "Rebalance Cost vs Node Count", "Number of Active Storage Nodes", "Rebalance Duration (ms)")
        ax_reb.legend(loc="upper left")
        fig_reb.tight_layout()
        fig_reb.savefig(OUTPUT_DIR / "compare_rebalance_chord.png", dpi=150)

    # --- Data Skew graph based on 15-node run ---
    fig_skew2, ax_skew2 = plt.subplots(figsize=(14, 6))
    skew_x_indices2 = None
    node_labels2 = []

    for idx2, (arch, cfg) in enumerate(SKEW_CONFIGS.items()):
        df_skew = load_data(cfg["csv"])
        if df_skew.empty:
            continue

        color = cfg["color"]
        label = cfg["label"]

        skew_before = df_skew[df_skew["operation"] == "DATA_SKEW_BEFORE"].copy()
        skew_after  = df_skew[df_skew["operation"] == "DATA_SKEW_AFTER"].copy()

        if skew_after.empty or skew_before.empty:
            continue

        final_after = skew_after.sort_values("timestamp_ms").groupby("node").last().reset_index()
        first_after_ts = skew_after["timestamp_ms"].min()
        valid_before = skew_before[skew_before["timestamp_ms"] < first_after_ts]

        if valid_before.empty:
            continue

        final_before = valid_before.sort_values("timestamp_ms").groupby("node").last().reset_index()
        skew_merged = pd.merge(final_before, final_after, on="node", how="outer", suffixes=('_before', '_after'))
        skew_merged["keys_count_before"] = skew_merged["keys_count_before"].fillna(0)
        skew_merged["keys_count_after"]  = skew_merged["keys_count_after"].fillna(0)
        skew_merged = skew_merged.sort_values("node")

        total_before = int(skew_merged["keys_count_before"].sum())
        total_after  = int(skew_merged["keys_count_after"].sum())

        if skew_x_indices2 is None:
            skew_x_indices2 = np.arange(len(skew_merged))
            node_labels2 = [f"Node {n}" for n in skew_merged["node"]]

        bar_width = 0.35
        offset = (idx2 - (len(SKEW_CONFIGS) - 1) / 2) * (bar_width + 0.05)

        ax_skew2.bar(skew_x_indices2 - bar_width / 2 + offset, skew_merged["keys_count_before"],
                     width=bar_width, color=color, alpha=0.5, edgecolor="white", hatch='//',
                     label=f"{label} Before crash — {total_before} keys")
        ax_skew2.bar(skew_x_indices2 + bar_width / 2 + offset, skew_merged["keys_count_after"],
                     width=bar_width, color=color, alpha=0.9, edgecolor="white",
                     label=f"{label} After rebalance — {total_after} keys")

        for i, row in skew_merged.iterrows():
            val_b = row["keys_count_before"]
            val_a = row["keys_count_after"]
            if val_b > 0:
                ax_skew2.text(i - bar_width / 2 + offset, val_b, f"{int(val_b)}",
                              ha='center', va='bottom', fontsize=7)
            if val_a > 0:
                ax_skew2.text(i + bar_width / 2 + offset, val_a, f"{int(val_a)}",
                              ha='center', va='bottom', fontsize=7, fontweight='bold')

    format_ax(ax_skew2,
              "Key Distribution Before vs After Node Crash (15-node cluster)",
              "Storage Nodes", "Stored Keys Count")
    if skew_x_indices2 is not None:
        ax_skew2.set_xticks(skew_x_indices2)
        ax_skew2.set_xticklabels(node_labels2, rotation=45, ha='right')
    ax_skew2.legend(loc="upper right")
    fig_skew2.tight_layout()
    fig_skew2.savefig(OUTPUT_DIR / "compare_skew_chord.png", dpi=150)

    print("✓ All comparative graphs successfully generated in: results/graphs/")
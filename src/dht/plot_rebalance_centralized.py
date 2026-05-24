#!/usr/bin/env python3
"""
plot_rebalance.py — Génère uniquement le graphe Rebalance Cost vs Node Count
Centralisé vs Chord, même échelle, mêmes unités.
"""

import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from pathlib import Path

CONFIGS = {
    "centralized": {
        "csv":   "results/centralized_metrics_rebalance.csv",
        "color": "#4C72B0",
        "label": "Centralized (V1)"
    },
    "chord": {
        "csv":   "results/chord_metrics.csv",
        "color": "#55A868",
        "label": "Chord (V3)"
    },
}

OUTPUT_DIR = Path("results/graphs")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

fig, ax = plt.subplots(figsize=(10, 6))

for arch, cfg in CONFIGS.items():
    path = Path(cfg["csv"])
    if not path.exists() or path.stat().st_size == 0:
        print(f"  [SKIP] {cfg['csv']} not found or empty")
        continue

    df = pd.read_csv(cfg["csv"])
    df.columns = df.columns.str.strip()

    if "operation" not in df.columns:
        print(f"  [SKIP] {cfg['csv']} missing 'operation' column")
        continue

    df["operation"]  = df["operation"].str.strip().str.upper()
    df["latency_ms"] = pd.to_numeric(df["latency_ms"], errors="coerce")
    df["extra"]      = df["extra"].fillna("")
    df["active_nodes"] = df["extra"].str.extract(r'active_nodes=(\d+)').astype(float)

    reb = df[(df["operation"] == "REBALANCE") & df["active_nodes"].notnull()]
    if reb.empty:
        print(f"  [SKIP] No REBALANCE rows found in {cfg['csv']}")
        continue

    agg = reb.groupby("active_nodes")["latency_ms"].mean().reset_index().sort_values("active_nodes")

    ax.plot(agg["active_nodes"], agg["latency_ms"],
            marker="D", lw=2.5, markersize=8,
            color=cfg["color"], label=cfg["label"])

    for _, row in agg.iterrows():
        ax.annotate(f"{row['latency_ms']:.0f}ms",
                    xy=(row["active_nodes"], row["latency_ms"]),
                    xytext=(0, 8), textcoords="offset points",
                    ha='center', fontsize=8, color=cfg["color"])

ax.set_title("Rebalance Cost vs Node Count", fontsize=14, fontweight="bold", pad=15)
ax.set_xlabel("Number of Active Storage Nodes", fontsize=12, fontweight="bold")
ax.set_ylabel("Rebalance Duration (ms)", fontsize=12, fontweight="bold")
ax.grid(True, linestyle="--", alpha=0.6)
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.legend(loc="upper right", fontsize=11)

fig.tight_layout()
out = OUTPUT_DIR / "compare_rebalance.png"
fig.savefig(out, dpi=150)
print(f"✓ Graphe sauvegardé : {out}")
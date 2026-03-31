#!/usr/bin/env python3
"""
plot_metrics.py — Génère UN graphe par implémentation PSAR.

Chaque graphe contient 3 sous-graphes (subplots) :
  1. Latence GET (ms) au fil du temps  ← timeline de ta session
  2. Nombre de messages (hopCount) par requête GET/PUT
  3. Durée des rebalances / JOIN / LEAVE (ms)

Usage :
  python3 plot_metrics.py --impl centralized --csv results/centralized_metrics.csv
  python3 plot_metrics.py --impl dht         --csv results/dht_metrics.csv
  python3 plot_metrics.py --impl chord       --csv results/chord_metrics.csv

  # ou lancer les 3 d'un coup :
  python3 plot_metrics.py --all

Dépendances : pip install pandas matplotlib
"""

import argparse
import os
import sys
from pathlib import Path

import pandas as pd
import matplotlib
matplotlib.use("Agg")          # pas de display X11 nécessaire
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import numpy as np

# ─── config ────────────────────────────────────────────────────────────────────
CONFIGS = {
    "centralized": {
        "csv":   "results/centralized_metrics.csv",
        "color": "#4C72B0",
        "label": "Centralisé (Hachage Modulo)",
    },
    "dht": {
        "csv":   "results/dht_metrics.csv",
        "color": "#DD8452",
        "label": "DHT Naïve",
    },
    "chord": {
        "csv":   "results/chord_metrics.csv",
        "color": "#55A868",
        "label": "Chord",
    },
}

OUTPUT_DIR = Path("results/graphs")


# ─── chargement CSV ────────────────────────────────────────────────────────────
def load(csv_path: str) -> pd.DataFrame:
    path = Path(csv_path)
    if not path.exists() or path.stat().st_size == 0:
        print(f"  [WARN] Fichier vide ou absent : {csv_path}")
        return pd.DataFrame()

    df = pd.read_csv(csv_path)
    df.columns = df.columns.str.strip()

    required = {"timestamp_ms", "operation", "latency_ms", "hop_count"}
    if not required.issubset(df.columns):
        print(f"  [WARN] Colonnes manquantes dans {csv_path}: {required - set(df.columns)}")
        return pd.DataFrame()

    df["timestamp_ms"] = pd.to_numeric(df["timestamp_ms"], errors="coerce")
    df["latency_ms"]   = pd.to_numeric(df["latency_ms"],   errors="coerce")
    df["hop_count"]    = pd.to_numeric(df["hop_count"],     errors="coerce")
    df["operation"]    = df["operation"].str.strip().str.upper()
    df["datetime"]     = pd.to_datetime(df["timestamp_ms"], unit="ms")

    return df.dropna(subset=["timestamp_ms"])


# ─── graphe principal ─────────────────────────────────────────────────────────
def plot_impl(impl: str, cfg: dict):
    df = load(cfg["csv"])
    color = cfg["color"]
    label = cfg["label"]

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = OUTPUT_DIR / f"graph_{impl}.png"

    # ── cas : aucune donnée ──────────────────────────────────────────────────
    if df.empty:
        fig, ax = plt.subplots(figsize=(10, 6))
        ax.text(0.5, 0.5, f"Aucune donnée disponible\n({cfg['csv']})",
                ha="center", va="center", fontsize=14, color="gray",
                transform=ax.transAxes)
        ax.set_title(f"PSAR — {label}", fontsize=16, fontweight="bold")
        fig.savefig(out_path, dpi=130, bbox_inches="tight")
        plt.close(fig)
        print(f"  [EMPTY] {out_path}")
        return

    gets     = df[df["operation"] == "GET"]
    puts     = df[df["operation"] == "PUT"]
    events   = df[df["operation"].isin(["REBALANCE", "JOIN", "LEAVE",
                                         "NODE_TIMEOUT", "TRANSFER_KEYS"])]

    fig, axes = plt.subplots(1, 3, figsize=(18, 5))
    fig.suptitle(f"PSAR — Métriques de performance : {label}",
                 fontsize=15, fontweight="bold", y=1.02)

    # ── subplot 1 : latence GET au fil du temps ─────────────────────────────
    ax1 = axes[0]
    if not gets.empty and gets["latency_ms"].gt(0).any():
        valid_gets = gets[gets["latency_ms"] > 0]
        ax1.plot(valid_gets["datetime"], valid_gets["latency_ms"],
                 "o-", color=color, linewidth=1.2, markersize=4, alpha=0.8,
                 label="GET latency")
        # Ligne médiane
        median = valid_gets["latency_ms"].median()
        ax1.axhline(median, color=color, linewidth=1, linestyle="--", alpha=0.5,
                    label=f"médiane={median:.1f}ms")
        ax1.legend(fontsize=8)
    else:
        ax1.text(0.5, 0.5, "Pas de données GET\navec latence > 0",
                 ha="center", va="center", color="gray", transform=ax1.transAxes)

    ax1.set_title("Latence GET au fil du temps", fontsize=11)
    ax1.set_xlabel("Heure")
    ax1.set_ylabel("Latence (ms)")
    ax1.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M:%S"))
    ax1.tick_params(axis="x", rotation=30)
    ax1.yaxis.grid(True, linestyle="--", alpha=0.5)

    # Annoter les événements JOIN/LEAVE/REBALANCE sur la timeline
    for _, row in events.iterrows():
        if row["operation"] in ("JOIN", "LEAVE", "REBALANCE", "NODE_TIMEOUT"):
            ev_color = {"JOIN": "green", "LEAVE": "red",
                        "REBALANCE": "orange", "NODE_TIMEOUT": "darkred"}.get(
                            row["operation"], "gray")
            ax1.axvline(row["datetime"], color=ev_color, linewidth=1,
                        linestyle=":", alpha=0.7)

    # Légende des événements
    from matplotlib.lines import Line2D
    ev_legend = [
        Line2D([0], [0], color="green",   linestyle=":", label="JOIN"),
        Line2D([0], [0], color="red",     linestyle=":", label="LEAVE"),
        Line2D([0], [0], color="orange",  linestyle=":", label="REBALANCE"),
    ]
    if any(events["operation"] == "NODE_TIMEOUT"):
        ev_legend.append(Line2D([0], [0], color="darkred", linestyle=":", label="TIMEOUT"))
    if ev_legend:
        ax1.legend(handles=ev_legend, fontsize=7, loc="upper right")

    # ── subplot 2 : hopCount par requête ──────────────────────────────────────
    ax2 = axes[1]
    plotted_hop = False
    if not gets.empty and gets["hop_count"].gt(0).any():
        ax2.scatter(gets["datetime"], gets["hop_count"],
                    color=color, alpha=0.7, s=25, label="GET hops")
        plotted_hop = True
    if not puts.empty and puts["hop_count"].gt(0).any():
        ax2.scatter(puts["datetime"], puts["hop_count"],
                    color=color, alpha=0.4, s=25, marker="^", label="PUT hops")
        plotted_hop = True

    if plotted_hop:
        all_hops = pd.concat([
            gets[gets["hop_count"] > 0]["hop_count"],
            puts[puts["hop_count"] > 0]["hop_count"]
        ])
        avg_hop = all_hops.mean()
        ax2.axhline(avg_hop, color=color, linestyle="--", linewidth=1, alpha=0.6,
                    label=f"moy={avg_hop:.1f}")
        ax2.legend(fontsize=8)
    else:
        ax2.text(0.5, 0.5, "Pas de données hop_count > 0",
                 ha="center", va="center", color="gray", transform=ax2.transAxes)

    ax2.set_title("Nombre de messages par requête (hop count)", fontsize=11)
    ax2.set_xlabel("Heure")
    ax2.set_ylabel("Messages réseau")
    ax2.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M:%S"))
    ax2.tick_params(axis="x", rotation=30)
    ax2.yaxis.grid(True, linestyle="--", alpha=0.5)

    # ── subplot 3 : durée des événements JOIN / LEAVE / REBALANCE ─────────────
    ax3 = axes[2]
    ev_with_duration = events[events["latency_ms"] > 0]

    if not ev_with_duration.empty:
        for op, grp in ev_with_duration.groupby("operation"):
            ev_color = {"JOIN": "green", "LEAVE": "red", "REBALANCE": "orange",
                        "NODE_TIMEOUT": "darkred", "TRANSFER_KEYS": "purple"}.get(op, "gray")
            ax3.bar(grp["datetime"], grp["latency_ms"],
                    width=pd.Timedelta(seconds=1), color=ev_color,
                    alpha=0.75, label=op)
        ax3.legend(fontsize=8)
    else:
        ax3.text(0.5, 0.5, "Pas d'événements JOIN/LEAVE/REBALANCE\navec durée mesurée",
                 ha="center", va="center", color="gray", transform=ax3.transAxes)

    ax3.set_title("Durée des événements réseau (ms)", fontsize=11)
    ax3.set_xlabel("Heure")
    ax3.set_ylabel("Durée (ms)")
    ax3.xaxis.set_major_formatter(mdates.DateFormatter("%H:%M:%S"))
    ax3.tick_params(axis="x", rotation=30)
    ax3.yaxis.grid(True, linestyle="--", alpha=0.5)

    # ── résumé textuel en bas de page ────────────────────────────────────────
    n_get  = len(gets)
    n_put  = len(puts)
    n_ev   = len(events)
    valid_gets_lat = gets[gets["latency_ms"] > 0]["latency_ms"] if not gets.empty else pd.Series([], dtype=float)
    avg_lat = valid_gets_lat.mean() if not valid_gets_lat.empty else 0
    avg_hop = df[df["hop_count"] > 0]["hop_count"].mean() if not df[df["hop_count"] > 0].empty else 0

    summary = (f"Total requêtes : GET={n_get}  PUT={n_put}  Événements={n_ev}   |   "
               f"Latence GET moy={avg_lat:.1f}ms   |   Hops moy={avg_hop:.1f}")
    fig.text(0.5, -0.01, summary, ha="center", fontsize=9, color="dimgray")

    fig.tight_layout()
    fig.savefig(out_path, dpi=130, bbox_inches="tight")
    plt.close(fig)
    print(f"  ✓ Graphe généré : {out_path}")


# ─── main ─────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="PSAR — Génération des graphes de performance")
    parser.add_argument("--impl", choices=["centralized", "dht", "chord"],
                        help="Implémentation à tracer")
    parser.add_argument("--csv",  help="Chemin du CSV (si --impl est fourni)")
    parser.add_argument("--all",  action="store_true",
                        help="Générer les graphes pour les 3 implémentations")
    args = parser.parse_args()

    if not args.impl and not args.all:
        parser.print_help()
        sys.exit(1)

    print("=== PSAR Performance Plotter ===")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    if args.all:
        for impl, cfg in CONFIGS.items():
            print(f"\n[{impl.upper()}]")
            plot_impl(impl, cfg)
    else:
        cfg = CONFIGS[args.impl].copy()
        if args.csv:
            cfg["csv"] = args.csv
        print(f"\n[{args.impl.upper()}]")
        plot_impl(args.impl, cfg)

    print(f"\n✓ Graphes disponibles dans : {OUTPUT_DIR.resolve()}")


if __name__ == "__main__":
    main()
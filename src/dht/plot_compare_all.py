#!/usr/bin/env python3
"""
plot_compare_all.py
Génère 3 graphes comparatifs pour les 3 implémentations :
  1. Latence moyenne vs charge
  2. Débit global vs charge
  3. Nombre moyen de sauts vs charge
"""

from pathlib import Path
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

# ── Chemins relatifs au dossier où est lancé le script ─────────────────────
IMPLS = {
    "centralized": {
        "csv":    "results/centralized_metrics.csv",
        "color":  "#4C72B0",
        "label":  "Centralisé",
        "marker": "o",
    },
    "dht": {
        "csv":    "src/dht/results/dht_metrics.csv",
        "color":  "#DD8452",
        "label":  "DHT Naïve",
        "marker": "s",
    },
    "chord": {
        "csv":    "results/chord_metrics.csv",
        "color":  "#2ECC71",
        "label":  "Chord",
        "marker": "^",
    },
}

OUTPUT_DIR = Path("results/graphs")


def load(path: str, impl: str) -> pd.DataFrame:
    p = Path(path)
    if not p.exists() or p.stat().st_size == 0:
        print(f"  [MANQUANT] {path}")
        return pd.DataFrame()

    df = pd.read_csv(path)
    df.columns = df.columns.str.strip()

    # Normalise les noms de colonnes de la DHT naïve
    rename = {
        "ts_ms":  "timestamp_ms",
        "op":     "operation",
        "lat_ms": "latency_ms",
        "hops":   "hop_count",
        "k":      "key",
        "ext":    "extra",
    }
    df.rename(columns=rename, inplace=True)

    required = {"timestamp_ms", "operation", "latency_ms", "hop_count", "extra"}
    if not required.issubset(df.columns):
        print(f"  [ERREUR colonnes] {path} : {list(df.columns)}")
        return pd.DataFrame()

    df["timestamp_ms"] = pd.to_numeric(df["timestamp_ms"], errors="coerce")
    df["latency_ms"]   = pd.to_numeric(df["latency_ms"],   errors="coerce")
    df["hop_count"]    = pd.to_numeric(df["hop_count"],    errors="coerce")
    df["operation"]    = df["operation"].str.strip().str.upper()
    df["extra"]        = df["extra"].fillna("")

    # Extrait le niveau de charge :
    # - centralisé et chord : clients=N
    # - DHT naïve           : reqs=N
    df["clients"] = (
        df["extra"].str.extract(r'clients=(\d+)').astype(float)
        .fillna(df["extra"].str.extract(r'reqs=(\d+)').astype(float))
    )

    # Chord : PUT_HOP porte les hops côté nœud → fusionné avec PUT
    df.loc[df["operation"] == "PUT_HOP", "operation"] = "PUT"

    df["impl"] = impl
    return df


def format_ax(ax, title, xlabel, ylabel):
    ax.set_title(title, fontsize=14, fontweight="bold", pad=15)
    ax.set_xlabel(xlabel, fontsize=12)
    ax.set_ylabel(ylabel, fontsize=12)
    ax.grid(True, linestyle="--", alpha=0.5)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)


if __name__ == "__main__":
    print("=== Graphes comparatifs — 3 implémentations ===")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    fig_lat, ax_lat = plt.subplots(figsize=(10, 6))
    fig_tpt, ax_tpt = plt.subplots(figsize=(10, 6))
    fig_hop, ax_hop = plt.subplots(figsize=(10, 6))

    for impl, cfg in IMPLS.items():
        df = load(cfg["csv"], impl)
        if df.empty:
            continue

        color  = cfg["color"]
        label  = cfg["label"]
        marker = cfg["marker"]

        print(f"Chargé : {label}  —  opérations : {df['operation'].unique()}")

        # ── Latence & Débit ────────────────────────────────────────────────
        lat_df = df[
            df["operation"].isin(["PUT", "GET"]) &
            df["clients"].notnull() &
            (df["latency_ms"] > 0)
        ]

        if not lat_df.empty:
            agg = (lat_df.groupby("clients")
                         .agg(avg_lat=("latency_ms", "mean"),
                              count=("operation", "count"),
                              min_ts=("timestamp_ms", "min"),
                              max_ts=("timestamp_ms", "max"))
                         .reset_index()
                         .sort_values("clients"))

            agg["duration_s"] = ((agg["max_ts"] - agg["min_ts"]) / 1000.0).replace(0, 0.1)
            agg["throughput"] = agg["count"] / agg["duration_s"]

            ax_lat.plot(agg["clients"], agg["avg_lat"],
                        marker=marker, lw=2.5, markersize=8,
                        color=color, label=label)
            ax_tpt.plot(agg["clients"], agg["throughput"],
                        marker=marker, lw=2.5, markersize=8,
                        color=color, label=label)
        else:
            print(f"  [AVERTISSEMENT] Pas de données latence/débit pour {label}")

        # ── Hops ──────────────────────────────────────────────────────────
        hop_df = df[
            df["hop_count"].notnull() &
            (df["hop_count"] > 0) &
            df["clients"].notnull()
        ]

        if not hop_df.empty:
            hop_agg = (hop_df.groupby("clients")["hop_count"]
                             .mean()
                             .reset_index()
                             .sort_values("clients"))
            ax_hop.plot(hop_agg["clients"], hop_agg["hop_count"],
                        marker=marker, lw=2.5, markersize=8,
                        color=color, label=label)
        else:
            # Pas de tag clients= → ligne horizontale (valeur moyenne globale)
            hop_all = df[df["hop_count"].notnull() & (df["hop_count"] > 0)]
            if not hop_all.empty:
                avg_h = hop_all["hop_count"].mean()
                ax_hop.axhline(y=avg_h, color=color, linestyle="--", lw=2.5,
                               label=f"{label} (moy. {avg_h:.1f} hops)")
                print(f"  [{label}] Hops : ligne horizontale à {avg_h:.2f}")
            else:
                print(f"  [AVERTISSEMENT] Pas de données hops pour {label}")

    # ── Sauvegarde ─────────────────────────────────────────────────────────
    format_ax(ax_lat,
              "Latence moyenne des requêtes en fonction de la charge",
              "Clients concurrents", "Latence (ms)")
    ax_lat.legend(loc="upper left", fontsize=11)
    fig_lat.tight_layout()
    fig_lat.savefig(OUTPUT_DIR / "all_latency.png", dpi=150)
    print("✓ all_latency.png")

    format_ax(ax_tpt,
              "Débit global du système en fonction de la charge",
              "Clients concurrents", "Requêtes / seconde")
    ax_tpt.legend(loc="upper left", fontsize=11)
    fig_tpt.tight_layout()
    fig_tpt.savefig(OUTPUT_DIR / "all_throughput.png", dpi=150)
    print("✓ all_throughput.png")

    format_ax(ax_hop,
              "Efficacité réseau : nombre moyen de sauts par requête",
              "Clients concurrents", "Nombre moyen de sauts")
    ax_hop.legend(loc="upper right", fontsize=11)
    fig_hop.tight_layout()
    fig_hop.savefig(OUTPUT_DIR / "all_hops.png", dpi=150)
    print("✓ all_hops.png")

    print("\n✓ Les 3 graphes comparatifs sont dans : results/graphs/")
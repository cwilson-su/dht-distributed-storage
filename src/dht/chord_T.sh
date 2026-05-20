# Fusion manuelle
MERGED="results/chord_metrics_phase2.csv"
head -1 results/chord_metrics_nodes3.csv > "$MERGED"
for NUM_NODES in 3 4 5 6 7 8 10 12 15 20 25 30 40 50; do
    FILE="results/chord_metrics_nodes${NUM_NODES}.csv"
    [ -f "$FILE" ] && tail -n +2 "$FILE" >> "$MERGED"
done

FINAL="results/chord_metrics.csv"
cat results/chord_metrics_phase1.csv > "$FINAL"
tail -n +2 "$MERGED" >> "$FINAL"

# Graphes
python3 plot_metrics_chord.py
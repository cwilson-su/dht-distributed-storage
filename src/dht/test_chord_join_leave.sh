#!/bin/bash

# =============================================================
#  test_chord_dynamic.sh
#  Test du systeme dynamique Chord : JOIN et LEAVE uniquement.
#  On verifie que l'anneau reste coherent (successeurs/predecesseurs)
#  apres chaque evenement. Pas de test de donnees.
#  Compatible WSL + Windows Terminal.
# =============================================================

# =============================================================
# FONCTION WSL WINDOWS TERMINAL
# =============================================================
run_term() {
    local title="$1"
    local cmd="$2"
    wt.exe new-tab --title "$title" bash -c "$cmd; exec bash"
}

# =============================================================
# VARIABLES
# =============================================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$SCRIPT_DIR/bin"
LOG_DIR="$SCRIPT_DIR/logs"

STABILIZE_WAIT=20   # secondes max pour qu'un JOIN se stabilise
LEAVE_WAIT=20       # secondes max apres un LEAVE propre (SIGTERM)
KILL_WAIT=25        # secondes max apres un crash brutal (SIGKILL)
POLL_INTERVAL=2     # intervalle de polling pour wait_converge

PASS=0
FAIL=0

PID_8001=""
PID_8002=""
PID_8003=""
PID_8004=""
PID_8005=""

# =============================================================
# FONCTIONS UTILITAIRES
# =============================================================

ok()  { echo "  [PASS] $*"; ((PASS++)); }
fail(){ echo "  [FAIL] $*"; ((FAIL++)); }

# Demarre un noeud en arriere-plan, log dans logs/node_PORT.log
start_node() {
    local port=$1
    local bootstrap=${2:-""}
    local logfile="$LOG_DIR/node_${port}.log"
    > "$logfile"
    if [ -n "$bootstrap" ]; then
        java -cp "$BIN_DIR" chord.Main "$port" "$bootstrap" >> "$logfile" 2>&1 &
    else
        java -cp "$BIN_DIR" chord.Main "$port" >> "$logfile" 2>&1 &
    fi
    eval "PID_${port}=$!"
    echo "  Noeud $port demarre (PID=$!) --> $logfile"
}

# Verifie si un noeud repond sur son port TCP
node_alive() {
    local port=$1
    nc -z -w2 127.0.0.1 "$port" 2>/dev/null
    return $?
}

# Attend que l'anneau soit stable :
#   - aucun noeud ne logue "ne repond pas" dans ses dernieres lignes
#   - chaque noeud actif a un successeur defini dans ses logs
#   - les successeurs ne sont pas tous identiques (anneau forme)
# Usage : wait_converge <max_wait> <port1> [port2] ...
wait_converge() {
    local max_wait=$1
    shift
    local ports=("$@")
    local elapsed=0
    local stable_ticks=0
    local needed_ticks=3   # 3 cycles consecutifs stables = converge

    echo "  Attente convergence (max ${max_wait}s)..."
    while [ "$elapsed" -lt "$max_wait" ]; do
        sleep "$POLL_INTERVAL"
        elapsed=$((elapsed + POLL_INTERVAL))

        local unstable=0

        # Critere 1 : aucun noeud ne cherche un backup en ce moment
        for port in "${ports[@]}"; do
            local logfile="$LOG_DIR/node_${port}.log"
            [ -f "$logfile" ] || continue
            if tail -20 "$logfile" 2>/dev/null | grep -q "ne repond pas\|recherche d'un backup"; then
                unstable=1
                break
            fi
        done

        # Critere 2 : chaque noeud a un successeur defini
        if [ "$unstable" -eq 0 ]; then
            for port in "${ports[@]}"; do
                local logfile="$LOG_DIR/node_${port}.log"
                [ -f "$logfile" ] || continue
                if ! grep -q "successeur" "$logfile" 2>/dev/null; then
                    unstable=1
                    break
                fi
            done
        fi

        # Critere 3 : avec plus d'un noeud, les successeurs ne sont pas tous identiques
        # (evite de valider un etat en etoile non converge)
        if [ "$unstable" -eq 0 ] && [ "${#ports[@]}" -gt 2 ]; then
            local succs=""
            local all_same=1
            local first_succ=""
            for port in "${ports[@]}"; do
                local logfile="$LOG_DIR/node_${port}.log"
                [ -f "$logfile" ] || continue
                local s
                s=$(tail -50 "$logfile" | grep -o "successeur[^,)]*= [^ ]*" | tail -1 | awk '{print $NF}')
                if [ -z "$first_succ" ]; then
                    first_succ="$s"
                elif [ "$s" != "$first_succ" ]; then
                    all_same=0
                fi
            done
            if [ "$all_same" -eq 1 ] && [ -n "$first_succ" ]; then
                unstable=1  # tous les noeuds ont le meme successeur = pas encore converge
            fi
        fi

        if [ "$unstable" -eq 0 ]; then
            stable_ticks=$((stable_ticks + 1))
            if [ "$stable_ticks" -ge "$needed_ticks" ]; then
                echo "  Anneau stable apres ${elapsed}s."
                return 0
            fi
        else
            stable_ticks=0
        fi
    done

    echo "  Timeout atteint (${max_wait}s)."
    return 0
}

# Arret propre : declenche le shutdown hook --> LEAVE_NOTIFY
stop_graceful() {
    local port=$1
    local varname="PID_${port}"
    local pid="${!varname}"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        echo "  SIGTERM --> noeud $port (PID=$pid)"
        kill -SIGTERM "$pid"
    else
        echo "  Noeud $port : PID introuvable ou deja arrete"
    fi
}

# Arret brutal : aucun shutdown hook, aucun LEAVE_NOTIFY
stop_brutal() {
    local port=$1
    local varname="PID_${port}"
    local pid="${!varname}"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        echo "  SIGKILL --> noeud $port (PID=$pid) [crash simule]"
        kill -SIGKILL "$pid"
    else
        echo "  Noeud $port : PID introuvable ou deja arrete"
    fi
}

# Verifie dans le log d'un noeud que son successeur courant est bien
# le noeud attendu (en cherchant la derniere ligne de stabilisation).
# Usage : check_successor <port> <expected_successor_port>
check_successor() {
    local port=$1
    local expected_port=$2
    local logfile="$LOG_DIR/node_${port}.log"

    # On cherche la derniere mention du successeur dans les logs
    # (mise a jour par stabilize() ou LEAVE_NOTIFY)
    if grep -q "successeur.*:${expected_port}\|nouveau successeur.*:${expected_port}\|LEAVE_NOTIFY.*nouveau successeur.*:${expected_port}" "$logfile" 2>/dev/null; then
        ok "Noeud $port : successeur = $expected_port"
        return 0
    fi
    fail "Noeud $port : successeur $expected_port non trouve dans les logs"
    return 1
}

# Verifie dans le log d'un noeud que son predecesseur courant est bien
# le noeud attendu.
# Usage : check_predecessor <port> <expected_predecessor_port>
check_predecessor() {
    local port=$1
    local expected_port=$2
    local logfile="$LOG_DIR/node_${port}.log"

    if grep -q "predecesseur.*:${expected_port}" "$logfile" 2>/dev/null; then
        ok "Noeud $port : predecesseur = $expected_port"
        return 0
    fi
    fail "Noeud $port : predecesseur $expected_port non trouve dans les logs"
    return 1
}

# Verifie qu'aucun noeud actif ne boucle encore sur un noeud mort.
# On cherche la presence de messages "ne repond pas" recents dans les logs
# APRES le delai d'attente (donc l'anneau devrait s'etre re-stabilise).
# Usage : check_no_loop <dead_port> <port1> [port2] ...
check_no_loop() {
    local dead_port=$1
    shift
    local active_ports=("$@")
    local found_loop=0

    for port in "${active_ports[@]}"; do
        local logfile="$LOG_DIR/node_${port}.log"
        # On compte les occurrences recentes de "ne repond pas" pointant vers le noeud mort
        local count
        count=$(grep -c "successeur.*:${dead_port}.*ne repond pas\|ne repond pas.*:${dead_port}" "$logfile" 2>/dev/null || echo 0)
        if [ "$count" -gt 0 ]; then
            # Il peut y en avoir quelques-unes juste apres le depart, c'est normal.
            # On verifie plutot qu'il n'y a PAS eu de nouvelle occurrence apres stabilisation.
            # Heuristique : si le log contient "nouveau successeur" APRES la derniere
            # occurrence de "ne repond pas", l'anneau s'est bien recupere.
            local last_loop_line
            last_loop_line=$(grep -n "ne repond pas" "$logfile" 2>/dev/null | tail -1 | cut -d: -f1)
            local last_recovery_line
            last_recovery_line=$(grep -n "nouveau successeur\|LEAVE_NOTIFY" "$logfile" 2>/dev/null | tail -1 | cut -d: -f1)

            if [ -n "$last_recovery_line" ] && [ -n "$last_loop_line" ] && \
               [ "$last_recovery_line" -gt "$last_loop_line" ]; then
                ok "Noeud $port : boucle detectee puis resolue (successeur mort $dead_port remplace)"
            else
                fail "Noeud $port : toujours en boucle sur le noeud mort $dead_port"
                found_loop=1
            fi
        fi
    done

    if [ "$found_loop" -eq 0 ]; then
        ok "Aucun noeud actif ne boucle sur le noeud mort $dead_port"
    fi
}

# Affiche l'etat de l'anneau depuis les logs
show_ring_state() {
    echo ""
    echo "  --- Etat de l'anneau (derniere valeur connue) ---"
    for port in 8001 8002 8003 8004 8005; do
        local logfile="$LOG_DIR/node_${port}.log"
        [ -f "$logfile" ] || continue
        if node_alive "$port"; then
            local succ pred id
            id=$(grep -o "id=[0-9]*" "$logfile" | head -1 | cut -d= -f2)
            # On prend les 50 dernieres lignes pour avoir l'etat courant
            succ=$(tail -50 "$logfile" | grep -o "successeur[^,)]*= [^ ]*" | tail -1 | awk '{print $NF}')
            pred=$(tail -50 "$logfile" | grep -o "pr[eé]d[eé]cesseur = [^ ]*" | tail -1 | awk '{print $3}' | tr -d 'é')
            echo "  [ACTIF]  Noeud $port (id=$id)  succ=$succ  pred=$pred"
        else
            echo "  [ARRETE] Noeud $port"
        fi
    done
    echo "  -------------------------------------------------"
    echo ""
}

# Nettoyage au exit
cleanup() {
    echo ""
    echo "Nettoyage des processus..."
    for pid_var in PID_8001 PID_8002 PID_8003 PID_8004 PID_8005; do
        local pid="${!pid_var}"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            kill -SIGKILL "$pid" 2>/dev/null
        fi
    done
    pkill -f "chord.Main" 2>/dev/null
}

trap cleanup EXIT

# =============================================================
echo ""
echo "============================================"
echo "   TEST CHORD --- ROUTAGE DYNAMIQUE"
echo "   (JOIN / LEAVE --- sans donnees)"
echo "============================================"
echo ""

# =============================================================
echo "=== Phase 0: Nettoyage + Compilation ==="
# =============================================================

pkill -f "chord.Main" 2>/dev/null
sleep 1

rm -rf "$BIN_DIR" "$LOG_DIR"
mkdir -p "$BIN_DIR" "$LOG_DIR"

echo "Compilation..."
javac -d "$BIN_DIR" "$SCRIPT_DIR/src/dht"/*.java "$SCRIPT_DIR/src/chord"/*.java

if [ $? -ne 0 ]; then
    echo "Compilation echouee ! Arret."
    exit 1
fi
echo "Compilation OK"

# =============================================================
echo ""
echo "=== Phase 1: Anneau de base --- noeud 8001 (bootstrap) ==="
# =============================================================

start_node 8001
run_term "CHORD NODE 8001" "tail -f $LOG_DIR/node_8001.log"

wait_converge "$STABILIZE_WAIT" 8001
show_ring_state

if node_alive 8001; then
    ok "Noeud 8001 repond"
else
    fail "Noeud 8001 ne repond pas apres demarrage"
fi

echo ""
echo "Appuyez sur ENTREE pour ajouter le noeud 8002..."
read -r

# =============================================================
echo ""
echo "=== Phase 2: JOIN --- noeud 8002 rejoint via 8001 ==="
# =============================================================

start_node 8002 "127.0.0.1:8001"
run_term "CHORD NODE 8002" "tail -f $LOG_DIR/node_8002.log"

wait_converge "$STABILIZE_WAIT" 8001 8002
show_ring_state

for p in 8001 8002; do
    if node_alive "$p"; then
        ok "Noeud $p repond"
    else
        fail "Noeud $p ne repond pas"
    fi
done

echo ""
echo "Appuyez sur ENTREE pour ajouter le noeud 8003..."
read -r

# =============================================================
echo ""
echo "=== Phase 3: JOIN --- noeud 8003 rejoint via 8001 ==="
# =============================================================

start_node 8003 "127.0.0.1:8001"
run_term "CHORD NODE 8003" "tail -f $LOG_DIR/node_8003.log"

wait_converge "$STABILIZE_WAIT" 8001 8002 8003
show_ring_state

for p in 8001 8002 8003; do
    if node_alive "$p"; then
        ok "Noeud $p repond"
    else
        fail "Noeud $p ne repond pas"
    fi
done

echo ""
echo "Appuyez sur ENTREE pour lancer le JOIN du noeud 8004..."
read -r

# =============================================================
echo ""
echo "=== Phase 4: JOIN --- le noeud 8004 rejoint l'anneau ==="
# =============================================================

start_node 8004 "127.0.0.1:8001"
run_term "CHORD NODE 8004 (JOIN)" "tail -f $LOG_DIR/node_8004.log"

wait_converge "$STABILIZE_WAIT" 8001 8002 8003 8004
show_ring_state

echo "Verification apres JOIN de 8004..."

if node_alive 8004; then
    ok "Noeud 8004 repond"
else
    fail "Noeud 8004 ne repond pas apres JOIN"
fi

for p in 8001 8002 8003; do
    if node_alive "$p"; then
        ok "Noeud $p toujours actif apres JOIN de 8004"
    else
        fail "Noeud $p ne repond plus apres JOIN de 8004"
    fi
done

if grep -q "successeur = " "$LOG_DIR/node_8004.log" 2>/dev/null; then
    ok "Noeud 8004 a un successeur defini"
else
    fail "Noeud 8004 n'a pas de successeur defini"
fi

if grep -q "predecesseur = 127.0.0.1:8004" "$LOG_DIR"/node_*.log 2>/dev/null; then
    ok "Noeud 8004 est reconnu comme predecesseur par un autre noeud"
else
    fail "Noeud 8004 n'est reconnu comme predecesseur par aucun noeud"
fi

echo ""
echo "Appuyez sur ENTREE pour simuler le LEAVE propre de 8002 (SIGTERM)..."
read -r

# =============================================================
echo ""
echo "=== Phase 5: LEAVE PROPRE --- arret gracieux de 8002 (SIGTERM) ==="
# =============================================================

stop_graceful 8002

wait_converge "$LEAVE_WAIT" 8001 8003 8004
show_ring_state

echo "Verification apres LEAVE propre de 8002..."

if ! node_alive 8002; then
    ok "Noeud 8002 est bien arrete"
else
    fail "Noeud 8002 repond encore apres SIGTERM"
fi

for p in 8001 8003 8004; do
    if node_alive "$p"; then
        ok "Noeud $p toujours actif apres depart de 8002"
    else
        fail "Noeud $p ne repond plus apres depart de 8002"
    fi
done

check_no_loop 8002 8001 8003 8004

if grep -q "LEAVE_NOTIFY.*nouveau successeur\|nouveau successeur" "$LOG_DIR"/node_*.log 2>/dev/null; then
    ok "LEAVE_NOTIFY traite : un noeud a mis a jour son successeur"
else
    fail "Aucune trace de LEAVE_NOTIFY dans les logs"
fi

echo ""
echo "Appuyez sur ENTREE pour lancer le JOIN de 8005 (anneau sans 8002)..."
read -r

# =============================================================
echo ""
echo "=== Phase 6: JOIN APRES LEAVE --- noeud 8005 rejoint l'anneau ==="
# =============================================================

start_node 8005 "127.0.0.1:8001"
run_term "CHORD NODE 8005 (JOIN apres LEAVE)" "tail -f $LOG_DIR/node_8005.log"

wait_converge "$STABILIZE_WAIT" 8001 8003 8004 8005
show_ring_state

echo "Verification apres JOIN de 8005..."

if node_alive 8005; then
    ok "Noeud 8005 repond"
else
    fail "Noeud 8005 ne repond pas"
fi

for p in 8001 8003 8004; do
    if node_alive "$p"; then
        ok "Noeud $p toujours actif apres JOIN de 8005"
    else
        fail "Noeud $p ne repond plus apres JOIN de 8005"
    fi
done

if grep -q "successeur = " "$LOG_DIR/node_8005.log" 2>/dev/null; then
    ok "Noeud 8005 a un successeur defini"
else
    fail "Noeud 8005 n'a pas de successeur defini"
fi

if grep -q "predecesseur = 127.0.0.1:8005" "$LOG_DIR"/node_*.log 2>/dev/null; then
    ok "Noeud 8005 est reconnu comme predecesseur par un autre noeud"
else
    fail "Noeud 8005 n'est reconnu comme predecesseur par aucun noeud"
fi


check_no_loop 8003 8001 8004 8005


# =============================================================
echo ""
echo "============================================"
echo "   RESULTATS"
echo "============================================"
TOTAL=$((PASS + FAIL))
echo "  Tests passes  : $PASS / $TOTAL"
echo "  Tests echoues : $FAIL / $TOTAL"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo "  TOUS LES TESTS PASSENT."
    echo "  Anneau actif : noeuds 8001, 8004, 8005"
    echo "  Logs : $LOG_DIR/"
    echo ""
    echo "  Pour fermer tout : pkill -f chord.Main"
    echo ""
else
    echo "  $FAIL TEST(S) ECHOUE(S)."
    echo "  Consultez les logs : $LOG_DIR/"
    echo "  Astuce : grep 'ne repond pas\|LEAVE\|Stabilize\|backup' $LOG_DIR/*.log"
    echo ""
    echo "  Pour fermer tout : pkill -f chord.Main"
    echo ""
fi
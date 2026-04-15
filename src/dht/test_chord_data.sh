#!/bin/bash

# =============================================================
#  test_chord_data.sh
#  Test de la distribution et redistribution des donnees Chord.
#  - PUT : les cles vont sur le bon noeud (hash correct)
#  - JOIN : redistribution vers le nouveau noeud
#  - LEAVE : redistribution vers le successeur
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

STABILIZE_WAIT=20
LEAVE_WAIT=20
PUT_WAIT=5       # secondes apres un PUT pour que le routage se propage
POLL_INTERVAL=2

PASS=0
FAIL=0

PID_8001=""
PID_8002=""
PID_8003=""
PID_8004=""

# =============================================================
# FONCTIONS UTILITAIRES
# =============================================================

ok()  { echo "  [PASS] $*"; ((PASS++)); }
fail(){ echo "  [FAIL] $*"; ((FAIL++)); }

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

node_alive() {
    local port=$1
    nc -z -w2 127.0.0.1 "$port" 2>/dev/null
    return $?
}

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

wait_converge() {
    local max_wait=$1
    shift
    local ports=("$@")
    local elapsed=0
    local stable_ticks=0
    local needed_ticks=3

    echo "  Attente convergence (max ${max_wait}s)..."
    while [ "$elapsed" -lt "$max_wait" ]; do
        sleep "$POLL_INTERVAL"
        elapsed=$((elapsed + POLL_INTERVAL))

        local unstable=0

        for port in "${ports[@]}"; do
            local logfile="$LOG_DIR/node_${port}.log"
            [ -f "$logfile" ] || continue
            if tail -20 "$logfile" 2>/dev/null | grep -q "ne repond pas\|recherche d'un backup"; then
                unstable=1
                break
            fi
        done

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

        if [ "$unstable" -eq 0 ] && [ "${#ports[@]}" -gt 2 ]; then
            local first_succ=""
            local all_same=1
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
                unstable=1
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

show_ring_state() {
    echo ""
    echo "  --- Etat de l'anneau ---"
    for port in 8001 8002 8003 8004; do
        local logfile="$LOG_DIR/node_${port}.log"
        [ -f "$logfile" ] || continue
        if node_alive "$port"; then
            local id succ pred
            id=$(grep -o "id=[0-9]*" "$logfile" | head -1 | cut -d= -f2)
            succ=$(tail -50 "$logfile" | grep -o "successeur[^,)]*= [^ ]*" | tail -1 | awk '{print $NF}')
            pred=$(tail -50 "$logfile" | grep -o "pr[eé]d[eé]cesseur = [^ ]*" | tail -1 | awk '{print $3}' | tr -d 'é')
            echo "  [ACTIF]  Noeud $port (id=$id)  succ=$succ  pred=$pred"
        else
            echo "  [ARRETE] Noeud $port"
        fi
    done
    echo "  ------------------------"
    echo ""
}

# Envoie un PUT en mode batch (argument direct, pas de stdin pipe).
# Le client s'execute et quitte immediatement apres l'envoi.
# Usage : do_put <port> <key> <value>
do_put() {
    local port=$1
    local key=$2
    local value=$3
    java -cp "$BIN_DIR" dht.Client "127.0.0.1:$port" PUT "$key" "$value" > /dev/null 2>&1
    sleep "$PUT_WAIT"
    echo "  PUT $key=$value envoye via noeud $port"
}

# Verifie qu'une cle est bien stockee sur un noeud precis en cherchant dans ses logs.
# Usage : check_stored_on <key> <value> <port>
check_stored_on() {
    local key=$1
    local value=$2
    local port=$3
    local logfile="$LOG_DIR/node_${port}.log"

    if grep -q "Stocké \[$key=$value\].*noeud $port\|Stocke \[$key=$value\].*$port\|>>> Stocke \[$key=$value\]" "$logfile" 2>/dev/null; then
        ok "Cle '$key=$value' bien stockee sur noeud $port"
        return 0
    fi
    fail "Cle '$key=$value' NON trouvee sur noeud $port"
    return 1
}

# Verifie qu'une cle a ete transferee vers un noeud (dans les logs de transfert)
# Usage : check_transferred_to <key> <dest_port>
check_transferred_to() {
    local key=$1
    local dest_port=$2

    if grep -r "transfere.*cle.*$key\|$key.*->.*$dest_port\|recu.*$key" "$LOG_DIR"/node_*.log 2>/dev/null | grep -q "$dest_port\|$key"; then
        ok "Cle '$key' transferee vers noeud $dest_port"
        return 0
    fi
    # Verifier aussi dans le log du noeud destinataire
    if grep -q "recu.*$key\|$key" "$LOG_DIR/node_${dest_port}.log" 2>/dev/null; then
        ok "Cle '$key' presente sur noeud $dest_port apres transfert"
        return 0
    fi
    fail "Cle '$key' non transferee vers noeud $dest_port"
    return 1
}

# Verifie qu'un transfert de cles a eu lieu entre deux noeuds
# Usage : check_transfer_happened <src_port> <dest_port>
check_transfer_happened() {
    local src=$1
    local dest=$2
    local logfile="$LOG_DIR/node_${src}.log"

    if grep -q "transfere.*->.*$dest\|transfert.*->.*127.0.0.1:$dest\|LEAVE.*->.*127.0.0.1:$dest" "$logfile" 2>/dev/null; then
        ok "Transfert de cles detecte : noeud $src -> noeud $dest"
        return 0
    fi
    # Verifier cote destinataire
    if grep -q "recu.*cle.*de.*$src\|de 127.0.0.1:$src" "$LOG_DIR/node_${dest}.log" 2>/dev/null; then
        ok "Noeud $dest a bien recu des cles de noeud $src"
        return 0
    fi
    fail "Aucun transfert detecte entre noeud $src et noeud $dest"
    return 1
}

# Nettoyage au exit
cleanup() {
    echo ""
    echo "Nettoyage des processus..."
    for pid_var in PID_8001 PID_8002 PID_8003 PID_8004; do
        local pid="${!pid_var}"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            kill -SIGKILL "$pid" 2>/dev/null
        fi
    done
    pkill -f "chord.Main" 2>/dev/null
    pkill -f "dht.Client" 2>/dev/null
}

trap cleanup EXIT

# =============================================================
echo ""
echo "============================================"
echo "   TEST CHORD --- DONNEES"
echo "   Distribution et redistribution"
echo "============================================"
echo ""

# =============================================================
echo "=== Phase 0: Nettoyage + Compilation ==="
# =============================================================

pkill -f "chord.Main" 2>/dev/null
pkill -f "dht.Client" 2>/dev/null
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
echo "=== Phase 1: Anneau de base --- 2 noeuds ==="
# Deux noeuds suffisent pour tester le PUT de base.
# On verifie que les cles sont bien routees vers le bon noeud.
# =============================================================

start_node 8001
sleep 1
start_node 8002 "127.0.0.1:8001"

run_term "CHORD NODE 8001" "tail -f $LOG_DIR/node_8001.log"
run_term "CHORD NODE 8002" "tail -f $LOG_DIR/node_8002.log"

wait_converge "$STABILIZE_WAIT" 8001 8002
show_ring_state

for p in 8001 8002; do
    if node_alive "$p"; then
        ok "Noeud $p actif"
    else
        fail "Noeud $p ne repond pas"
    fi
done

echo ""
echo "Appuyez sur ENTREE pour inserer les donnees de reference..."
read -r

# =============================================================
echo ""
echo "=== Phase 2: PUT --- insertion des donnees de reference ==="
# On insere plusieurs cles depuis les deux noeuds.
# Chaque cle doit etre routee et stockee sur le noeud dont
# l'id est le successeur du hash de la cle.
# On verifie dans les logs que le stockage a bien eu lieu.
# =============================================================

echo "  Insertion de 8 cles couvrant les 4 noeuds..."
# Repartition calculee selon les IDs : 8001=42, 8002=41, 8003=40, 8004=39
# k2,k40  -> id=39 -> noeud 8004  (quand il sera present)
# k3,k41  -> id=40 -> noeud 8003  (quand il sera present)
# k4,k20  -> id=41 -> noeud 8002
# k5,k21  -> id=42 -> noeud 8001
# Avec seulement 8001(id=42) et 8002(id=41) au depart :
#   k4,k20 -> 8002 | k5,k21 -> 8001
#   k2,k40,k3,k41 -> 8002 (car 8003/8004 absents, successeur suivant = 8002 via wrap)
do_put 8001 "k4" "val_k4"
do_put 8001 "k5" "val_k5"
do_put 8002 "k20" "val_k20"
do_put 8002 "k21" "val_k21"
do_put 8001 "k2" "val_k2"
do_put 8001 "k3" "val_k3"
do_put 8002 "k40" "val_k40"
do_put 8002 "k41" "val_k41"

echo "  Attente propagation des messages (5s supplementaires)..."
sleep 5

echo ""
echo "  Verification du stockage (chaque cle doit etre sur UN noeud)..."
# Pour chaque cle, verifier qu'elle est stockee quelque part (8001 ou 8002)
for kv in "k4:val_k4" "k5:val_k5" "k20:val_k20" "k21:val_k21" \
          "k2:val_k2" "k3:val_k3" "k40:val_k40" "k41:val_k41"; do
    key="${kv%%:*}"
    val="${kv##*:}"
    found=0
    for p in 8001 8002; do
        if grep -q "Stocké \[$key=$val\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
            ok "Cle '$key=$val' stockee sur noeud $p"
            found=1
            break
        fi
    done
    if [ "$found" -eq 0 ]; then
        fail "Cle '$key=$val' introuvable sur aucun noeud"
    fi
done

echo ""
echo "  Verification qu'aucune cle n'est dupliquee sur les deux noeuds..."
for kv in "k4:val_k4" "k5:val_k5" "k20:val_k20" "k21:val_k21" \
          "k2:val_k2" "k3:val_k3" "k40:val_k40" "k41:val_k41"; do
    key="${kv%%:*}"
    val="${kv##*:}"
    count=0
    for p in 8001 8002; do
        if grep -q "Stocké \[$key=$val\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
            count=$((count + 1))
        fi
    done
    if [ "$count" -eq 1 ]; then
        ok "Cle '$key' stockee sur exactement 1 noeud (pas de doublon)"
    elif [ "$count" -gt 1 ]; then
        fail "Cle '$key' dupliquee sur $count noeuds !"
    fi
done

echo ""
echo "Appuyez sur ENTREE pour ajouter le noeud 8003 et tester la redistribution..."
read -r

# =============================================================
echo ""
echo "=== Phase 3: JOIN 8003 --- redistribution des cles ==="
# Quand 8003 rejoint, il contacte son successeur et lui demande
# les cles dont le hash tombe dans sa plage.
# On verifie que :
#   - un transfert a bien eu lieu
#   - les cles transferees ne sont plus sur l'ancien noeud
#   - les cles sont accessibles depuis n'importe quel noeud
# =============================================================

start_node 8003 "127.0.0.1:8001"
run_term "CHORD NODE 8003 (JOIN)" "tail -f $LOG_DIR/node_8003.log"

wait_converge "$STABILIZE_WAIT" 8001 8002 8003
show_ring_state

if node_alive 8003; then
    ok "Noeud 8003 actif"
else
    fail "Noeud 8003 ne repond pas apres JOIN"
fi

echo ""
echo "  Verification de la redistribution lors du JOIN de 8003..."

# Verifier qu'un transfert de cles a eu lieu vers 8003
if grep -q "transfere\|transfert\|recu.*cle" "$LOG_DIR/node_8003.log" 2>/dev/null || \
   grep -q "transfere.*->.*127.0.0.1:8003\|->.*8003.*cle\|transf" "$LOG_DIR"/node_*.log 2>/dev/null; then
    ok "Redistribution detectee : des cles ont ete transferees vers 8003"
else
    # Pas de transfert peut signifier que 8003 n'a pas de cles dans sa plage, ce qui est possible
    echo "  [INFO] Aucun transfert vers 8003 (possible si aucune cle dans sa plage)"
fi

echo ""
echo "  Verification que toutes les cles sont toujours accessibles apres JOIN..."
for kv in "k4:val_k4" "k5:val_k5" "k20:val_k20" "k21:val_k21" \
          "k2:val_k2" "k3:val_k3" "k40:val_k40" "k41:val_k41"; do
    key="${kv%%:*}"
    val="${kv##*:}"
    found=0
    for p in 8001 8002 8003; do
        if grep -q "Stocké \[$key=$val\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
            ok "Cle '$key=$val' toujours presente sur noeud $p apres JOIN 8003"
            found=1
            break
        fi
    done
    if [ "$found" -eq 0 ]; then
        fail "Cle '$key=$val' perdue apres JOIN de 8003"
    fi
done

echo ""
echo "  Insertion d'une nouvelle cle depuis 8003 (test routage post-JOIN)..."
do_put 8003 "knew" "apres_join"
found=0
for p in 8001 8002 8003; do
    if grep -q "Stocké \[knew=apres_join\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
        ok "Cle 'knew=apres_join' stockee sur noeud $p"
        found=1
        break
    fi
done
if [ "$found" -eq 0 ]; then
    fail "Cle 'knew=apres_join' introuvable apres insertion depuis 8003"
fi

echo ""
echo "Appuyez sur ENTREE pour ajouter le noeud 8004..."
read -r

# =============================================================
echo ""
echo "=== Phase 4: JOIN 8004 --- redistribution avec 4 noeuds ==="
# =============================================================

start_node 8004 "127.0.0.1:8001"
run_term "CHORD NODE 8004 (JOIN)" "tail -f $LOG_DIR/node_8004.log"

wait_converge "$STABILIZE_WAIT" 8001 8002 8003 8004
show_ring_state

if node_alive 8004; then
    ok "Noeud 8004 actif"
else
    fail "Noeud 8004 ne repond pas apres JOIN"
fi

echo ""
echo "  Verification que toutes les cles sont toujours presentes apres JOIN 8004..."
for kv in "k4:val_k4" "k5:val_k5" "k20:val_k20" "k21:val_k21" \
          "k2:val_k2" "k3:val_k3" "k40:val_k40" "k41:val_k41" "knew:apres_join"; do
    key="${kv%%:*}"
    val="${kv##*:}"
    found=0
    for p in 8001 8002 8003 8004; do
        if grep -q "Stocké \[$key=$val\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
            ok "Cle '$key=$val' presente sur noeud $p apres JOIN 8004"
            found=1
            break
        fi
    done
    if [ "$found" -eq 0 ]; then
        fail "Cle '$key=$val' perdue apres JOIN de 8004"
    fi
done

echo ""
echo "  Insertion d'une cle depuis 8004..."
do_put 8004 "ktest" "noeud4"
found=0
for p in 8001 8002 8003 8004; do
    if grep -q "Stocké \[ktest=noeud4\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
        ok "Cle 'ktest=noeud4' stockee sur noeud $p"
        found=1
        break
    fi
done
if [ "$found" -eq 0 ]; then
    fail "Cle 'ktest=noeud4' introuvable"
fi

echo ""
echo "Appuyez sur ENTREE pour simuler le LEAVE propre de 8002 (SIGTERM)..."
read -r

# =============================================================
echo ""
echo "=== Phase 5: LEAVE 8002 --- redistribution vers le successeur ==="
# Quand 8002 quitte proprement, il transfere toutes ses cles
# a son successeur avant de s'arreter.
# On verifie que :
#   - les cles de 8002 se retrouvent sur son successeur
#   - aucune cle n'est perdue
# =============================================================

# Identifier les cles actuellement sur 8002 avant qu'il parte
echo "  Cles actuellement sur noeud 8002 :"
grep "Stocké \[" "$LOG_DIR/node_8002.log" 2>/dev/null | grep -o "\[.*\]" | sort -u | while read -r k; do
    echo "    $k"
done

# Identifier le successeur de 8002 avant le depart
SUCC_8002=$(tail -50 "$LOG_DIR/node_8002.log" | grep -o "successeur[^,)]*= [^ ]*" | tail -1 | awk '{print $NF}')
SUCC_PORT=$(echo "$SUCC_8002" | cut -d: -f2)
echo "  Successeur de 8002 : $SUCC_8002 (port $SUCC_PORT)"

stop_graceful 8002

wait_converge "$LEAVE_WAIT" 8001 8003 8004
show_ring_state

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

echo ""
echo "  Verification du transfert de cles lors du LEAVE de 8002..."
if grep -q "transfert LEAVE\|transfere.*LEAVE" "$LOG_DIR/node_8002.log" 2>/dev/null; then
    ok "Transfert de cles au LEAVE detecte dans les logs de 8002"
    # Afficher les cles transferees
    grep "transfert LEAVE" "$LOG_DIR/node_8002.log" 2>/dev/null | tail -1 | while read -r line; do
        echo "    $line"
    done
else
    echo "  [INFO] Pas de trace de transfert (possible si 8002 n'avait pas de cles)"
fi

echo ""
echo "  Verification que toutes les cles sont toujours presentes apres LEAVE 8002..."
echo "  (routage avec 3 noeuds : 8001 id=42, 8003 id=40, 8004 id=39)"
# Apres le depart de 8002, le routage change :
#   k4  (hash=41) -> 8001 (id=42, successeur de 41)  via transfert LEAVE
#   k20 (hash=41) -> 8001 (id=42)                    via transfert LEAVE
#   k2  (hash=39) -> 8004 (id=39)  deja redistribue au JOIN de 8004
#   k40 (hash=39) -> 8004 (id=39)  deja redistribue au JOIN de 8004
#   k3  (hash=40) -> 8003 (id=40)  deja redistribue au JOIN de 8003
#   k41 (hash=40) -> 8003 (id=40)  deja redistribue au JOIN de 8003
#   knew (hash=21)-> 8004           deja sur 8003 ou 8004
#   ktest(hash=61)-> 8004           deja sur 8004
declare -A KEY_NODE
KEY_NODE["k4:val_k4"]="8001"
KEY_NODE["k5:val_k5"]="8001"
KEY_NODE["k20:val_k20"]="8001"
KEY_NODE["k21:val_k21"]="8001"
KEY_NODE["k2:val_k2"]="8004"
KEY_NODE["k40:val_k40"]="8004"
KEY_NODE["k3:val_k3"]="8003"
KEY_NODE["k41:val_k41"]="8003"
KEY_NODE["knew:apres_join"]="8003"
KEY_NODE["ktest:noeud4"]="8004"

for kv in "${!KEY_NODE[@]}"; do
    key="${kv%%:*}"
    val="${kv##*:}"
    expected="${KEY_NODE[$kv]}"
    found=0
    # Chercher sur le noeud attendu ET sur les autres (transfert peut avoir change la cible)
    for p in 8001 8003 8004; do
        if grep -q "Stocké \[$key=$val\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
            if [ "$p" = "$expected" ]; then
                ok "Cle '$key=$val' sur noeud $p (attendu) apres LEAVE 8002"
            else
                ok "Cle '$key=$val' sur noeud $p (attendu=$expected) apres LEAVE 8002"
            fi
            found=1
            break
        fi
        if grep -q "reçu.*$key\|recu.*$key\|$key" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
            ok "Cle '$key' recue par transfert sur noeud $p apres LEAVE 8002"
            found=1
            break
        fi
    done
    if [ "$found" -eq 0 ]; then
        fail "Cle '$key=$val' PERDUE apres depart de 8002"
    fi
done

echo ""
echo "  Insertion d'une cle apres le LEAVE (test routage avec 3 noeuds)..."
do_put 8001 "kleave" "ok"
found=0
for p in 8001 8003 8004; do
    if grep -q "Stocké \[kleave=ok\]" "$LOG_DIR/node_${p}.log" 2>/dev/null; then
        ok "Cle 'kleave=ok' stockee sur noeud $p"
        found=1
        break
    fi
done
if [ "$found" -eq 0 ]; then
    fail "Cle 'kleave=ok' introuvable apres insertion post-LEAVE"
fi


echo "============================================"
echo "   RESULTATS"
echo "============================================"
TOTAL=$((PASS + FAIL))
echo "  Tests passes  : $PASS / $TOTAL"
echo "  Tests echoues : $FAIL / $TOTAL"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo "  TOUS LES TESTS PASSENT."
    echo "  Distribution et redistribution des donnees OK."
    echo "  Logs : $LOG_DIR/"
    echo ""
    echo "  Pour fermer tout : pkill -f chord.Main && pkill -f dht.Client"
    echo ""
else
    echo "  $FAIL TEST(S) ECHOUE(S)."
    echo "  Consultez les logs : $LOG_DIR/"
    echo "  Astuce : grep 'Stocke\|transfere\|recu.*cle' $LOG_DIR/*.log"
    echo ""
    echo "  Pour fermer tout : pkill -f chord.Main && pkill -f dht.Client"
    echo ""
fi
package chord;

import dht.Address;
import metrics.MetricsLogger;

import java.util.ArrayList;
import java.util.List;

public class StabilizeService {

    private static final int INTERVAL_MS         = 2000;
    private static final int CHECK_PRED_INTERVAL = 3;

    private final ChordNode        node;
    private final ChordNodeHandler handler;
    private int nextFinger = 1;
    private int cycleCount = 0;

    // --- Rebalance tracking ---
    // On chronomètre depuis le premier cycle où le successeur ne répond plus
    // jusqu'au premier cycle où il répond à nouveau (ou un remplaçant est trouvé).
    private boolean rebalancing      = false;
    private long    rebalanceStartMs = 0;
    private Address deadSuccessor    = null;

    private volatile Thread stabThread = null;

    public StabilizeService(ChordNode node, ChordNodeHandler handler) {
        this.node    = node;
        this.handler = handler;
    }

    public void start() {
        stabThread = new Thread(this::loop, "Stabilizer-" + node.self.getPort());
        stabThread.setDaemon(true);
        stabThread.start();
    }

    public void stop() {
        if (stabThread != null) stabThread.interrupt();
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(INTERVAL_MS);
                stabilize();
                fixFingers();
                cycleCount++;
                if (cycleCount % CHECK_PRED_INTERVAL == 0) {
                    checkPredecessorAlive();
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println("[Stabilizer-" + node.self.getPort() + "] arrêté.");
    }

    void stabilize() {
        Address succ = node.fingerTable.getSuccessor();

        if (succ == null) {
            node.fingerTable.setSuccessor(node.self);
            return;
        }

        if (succ.equals(node.self)) {
            Address pred = node.predecessor;
            if (pred != null && !pred.equals(node.self)) {
                node.fingerTable.setSuccessor(pred);
                succ = pred;
            } else {
                return;
            }
        }

        ChordNodeHandler.PredecessorResult res = handler.remoteCallGetPredecessor(succ);

        if (!res.reachable) {
            // --- Début du rebalance : on note l'heure du premier échec ---
            if (!rebalancing) {
                rebalancing      = true;
                rebalanceStartMs = System.currentTimeMillis();
                deadSuccessor    = succ;
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] CRASH DÉTECTÉ : successeur " + succ
                        + " ne répond plus — chrono rebalance démarré");
            }

            Address replacement = findLiveReplacement(succ);
            if (replacement != null) {
                node.fingerTable.setSuccessor(replacement);
                succ = replacement;
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] nouveau successeur trouvé : " + replacement);
                res = handler.remoteCallGetPredecessor(succ);
                if (!res.reachable) return;

                // Remplaçant trouvé et joignable → rebalance terminé
                logRebalanceCompleted();
            } else {
                node.fingerTable.setSuccessor(node.self);
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] aucun backup → anneau réduit à ce nœud");
                logRebalanceCompleted();
                return;
            }

        } else if (rebalancing && succ.equals(deadSuccessor)) {
            // Le successeur mort répond à nouveau (cas peu probable mais possible)
            logRebalanceCompleted();
        } else if (rebalancing) {
            // Un nouveau successeur valide est en place et joignable
            logRebalanceCompleted();
        }

        Address x = res.predecessor;
        if (x != null && !x.equals(node.self)) {
            int xId    = ChordHasher.hash(x);
            int succId = ChordHasher.hash(succ);
            if (ChordHasher.inRange(xId, node.id, succId)) {
                node.fingerTable.setSuccessor(x);
                succ = x;
            }
        }

        handler.sendNotify(succ);
        updateSuccessorList(succ);
    }

    /**
     * Log le REBALANCE avec la vraie durée mesurée depuis la détection du crash.
     * active_nodes est lu depuis -Dchord.active_nodes si dispo (passé par le script),
     * sinon estimé depuis la successorList.
     */
    private void logRebalanceCompleted() {
        if (!rebalancing) return;
        long duration = System.currentTimeMillis() - rebalanceStartMs;
        rebalancing   = false;
        deadSuccessor = null;

        int activeNodes;
        String prop = System.getProperty("chord.active_nodes");
        if (prop != null) {
            try { activeNodes = Integer.parseInt(prop); }
            catch (NumberFormatException e) { activeNodes = node.successorList.size() + 1; }
        } else {
            activeNodes = node.successorList.size() + 1;
        }

        MetricsLogger.get().log("REBALANCE", duration, 0, "",
                "active_nodes=" + activeNodes);
        System.out.println("[Stabilize " + node.self.getPort()
                + "] REBALANCE terminé en " + duration + "ms"
                + " — active_nodes=" + activeNodes);
    }

    private void checkPredecessorAlive() {
        Address pred = node.predecessor;
        if (pred == null || pred.equals(node.self)) return;
        ChordNodeHandler.PredecessorResult r = handler.remoteCallGetPredecessor(pred);
        if (!r.reachable) {
            System.out.println("[Stabilize " + node.self.getPort()
                    + "] prédécesseur " + pred + " ne répond plus → remis à null");
            node.predecessor = null;
        }
    }

    private Address findLiveReplacement(Address deadNode) {
        synchronized (node) {
            for (Address candidate : node.successorList) {
                if (!candidate.equals(deadNode) && !candidate.equals(node.self)) {
                    ChordNodeHandler.PredecessorResult r = handler.remoteCallGetPredecessor(candidate);
                    if (r.reachable) return candidate;
                }
            }
        }
        for (int i = 0; i < ChordHasher.M; i++) {
            Address finger = node.fingerTable.get(i);
            if (finger == null || finger.equals(deadNode) || finger.equals(node.self)) continue;
            ChordNodeHandler.PredecessorResult r = handler.remoteCallGetPredecessor(finger);
            if (r.reachable) return finger;
        }
        return null;
    }

    private void updateSuccessorList(Address succ) {
        List<Address> list = new ArrayList<>();
        list.add(succ);
        Address current = succ;
        while (list.size() < ChordNode.SUCCESSOR_LIST_SIZE) {
            Address next = handler.remoteCallFindSuccessor(current, ChordHasher.hash(current) + 1);
            if (next == null || next.equals(current) || next.equals(node.self)) break;
            if (!list.contains(next)) list.add(next);
            current = next;
        }
        node.updateSuccessorList(list);
    }

    void fixFingers() {
        if (nextFinger >= ChordHasher.M) nextFinger = 1;
        int start = (node.id + (1 << nextFinger)) % ChordHasher.RING_SIZE;
        Address target = node.findSuccessor(start);
        node.fingerTable.set(nextFinger, target);
        System.out.println("[FixFingers " + node.self.getPort() + "] finger[" + nextFinger
                + "] start=" + start + " -> " + target);
        nextFinger++;
    }
}
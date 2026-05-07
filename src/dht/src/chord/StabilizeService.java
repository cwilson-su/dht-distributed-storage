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

    // Rebalance detection state
    private Address  lastKnownSuccessor = null;
    private boolean  rebalanceInProgress = false;
    private long     rebalanceStartMs    = 0;
    private int      storeBeforeRebalance = 0;

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
        if (stabThread != null) {
            stabThread.interrupt();
        }
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
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] successeur initialise depuis predecesseur -> " + succ);
            } else {
                return;
            }
        }

        // Detect successor failure → start rebalance tracking
        ChordNodeHandler.PredecessorResult res = handler.remoteCallGetPredecessor(succ);
        if (!res.reachable) {
            System.out.println("[Stabilize " + node.self.getPort()
                    + "] successeur " + succ + " ne répond pas → recherche d'un backup");

            // Start rebalance measurement if this is a new failure
            if (!rebalanceInProgress || !succ.equals(lastKnownSuccessor)) {
                rebalanceInProgress  = true;
                rebalanceStartMs     = System.currentTimeMillis();
                storeBeforeRebalance = handler.getStoreSize();
                lastKnownSuccessor   = succ;
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] REBALANCE started (successor crash detected)");
            }

            Address replacement = findLiveReplacement(succ);
            if (replacement != null) {
                node.fingerTable.setSuccessor(replacement);
                succ = replacement;
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] nouveau successeur : " + replacement);
                res = handler.remoteCallGetPredecessor(succ);
                if (!res.reachable) return;
            } else {
                node.fingerTable.setSuccessor(node.self);
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] aucun backup vivant → anneau réduit à ce nœud");

                // Log rebalance as completed (degenerate case: ring shrank to 1)
                if (rebalanceInProgress) {
                    long duration = System.currentTimeMillis() - rebalanceStartMs;
                    int keysMoved = handler.getStoreSize() - storeBeforeRebalance;
                    handler.logRebalance(duration, Math.max(0, keysMoved), 1);
                    rebalanceInProgress = false;
                }
                return;
            }
        } else if (rebalanceInProgress) {
            // Successor is now reachable again — rebalance has converged
            long duration  = System.currentTimeMillis() - rebalanceStartMs;
            int  keysMoved = handler.getStoreSize() - storeBeforeRebalance;
            // Count live nodes on the ring (approximation via successor list)
            int activeNodes = node.successorList.size() + 1;
            handler.logRebalance(duration, Math.max(0, keysMoved), activeNodes);
            System.out.println("[Stabilize " + node.self.getPort()
                    + "] REBALANCE completed in " + duration + "ms — keys gained: " + keysMoved);
            rebalanceInProgress = false;
            lastKnownSuccessor  = null;
        }

        Address x = res.predecessor;

        if (x != null && !x.equals(node.self)) {
            int xId    = ChordHasher.hash(x);
            int succId = ChordHasher.hash(succ);
            if (ChordHasher.inRange(xId, node.id, succId)) {
                node.fingerTable.setSuccessor(x);
                succ = x;
                System.out.println("[Stabilize " + node.self.getPort()
                        + "] successeur mis à jour -> " + succ);
            }
        }

        handler.sendNotify(succ);
        updateSuccessorList(succ);
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
        synchronized(node){
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

        int start  = (node.id + (1 << nextFinger)) % ChordHasher.RING_SIZE;
        Address target = node.findSuccessor(start);
        node.fingerTable.set(nextFinger, target);

        System.out.println("[FixFingers " + node.self.getPort() + "] finger[" + nextFinger
                + "] start=" + start + " -> " + target);
        nextFinger++;
    }
}
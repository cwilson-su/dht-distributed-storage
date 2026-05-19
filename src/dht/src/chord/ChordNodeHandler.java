package chord;

import dht.Address;
import dht.Message;
import dht.NodeHandler;
import dht.PeerRegistry;
import metrics.MetricsLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ChordNodeHandler extends NodeHandler {

    private static final int RPC_TIMEOUT_MS = 3000;

    private final ChordNode             node;
    final Map<String, String>           store   = new ConcurrentHashMap<>();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();

    public ChordNodeHandler(ChordNode node) {
        super(node.self, new PeerRegistry(node.self), node::nextSeq);
        this.node = node;
    }

    @Override
    public void process(Message m) {
        if (m == null || m.getOrigin() == null) return;
        if (m instanceof ChordMessage cm) handleChord(cm);
        else handleDht(m);
    }

    private void handleChord(ChordMessage cm) {
        switch (cm.getChordType()) {
            case FIND_SUCCESSOR -> {
                Address succ = node.findSuccessor(cm.getTargetId());
                send(cm.getOrigin(), ChordMessage.successorReply(succ, node.self, cm.getSeq()));
            }
            case SUCCESSOR_REPLY -> {
                Pending p = pending.get(cm.getSeq());
                if (p != null) {
                    String val = cm.getValue();
                    p.result = (val != null && !val.isBlank()) ? Address.parse(val) : null;
                    p.latch.countDown();
                }
            }
            case GET_PREDECESSOR ->
                send(cm.getOrigin(), ChordMessage.predecessorReply(node.predecessor, node.self, cm.getSeq()));
            case PREDECESSOR_REPLY -> {
                Pending p = pending.get(cm.getSeq());
                if (p != null) {
                    String val = cm.getValue();
                    p.result = (val == null || val.isBlank()) ? null : Address.parse(val);
                    p.latch.countDown();
                }
            }
            case NOTIFY       -> node.notify(cm.getOrigin());
            case TRANSFER_KEYS -> {
                Map<String,String> received = cm.getData();
                if (received != null && !received.isEmpty()) {
                    store.putAll(received);
                    System.out.println("[Node " + node.self.getPort() + "] reçu "
                            + received.size() + " clé(s) de " + cm.getOrigin());
                }
            }
            case REQUEST_KEYS -> handleRequestKeys(cm);
            case LEAVE_NOTIFY -> handleLeaveNotify(cm);
        }
    }

    private void handleLeaveNotify(ChordMessage cm) {
        Address leaving = cm.getOrigin();
        String  valStr  = cm.getValue();
        Address newSucc = (valStr != null && !valStr.isBlank()) ? Address.parse(valStr) : null;
        if (leaving.equals(node.fingerTable.getSuccessor())) {
            node.fingerTable.setSuccessor(newSucc != null && !newSucc.equals(node.self) ? newSucc : node.self);
        }
        if (leaving.equals(node.predecessor)) node.predecessor = null;
    }

    private void handleRequestKeys(ChordMessage cm) {
        int newNodeId = cm.getTargetId();
        int lowerBound;
        try {
            String val = cm.getValue();
            lowerBound = (val != null && !val.isBlank()) ? Integer.parseInt(val) : node.id;
        } catch (NumberFormatException e) { lowerBound = node.id; }

        Map<String,String> toTransfer = new HashMap<>();
        for (Map.Entry<String,String> entry : store.entrySet()) {
            int keyId = ChordHasher.hash(entry.getKey());
            if (ChordHasher.inRange(keyId, lowerBound, newNodeId))
                toTransfer.put(entry.getKey(), entry.getValue());
        }
        if (!toTransfer.isEmpty()) {
            store.keySet().removeAll(toTransfer.keySet());
            send(cm.getOrigin(), ChordMessage.transferKeys(toTransfer, node.self, node.nextSeq()));
        }
    }

    private void handleDht(Message m) {
        // Interception snapshot — AVANT tout routing Chord
        // La clé spéciale est envoyée en TCP direct par ChordSnapshotClient
        if (m.getKey() != null && m.getKey().startsWith("__snapshot_")) {
            String inner = m.getKey()
                    .replaceFirst("^__snapshot_", "")
                    .replaceAll("__$", "");          // ex: "before_3" ou "after_5"
            String[] parts     = inner.split("_");
            String   phase     = parts.length > 0 ? parts[0] : "before";
            int totalNodes     = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            if ("after".equals(phase)) logSnapshotAfter(totalNodes);
            else                       logSnapshotBefore(totalNodes);
            return;
        }

        switch (m.getType()) {
            case PUT -> {
                Address target = node.lookup(m.getKey());
                if (target.equals(node.self)) {
                    store.put(m.getKey(), m.getValue());
                    MetricsLogger.get().log("PUT_HOP", 0, m.getHops() + 1, m.getKey(), "");
                } else {
                    send(target, m.withLast(node.self));
                }
            }
            case GET -> {
                Address target = node.lookup(m.getKey());
                if (target.equals(node.self)) {
                    String value = store.getOrDefault(m.getKey(), "NOT_FOUND");
                    send(m.getOrigin(), new Message(Message.Type.REPLY, m.getKey(), value,
                            node.self, node.self, m.getSeq(), m.getHops() + 1));
                } else {
                    send(target, m.withLast(node.self));
                }
            }
            case REPLY -> {}
            default    -> {}
        }
    }

    // --- Metrics ---

    public void logSnapshotBefore(int totalNodes) {
        MetricsLogger.get().log("DATA_SKEW_BEFORE", 0, 0, "",
                "node=" + node.id + ";keys_count=" + store.size() + ";active_nodes=" + totalNodes);
        System.out.println("[SNAPSHOT-BEFORE] Node " + node.self.getPort()
                + " (id=" + node.id + ") : " + store.size() + " keys");
    }

    public void logSnapshotAfter(int totalNodes) {
        MetricsLogger.get().log("DATA_SKEW_AFTER", 0, 0, "",
                "node=" + node.id + ";keys_count=" + store.size() + ";active_nodes=" + totalNodes);
        System.out.println("[SNAPSHOT-AFTER] Node " + node.self.getPort()
                + " (id=" + node.id + ") : " + store.size() + " keys");
    }

    public int getStoreSize() { return store.size(); }

    // --- Remote calls ---

    public Address remoteCallFindSuccessor(Address dest, int targetId) {
        int seq = node.nextSeq();
        Pending p = new Pending();
        pending.put(seq, p);
        send(dest, ChordMessage.findSuccessor(targetId, node.self, seq));
        try { p.latch.await(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { pending.remove(seq); }
        return p.result;
    }

    public PredecessorResult remoteCallGetPredecessor(Address dest) {
        int seq = node.nextSeq();
        Pending p = new Pending();
        pending.put(seq, p);
        send(dest, ChordMessage.getPredecessor(node.self, seq));
        try {
            boolean ok = p.latch.await(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok) return PredecessorResult.unreachable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PredecessorResult.unreachable();
        } finally { pending.remove(seq); }
        return PredecessorResult.of(p.result);
    }

    public static class PredecessorResult {
        public final boolean reachable;
        public final Address predecessor;
        private PredecessorResult(boolean r, Address p) { reachable = r; predecessor = p; }
        public static PredecessorResult of(Address p)   { return new PredecessorResult(true, p); }
        public static PredecessorResult unreachable()   { return new PredecessorResult(false, null); }
    }

    public void sendNotify(Address dest) {
        send(dest, ChordMessage.notify(node.self, node.nextSeq()));
    }

    public void sendLeaveNotify(Address dest, Address mySuccessor) {
        send(dest, ChordMessage.leaveNotify(mySuccessor, node.self, node.nextSeq()));
    }

    public void requestKeysFromSuccessor(Address successor, int newNodeId, int predecessorId) {
        send(successor, ChordMessage.requestKeys(newNodeId, predecessorId, node.self, node.nextSeq()));
    }

    public void transferAllKeysTo(Address dest) {
        if (store.isEmpty()) return;
        Map<String,String> all = new HashMap<>(store);
        store.clear();
        send(dest, ChordMessage.transferKeys(all, node.self, node.nextSeq()));
    }

    private static class Pending {
        final CountDownLatch latch  = new CountDownLatch(1);
        volatile Address     result = null;
    }
}
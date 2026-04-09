package chord;

import dht.Address;
import dht.Message;
import dht.NodeHandler;
import dht.PeerRegistry;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class ChordNodeHandler extends NodeHandler {

    private static final int RPC_TIMEOUT_MS = 3000;

    private final ChordNode             node;
    private final Map<String, String>   store   = new ConcurrentHashMap<>();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();

    public ChordNodeHandler(ChordNode node) {
        super(node.self, new PeerRegistry(node.self), node::nextSeq);
        this.node = node;
    }


    @Override
    public void process(Message m) {
        if (m == null || m.getOrigin() == null) return;
        if (m instanceof ChordMessage cm) {
            handleChord(cm);
        } else {
            handleDht(m);
        }
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

            case GET_PREDECESSOR -> {
                send(cm.getOrigin(), ChordMessage.predecessorReply(node.predecessor, node.self, cm.getSeq()));
            }

            case PREDECESSOR_REPLY -> {
                Pending p = pending.get(cm.getSeq());
                if (p != null) {
                    String val = cm.getValue();
                    p.result = (val == null || val.isBlank()) ? null : Address.parse(val);
                    p.latch.countDown();
                }
            }

            case NOTIFY -> node.notify(cm.getOrigin());

            case TRANSFER_KEYS -> handleTransferKeys(cm);
 
            case REQUEST_KEYS -> handleRequestKeys(cm);

            case LEAVE_NOTIFY -> handleLeaveNotify(cm);
        }
    }

    private void handleLeaveNotify(ChordMessage cm) {
        Address leaving   = cm.getOrigin();
        String  valStr    = cm.getValue();
        Address newSucc   = (valStr != null && !valStr.isBlank()) ? Address.parse(valStr) : null;
 
        if (leaving.equals(node.fingerTable.getSuccessor())) {
            if (newSucc != null && !newSucc.equals(node.self)) {
                node.fingerTable.setSuccessor(newSucc);
                System.out.println("[Node " + node.self.getPort()
                        + "] LEAVE_NOTIFY : successeur " + leaving
                        + " est parti → nouveau successeur = " + newSucc);
            } else {
                node.fingerTable.setSuccessor(node.self);
                System.out.println("[Node " + node.self.getPort()
                        + "] LEAVE_NOTIFY : successeur " + leaving
                        + " est parti → anneau à un seul nœud");
            }
        }
 
        if (leaving.equals(node.predecessor)) {
            node.predecessor = null;
            System.out.println("[Node " + node.self.getPort()
                    + "] LEAVE_NOTIFY : prédécesseur " + leaving
                    + " est parti → prédécesseur remis à null");
        }
    }
    
    private void handleTransferKeys(ChordMessage cm) {
        Map<String,String> received = cm.getData();
        if (received == null || received.isEmpty()) return;
 
        store.putAll(received);
        System.out.println("[Node " + node.self.getPort() + "] reçu " + received.size()
                + " clé(s) de " + cm.getOrigin() + " : " + received.keySet());
    }


    private void handleRequestKeys(ChordMessage cm) {
        int newNodeId = cm.getTargetId();
        Map<String,String> toTransfer = new HashMap<>();
 
        for (Map.Entry<String,String> entry : store.entrySet()) {
            int keyId = ChordHasher.hash(entry.getKey());

            if (ChordHasher.inRange(keyId, node.id, newNodeId)) {
                toTransfer.put(entry.getKey(), entry.getValue());
            }
        }
 
        if (!toTransfer.isEmpty()) {
            store.keySet().removeAll(toTransfer.keySet());
            send(cm.getOrigin(), ChordMessage.transferKeys(toTransfer, node.self, node.nextSeq()));
            System.out.println("[Node " + node.self.getPort() + "] transféré " + toTransfer.size()
                    + " clé(s) -> " + cm.getOrigin() + " (id=" + newNodeId + ") : " + toTransfer.keySet());
        }
    }
 

    private void handleDht(Message m) {
        switch (m.getType()) {
            case PUT -> {
                Address target = node.lookup(m.getKey());
                if (target.equals(node.self)) {
                    store.put(m.getKey(), m.getValue());
                    System.out.println(">>> Stocké [" + m.getKey() + "=" + m.getValue()
                            + "] sur nœud " + node.self.getPort() + " (id=" + node.id + ")");
                } else {
                    System.out.println("--- Routage PUT '" + m.getKey() + "' -> " + target);
                    send(target, m.withLast(node.self));
                }
            }
            case GET -> {
                Address target = node.lookup(m.getKey());
                if (target.equals(node.self)) {
                    String value = store.getOrDefault(m.getKey(), "NOT_FOUND");
                    send(m.getOrigin(), new Message(Message.Type.REPLY, m.getKey(), value,
                            node.self, node.self, m.getSeq(), 0));
                } else {
                    System.out.println("--- Routage GET '" + m.getKey() + "' -> " + target);
                    send(target, m.withLast(node.self));
                }
            }
            case REPLY -> System.out.println("<<< REPLY " + m.getKey() + " = " + m.getValue());
            default    -> {}
        }
    }


    
    public Address remoteCallFindSuccessor(Address dest, int targetId) {
        int seq = node.nextSeq();
        Pending p = new Pending();
        pending.put(seq, p);

        send(dest, ChordMessage.findSuccessor(targetId, node.self, seq));

        try {
            p.latch.await(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pending.remove(seq);
        }
        return p.result;
    }

    
    public PredecessorResult remoteCallGetPredecessor(Address dest) {
        int seq = node.nextSeq();
        Pending p = new Pending();
        pending.put(seq, p);

        send(dest, ChordMessage.getPredecessor(node.self, seq));

        try {
            boolean responded = p.latch.await(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!responded){
                return PredecessorResult.unreachable();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PredecessorResult.unreachable();
        } finally {
            pending.remove(seq);
        }
        return PredecessorResult.of(p.result);
    }

    public static class PredecessorResult {
        public final boolean reachable;
        public final Address predecessor; 
 
        private PredecessorResult(boolean reachable, Address predecessor) {
            this.reachable   = reachable;
            this.predecessor = predecessor;
        }
 
        public static PredecessorResult of(Address p) { return new PredecessorResult(true, p); }
        public static PredecessorResult unreachable()  { return new PredecessorResult(false, null); }
    }


    public void sendNotify(Address dest) {
        send(dest, ChordMessage.notify(node.self, node.nextSeq()));
    }

    public void sendLeaveNotify(Address dest, Address mySuccessor) {
        send(dest, ChordMessage.leaveNotify(mySuccessor, node.self, node.nextSeq()));
    }

    private static class Pending {
        final CountDownLatch latch  = new CountDownLatch(1);
        volatile Address     result = null;
    }

     public void requestKeysFromSuccessor(Address successor) {
        send(successor, ChordMessage.requestKeys(node.id, node.self, node.nextSeq()));
        System.out.println("[Node " + node.self.getPort() + "] demande de clés -> " + successor);
    }

    public void transferAllKeysTo(Address dest) {
        if (store.isEmpty()) return;
        Map<String,String> all = new HashMap<>(store);
        store.clear();
        send(dest, ChordMessage.transferKeys(all, node.self, node.nextSeq()));
        System.out.println("[Node " + node.self.getPort() + "] transfert LEAVE : "
                + all.size() + " clé(s) -> " + dest + " : " + all.keySet());
    }
}

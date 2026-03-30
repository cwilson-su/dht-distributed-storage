package chord;

import dht.Address;
import dht.Message;
import dht.NodeHandler;
import dht.PeerRegistry;

import java.util.Map;
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
                    p.result = Address.parse(cm.getValue());
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
        }
    }

    
    private void handleTransferKeys(ChordMessage cm) {
        Map<String,String> received = cm.getData();
        if (received == null || received.isEmpty()) return;
 
        store.putAll(received);
        System.out.println("[Node " + node.self.getPort() + "] reçu " + received.size()
                + " clé(s) de " + cm.getOrigin() + " : " + received.keySet());
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

    
    public Address remoteCallGetPredecessor(Address dest) {
        int seq = node.nextSeq();
        Pending p = new Pending();
        pending.put(seq, p);

        send(dest, ChordMessage.getPredecessor(node.self, seq));

        try {
            p.latch.await(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pending.remove(seq);
        }
        return p.result;
    }


    public void sendNotify(Address dest) {
        send(dest, ChordMessage.notify(node.self, node.nextSeq()));
    }

    private static class Pending {
        final CountDownLatch latch  = new CountDownLatch(1);
        volatile Address     result = null;
    }
}
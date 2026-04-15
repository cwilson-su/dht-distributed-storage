package chord;

import dht.Address;
import dht.Message;
import dht.NodeServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Inspired by dht.Node but uses the finger table for routing.

public class ChordNode {
 
    static final int SUCCESSOR_LIST_SIZE = 2;
    final Address self;
    final int id;
    final FingerTable fingerTable;
 
    volatile Address predecessor = null;
    final List<Address>   successorList = new ArrayList<>();
 
    private final ChordNodeHandler handler;
    private final NodeServer server;
    private final StabilizeService stabilizer;
    private final AtomicInteger seqCounter = new AtomicInteger(0);
    private final Address bootstrapPeer;
 
    public ChordNode(int port, Address... peers) {
        this.self = new Address(port);
        this.id = ChordHasher.hash(self);
        this.fingerTable = new FingerTable(id);
        this.bootstrapPeer = (peers != null && peers.length > 0) ? peers[0] : null;
 
        fingerTable.setSuccessor(self); 
        this.handler = new ChordNodeHandler(this);
        this.server = new NodeServer(port, handler);
        this.stabilizer = new StabilizeService(this, handler);
    }
 
 
    public void start() {
        server.start();
        System.out.println("[ChordNode " + self.getPort() + "] démarré  id=" + id);
 
        if (bootstrapPeer != null) {
            join(bootstrapPeer);
        } else {
            System.out.println("[ChordNode " + self.getPort() + "] nouvel anneau créé");
        }
 
        stabilizer.start();
        registerShutdownHook();
    }
 

    public Address findSuccessor(int targetId) {
        if (targetId == id) {
            return self;
        }
        
        Address succ = fingerTable.getSuccessor();
 
        if (succ != null && ChordHasher.inRange(targetId, id, ChordHasher.hash(succ))) {
            return succ;
        }
 
        Address closest = fingerTable.closestPrecedingFinger(targetId);
        if (closest == null || closest.equals(self)) {
            return succ != null ? succ : self;
        }
 
        return handler.remoteCallFindSuccessor(closest, targetId);
    }
 
    
    public void join(Address bootstrap) {
        System.out.println("[ChordNode " + self.getPort() + "] rejoint via " + bootstrap);
        predecessor = null;
        Address succ = handler.remoteCallFindSuccessor(bootstrap, id);
        if (succ != null) {
            fingerTable.setSuccessor(succ);
            System.out.println("[ChordNode " + self.getPort() + "] successeur = " + succ
                    + " (id=" + ChordHasher.hash(succ) + ")");
 
            if (!succ.equals(self)) {
                ChordNodeHandler.PredecessorResult predResult =
                        handler.remoteCallGetPredecessor(succ);
 
                int lowerBound;
                if (predResult.reachable && predResult.predecessor != null) {
                    lowerBound = ChordHasher.hash(predResult.predecessor);
                    System.out.println("[ChordNode " + self.getPort()
                            + "] borne inférieure = prédécesseur du successeur id=" + lowerBound);
                } else {
                    lowerBound = ChordHasher.hash(succ);
                    System.out.println("[ChordNode " + self.getPort()
                            + "] successeur seul → borne inférieure = id successeur = " + lowerBound);
                }
 
                handler.requestKeysFromSuccessor(succ, id, lowerBound);
            }
        }
    }
   
    public void notify(Address candidate) {
        int candId = ChordHasher.hash(candidate);
        if (predecessor == null
                || ChordHasher.inRange(candId, ChordHasher.hash(predecessor), id)) {
            predecessor = candidate;
            System.out.println("[ChordNode " + self.getPort() + "] prédécesseur = "
                    + candidate + " (id=" + candId + ")");
        }
    }
 
    
    public Address lookup(String key) {
        return findSuccessor(ChordHasher.hash(key));
    }

    synchronized void updateSuccessorList(List<Address> list) {
        successorList.clear();
        successorList.addAll(list);
    }

    synchronized Address getNextLiveSuccessor() {
        for (Address backup : successorList) {
            if (!backup.equals(self) && !backup.equals(fingerTable.getSuccessor())) {
                return backup;
            }
        }
        return null;
    }
 
    private void leave() {
        stabilizer.stop();
        stabilizer.stabilize();
 
        Address succ = fingerTable.getSuccessor();
        Address pred = predecessor;
 
        if (succ != null && !succ.equals(self)) {
            handler.transferAllKeysTo(succ);
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
 
            if (pred != null && !pred.equals(self)) {
                System.out.println("[ChordNode " + self.getPort()
                        + "] LEAVE — notification prédécesseur " + pred
                        + " → nouveau successeur = " + succ);
                handler.sendLeaveNotify(pred, succ);
            }
 
            System.out.println("[ChordNode " + self.getPort()
                    + "] LEAVE — notification successeur " + succ);
            handler.sendLeaveNotify(succ, succ); // value = succ lui-même (ignoré côté succ)
 
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }
 
        server.stop();
        System.out.println("[ChordNode " + self.getPort() + "] arrêté.");
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[ChordNode " + self.getPort() + "] arrêt en cours...");
            leave();
        }));
    }
    
    public int nextSeq() { return seqCounter.incrementAndGet(); }
 
    public void send(Address dest, Message m) { handler.send(dest, m); }
}
 
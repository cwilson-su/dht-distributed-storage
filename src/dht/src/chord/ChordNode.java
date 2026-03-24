package chord;

import dht.Address;
import dht.Message;
import dht.NodeServer;
import java.util.concurrent.atomic.AtomicInteger;

// Inspired by dht.Node but uses the finger table for routing.

public class ChordNode {
 
    final Address self;
    final int id;
    final FingerTable fingerTable;
 
    volatile Address predecessor = null;
 
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
    }
 

    public Address findSuccessor(int targetId) {
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
 
    public int nextSeq() { return seqCounter.incrementAndGet(); }
 
    public void send(Address dest, Message m) { handler.send(dest, m); }
}
 
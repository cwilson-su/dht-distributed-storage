package chord;

import dht.Address;

public class StabilizeService {

    private static final int INTERVAL_MS = 2000;

    private final ChordNode        node;
    private final ChordNodeHandler handler;
    private int nextFinger = 1; 

    public StabilizeService(ChordNode node, ChordNodeHandler handler) {
        this.node    = node;
        this.handler = handler;
    }

    public void start() {
        Thread t = new Thread(this::loop, "Stabilizer-" + node.self.getPort());
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(INTERVAL_MS);
                stabilize();
                fixFingers();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

   
    void stabilize() {
        Address succ = node.fingerTable.getSuccessor();
        if (succ == null || succ.equals(node.self)) return;

        Address x = handler.remoteCallGetPredecessor(succ);

        if (x != null) {
            int xId   = ChordHasher.hash(x);
            int succId = ChordHasher.hash(succ);
            if (ChordHasher.inRange(xId, node.id, succId)) {
                node.fingerTable.setSuccessor(x);
                succ = x;
                System.out.println("[Stabilize " + node.self.getPort() + "] successeur mis à jour -> " + succ);
            }
        }

        handler.sendNotify(succ);
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
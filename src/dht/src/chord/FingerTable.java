package chord;

import dht.Address;

public class FingerTable {
    private final int nodeId;
    private final Address[] fingers;

    public FingerTable(int nodeId) {
        this.nodeId = nodeId;
        this.fingers = new Address[ChordHasher.M];
    }

    public Address get(int i) {
        return fingers[i];
    }

    public void set(int i, Address addr) {
        fingers[i] = addr;
    }

    // The immediate successor is always fingers[0].
    public Address getSuccessor() {
        return fingers[0];
    }

    public void setSuccessor(Address addr) {
        fingers[0] = addr;
    }


    // Returns the closest finger that *precedes* {@code targetId} on the ring.
    public Address closestPrecedingFinger(int targetId) {
        for (int i = ChordHasher.M - 1; i >= 0; i--) {
            Address f = fingers[i];
            if (f == null) continue;

            int fId = ChordHasher.hash(f);
            if (ChordHasher.inRange(fId, nodeId, targetId)) {
                return f;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ChordHasher.M; i++) {
            int start = (nodeId + (1 << i)) % ChordHasher.RING_SIZE;
            sb.append(String.format("  finger[%d] start=%2d -> %s%n",
                    i, start, fingers[i] != null ? fingers[i] + " (id=" + ChordHasher.hash(fingers[i]) + ")" : "null"));
        }
        return sb.toString();
    }

}
package chord;

import dht.Address;
import dht.Message;

public class ChordMessage extends Message {

    private static final long serialVersionUID = 2L;

    public enum ChordType {
        FIND_SUCCESSOR,
        SUCCESSOR_REPLY,
        GET_PREDECESSOR,
        PREDECESSOR_REPLY,
        NOTIFY
    }

    private final ChordType chordType;
    private final int targetId;  

    public ChordMessage(ChordType chordType, int targetId, String key, String value, Address origin, Address last, int seq, int hops) {
        super(Type.REPLY, key, value, origin, last, seq, hops);
        this.chordType = chordType;
        this.targetId  = targetId;
    }

    // Convenience constructors
    public static ChordMessage findSuccessor(int targetId, Address origin, int seq) {
        return new ChordMessage(ChordType.FIND_SUCCESSOR, targetId, "", "", origin, origin, seq, 0);
    }

    public static ChordMessage successorReply(Address successor, Address origin, int seq) {
        return new ChordMessage(ChordType.SUCCESSOR_REPLY, -1,"", successor.toString(), origin, origin, seq, 0);
    }

    public static ChordMessage getPredecessor(Address origin, int seq) {
        return new ChordMessage(ChordType.GET_PREDECESSOR, -1, "", "", origin, origin, seq, 0);
    }

    public static ChordMessage predecessorReply(Address predecessor, Address origin, int seq) {
        String val = (predecessor != null) ? predecessor.toString() : "";

        return new ChordMessage(ChordType.PREDECESSOR_REPLY, -1,"", val, origin, origin, seq, 0);
    }

    public static ChordMessage notify(Address sender, int seq) {
        return new ChordMessage(ChordType.NOTIFY, -1, "", "", sender, sender, seq, 0);
    }


    public ChordType getChordType() {
        return chordType;
    }

    public int getTargetId() {
        return targetId;
    }

    @Override
    public String toString() {
        return "ChordMessage{chordType=" + chordType
                + ", targetId=" + targetId
                + ", origin=" + getOrigin()
                + ", seq=" + getSeq() + "}";
    }
}
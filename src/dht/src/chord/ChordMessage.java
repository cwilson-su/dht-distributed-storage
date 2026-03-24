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

    public ChordMessage(ChordType chordType, int targetId, String value, Address origin, int seq) {
        super(Type.REPLY, "", value, origin, origin, seq, 0);
        this.chordType = chordType;
        this.targetId  = targetId;
    }

    // Convenience constructors
    public static ChordMessage findSuccessor(int targetId, Address origin, int seq) {
        return new ChordMessage(ChordType.FIND_SUCCESSOR, targetId, "", origin, seq);
    }

    public static ChordMessage successorReply(Address successor, Address origin, int seq) {
        return new ChordMessage(ChordType.SUCCESSOR_REPLY, 0, successor.toString(), origin, seq);
    }

    public static ChordMessage getPredecessor(Address origin, int seq) {
        return new ChordMessage(ChordType.GET_PREDECESSOR, 0, "", origin, seq);
    }

    public static ChordMessage predecessorReply(Address predecessor, Address origin, int seq) {
        String val = (predecessor != null) ? predecessor.toString() : "";

        return new ChordMessage(ChordType.PREDECESSOR_REPLY, 0, val, origin, seq);
    }

    public static ChordMessage notify(Address sender, int seq) {
        return new ChordMessage(ChordType.NOTIFY, 0, "", sender, seq);
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
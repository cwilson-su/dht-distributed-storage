package chord;

import java.util.HashMap;
import java.util.Map;

import dht.Address;
import dht.Message;

public class ChordMessage extends Message {

    private static final long serialVersionUID = 2L;

    public enum ChordType {
        FIND_SUCCESSOR,
        SUCCESSOR_REPLY,
        GET_PREDECESSOR,
        PREDECESSOR_REPLY,
        NOTIFY,
        TRANSFER_KEYS,
        REQUEST_KEYS,
        LEAVE_NOTIFY
    }

    private final ChordType chordType;
    private final int targetId;  
    private final Map<String,String> data;

    public ChordMessage(ChordType chordType, int targetId, String value, Address origin, int seq, Map<String,String> data) {
        super(Type.REPLY, "", value, origin, origin, seq, 0);
        this.chordType = chordType;
        this.targetId  = targetId;
        this.data = data;
    }

    // Convenience constructors
    public static ChordMessage findSuccessor(int targetId, Address origin, int seq) {
        return new ChordMessage(ChordType.FIND_SUCCESSOR, targetId, "", origin, seq, null);
    }

    public static ChordMessage successorReply(Address successor, Address origin, int seq) {
        String val = (successor != null) ? successor.toString() : "";
        return new ChordMessage(ChordType.SUCCESSOR_REPLY, 0, val, origin, seq, null);
    }

    public static ChordMessage getPredecessor(Address origin, int seq) {
        return new ChordMessage(ChordType.GET_PREDECESSOR, 0, "", origin, seq, null);
    }

    public static ChordMessage predecessorReply(Address predecessor, Address origin, int seq) {
        String val = (predecessor != null) ? predecessor.toString() : "";

        return new ChordMessage(ChordType.PREDECESSOR_REPLY, 0, val, origin, seq, null);
    }

    public static ChordMessage notify(Address sender, int seq) {
        return new ChordMessage(ChordType.NOTIFY, 0, "", sender, seq, null);
    }

    public static ChordMessage transferKeys(Map<String,String> keys, Address origin, int seq) {
        return new ChordMessage(ChordType.TRANSFER_KEYS, 0, "", origin, seq, new HashMap<>(keys));
    }

     public static ChordMessage requestKeys(int newNodeId, int predecessorId, Address origin, int seq) {
        return new ChordMessage(ChordType.REQUEST_KEYS, newNodeId, String.valueOf(predecessorId), origin, seq, null);
    }

    public static ChordMessage leaveNotify(Address mySuccessor, Address origin, int seq) {
        String val = (mySuccessor != null) ? mySuccessor.toString() : "";
        return new ChordMessage(ChordType.LEAVE_NOTIFY, 0, val, origin, seq, null);
    }

    public ChordType getChordType() {
        return chordType;
    }

    public int getTargetId() {
        return targetId;
    }

    public Map<String,String> getData() { 
        return data; 
    }

    @Override
    public String toString() {
        return "ChordMessage{chordType=" + chordType
                + ", targetId=" + targetId
                + ", origin=" + getOrigin()
                + ", seq=" + getSeq() + "}";
    }
}
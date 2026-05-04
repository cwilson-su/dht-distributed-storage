package dht;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { PUT, GET, REPLY, PING, PONG, JOIN, LEAVE, DELETE }

    private final Type type;
    private final String key;
    private final String value;
    private final Address origin;
    private final Address last;
    private final int seq;
    private final int hops;

    public Message(Type type, String key, String value, Address origin, Address last, int seq, int hops) {
        if (type == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }
        this.type = type;
        this.key = key;
        this.value = value;
        this.origin = origin;
        this.last = last;
        this.seq = seq;
        this.hops = hops;
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Address getOrigin() {
        return origin;
    }

    public Address getLast() {
        return last;
    }

    public int getSeq() {
        return seq;
    }

    public int getHops() {
        return hops;
    }

    public Message withLast(Address newLast) {
        return new Message(type, key, value, origin, newLast, seq, hops + 1);
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", origin=" + origin +
                ", last=" + last +
                ", seq=" + seq +
                ", hops=" + hops +
                '}';
    }
}
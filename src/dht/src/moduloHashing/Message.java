package moduloHashing;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        CLIENT_PUT,
        CLIENT_GET,
        CLIENT_RESPONSE,

        REGISTER_NODE,
        UNREGISTER_NODE,

        NODE_PUT,
        NODE_GET,
        NODE_RESPONSE,

        DUMP_REQUEST,
        DUMP_RESPONSE,
        CLEAR_STORE,

        REBALANCE,
        HEARTBEAT,
        HEARTBEAT_ACK,

        ACK,
        ERROR
    }

    private final Type type;
    private final Address source;
    private final String key;
    private final String value;
    private final boolean found;
    private final String info;
    private final Map<String, String> data;

    public Message(Type type, Address source, String key, String value,
                   boolean found, String info, Map<String, String> data) {
        if (type == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }
        this.type = type;
        this.source = source;
        this.key = key;
        this.value = value;
        this.found = found;
        this.info = info;
        this.data = (data == null) ? null : new HashMap<>(data);
    }

    public Type getType() {
        return type;
    }

    public Address getSource() {
        return source;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public boolean isFound() {
        return found;
    }

    public String getInfo() {
        return info;
    }

    public Map<String, String> getData() {
        return data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }

    // ---------- Factory methods ----------

    public static Message clientPut(String key, String value) {
        return new Message(Type.CLIENT_PUT, null, key, value, false, null, null);
    }

    public static Message clientGet(String key) {
        return new Message(Type.CLIENT_GET, null, key, null, false, null, null);
    }

    public static Message clientResponse(boolean found, String key, String value, String info) {
        return new Message(Type.CLIENT_RESPONSE, null, key, value, found, info, null);
    }

    public static Message registerNode(Address node) {
        return new Message(Type.REGISTER_NODE, node, null, null, false, null, null);
    }

    public static Message unregisterNode(Address node) {
        return new Message(Type.UNREGISTER_NODE, node, null, null, false, null, null);
    }

    public static Message nodePut(String key, String value) {
        return new Message(Type.NODE_PUT, null, key, value, false, null, null);
    }

    public static Message nodeGet(String key) {
        return new Message(Type.NODE_GET, null, key, null, false, null, null);
    }

    public static Message nodeResponse(boolean found, String key, String value) {
        return new Message(Type.NODE_RESPONSE, null, key, value, found, null, null);
    }

    public static Message dumpRequest() {
        return new Message(Type.DUMP_REQUEST, null, null, null, false, null, null);
    }

    public static Message dumpResponse(Address node, Map<String, String> data) {
        return new Message(Type.DUMP_RESPONSE, node, null, null, false, null, data);
    }

    public static Message clearStore() {
        return new Message(Type.CLEAR_STORE, null, null, null, false, null, null);
    }

    public static Message rebalance(String info) {
        return new Message(Type.REBALANCE, null, null, null, false, info, null);
    }

    public static Message heartbeat(Address node) {
        return new Message(Type.HEARTBEAT, node, null, null, false, null, null);
    }

    public static Message heartbeatAck(String info) {
        return new Message(Type.HEARTBEAT_ACK, null, null, null, false, info, null);
    }

    public static Message ack(String info) {
        return new Message(Type.ACK, null, null, null, false, info, null);
    }

    public static Message error(String info) {
        return new Message(Type.ERROR, null, null, null, false, info, null);
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", source=" + source +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", found=" + found +
                ", info='" + info + '\'' +
                ", dataSize=" + (data == null ? 0 : data.size()) +
                '}';
    }
}
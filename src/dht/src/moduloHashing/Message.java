package moduloHashing;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Type {
        CLIENT_PUT,
        CLIENT_GET,
        CLIENT_DELETE,
        CLIENT_RESPONSE,

        REGISTER_NODE,
        UNREGISTER_NODE,

        NODE_PUT,
        NODE_GET,
        NODE_DELETE,
        NODE_RESPONSE,

        DUMP_REQUEST,
        DUMP_RESPONSE,
        CLEAR_STORE,

        REBALANCE,
        REBALANCE_DONE,
        TRANSFER_KEYS,

        HEARTBEAT,
        HEARTBEAT_ACK,

        ACK,
        ERROR
    }

    private final Type               type;
    private final Address            source;
    private final String             key;
    private final String             value;
    private final boolean            found;
    private final String             info;
    private final Map<String,String> data;
    private final int                hopCount;          
    private final Address            transferDest;     
    private final List<String>       transferKeys;     

  
    public Message(Type type, Address source, String key, String value,
                   boolean found, String info, Map<String,String> data,
                   int hopCount, Address transferDest, List<String> transferKeys) {
        if (type == null) throw new IllegalArgumentException("Message type cannot be null");
        this.type         = type;
        this.source       = source;
        this.key          = key;
        this.value        = value;
        this.found        = found;
        this.info         = info;
        this.data         = (data == null) ? null : new HashMap<>(data);
        this.hopCount     = hopCount;
        this.transferDest = transferDest;
        this.transferKeys = (transferKeys == null) ? null : new ArrayList<>(transferKeys);
    }

   
    public Message(Type type, Address source, String key, String value,
                   boolean found, String info, Map<String,String> data) {
        this(type, source, key, value, found, info, data, 0, null, null);
    }

  
    public Type               getType()            { return type;         }
    public Address            getSource()          { return source;       }
    public String             getKey()             { return key;          }
    public String             getValue()           { return value;        }
    public boolean            isFound()            { return found;        }
    public String             getInfo()            { return info;         }
    public int                getHopCount()        { return hopCount;     }
    public Address            getTransferDest()    { return transferDest; }
    public List<String>       getTransferKeys()    {
        return transferKeys == null ? Collections.emptyList()
                                   : Collections.unmodifiableList(transferKeys);
    }
    public Map<String,String> getData() {
        return data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }

  
    public Message withNextHop() {
        return new Message(type, source, key, value, found, info, data,
                hopCount + 1, transferDest, transferKeys);
    }

   
    public static Message clientPut(String key, String value) {
        return new Message(Type.CLIENT_PUT, null, key, value, false, null, null, 1, null, null);
    }

    public static Message clientGet(String key) {
        return new Message(Type.CLIENT_GET, null, key, null, false, null, null, 1, null, null);
    }
    
    public static Message clientDelete(String key) {
        return new Message(Type.CLIENT_DELETE, null, key, null, false, null, null, 1, null, null);
    }
    
    public static Message clientResponse(boolean found, String key, String value,
                                         String info, int hopCount) {
        return new Message(Type.CLIENT_RESPONSE, null, key, value, found, info, null,
                hopCount, null, null);
    }

   
    public static Message clientResponse(boolean found, String key, String value, String info) {
        return clientResponse(found, key, value, info, 0);
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
    
    public static Message nodeDelete(String key) {
        return new Message(Type.NODE_DELETE, null, key, null, false, null, null);
    }

    public static Message nodeResponse(boolean found, String key, String value) {
        return new Message(Type.NODE_RESPONSE, null, key, value, found, null, null);
    }

    public static Message dumpRequest() {
        return new Message(Type.DUMP_REQUEST, null, null, null, false, null, null);
    }

    public static Message dumpResponse(Address node, Map<String,String> data) {
        return new Message(Type.DUMP_RESPONSE, node, null, null, false, null, data);
    }

    public static Message clearStore() {
        return new Message(Type.CLEAR_STORE, null, null, null, false, null, null);
    }

    public static Message rebalance(String info) {
        return new Message(Type.REBALANCE, null, null, null, false, info, null);
    }

    public static Message rebalanceDone() {
        return new Message(Type.REBALANCE_DONE, null, null, null, false, "Rebalance completed", null);
    }

    public static Message transferKeys(Address dest, List<String> keys) {
        return new Message(Type.TRANSFER_KEYS, null, null, null, false,
                "Transfer to " + dest, null, 0, dest, keys);
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
                ", hopCount=" + hopCount +
                ", dataSize=" + (data == null ? 0 : data.size()) +
                '}';
    }
}
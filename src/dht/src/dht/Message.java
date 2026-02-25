package dht;

import java.io.Serializable;

public class Message implements Serializable {
    public enum Type { PUT, GET }
    public Type type;
    public String k, v; // key, value
    public int port;    // sender port
    public int hops = 0;
}

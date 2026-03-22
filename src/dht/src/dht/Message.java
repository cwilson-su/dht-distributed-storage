package dht;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Type { PUT, GET, REPLY, PING, PONG, JOIN, LEAVE }
    public Type type;
    public String k, v; 	// key, value
    
    public Address origin; // original Node that sent the request
    public Address last;   // immediate previous hop
    
    public int seq;     	// sequenceNumber=a counter from the originPort    
    public int hops = 0;	// will be used to calculate TTL later
}

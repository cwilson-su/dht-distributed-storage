package dht;

import java.io.Serializable;

public class Message implements Serializable {
    public enum Type { PUT, GET }
    public Type type;
    public String k, v; 	// key, value
    
    public Address origin; // original Node that sent the request
    public Address last;   // immediate previous hop
    
    public int seq;     	// sequenceNumber=a counter from the originPort    
    public int hops = 0;	// will be used to calculate TTL later
}

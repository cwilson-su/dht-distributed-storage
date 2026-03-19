package dht;

import java.io.Serializable;

public class Message implements Serializable {
    public enum Type { PUT, GET }
    public Type type;
    public String k, v; 	// key, value
    // public int port;    	// sender port - NOT ENOUGH- CUASES BROADCAST STORMS
    
    public int originPort; // Client or Node that started the request
    public int lastPort;   // immediate neighbour who just sent this to us
    public int seq;     	// sequenceNumber=a counter from the originPort
    
    public int hops = 0;	// will be used to calculate TTL later
}

package dht;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Node {
    int p; // port
    Map<String, String> store = new ConcurrentHashMap<>();
    List<Address> peers = new CopyOnWriteArrayList<>();
    Map<Address, Integer> seenSeq = new ConcurrentHashMap<>(); // Tracks the last sequence number seen from each source
    
    static final int MAX_HOPS = 10; // Global TTL

    public Node(int port) { this.p = port; }
    
    public Node(int port, Address... peerAddrs) {
        this.p = port;
        for (Address peer : peerAddrs) peers.add(peer);
    }

    // Start the server thread
    public void start() {
        Thread t = new Thread(this::listen);
        t.start();
        System.out.println("Node " + p + " Started and listening...");
    }

    private void listen() {
        try (ServerSocket ss = new ServerSocket(p)) {
            while (!Thread.currentThread().isInterrupted()) {	// better coding practice than while (true) {PS: lectures ;) }
                try (Socket s = ss.accept();
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                    process((Message) in.readObject());
                } catch (SocketException e) {
                    break; // this here happens if the socket is closed while waiting for a connection
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + p);
        }
        System.out.println("Node " + p + " stopped.");
    }

    private void process(Message m) {    	
        if (seenSeq.getOrDefault(m.origin, -1) >= m.seq) return;	// Drop if we've seen this sequence from this specific Address
        seenSeq.put(m.origin, m.seq);
        
        System.out.println("Node " + p + " processing " + m.type + " for " + m.k);

        switch (m.type) {
        case PUT -> {
            store.put(m.k, m.v);
            System.out.println("Stored [" + m.k + " -> " + m.v + "] at Node " + p);
        }
        case GET -> {
            if (store.containsKey(m.k)) {
                System.out.println(">>> Node " + p + " FOUND IT: " + store.get(m.k));
                
                Message res = new Message();
                res.type = Message.Type.REPLY;
                res.k = m.k;
                res.v = store.get(m.k);
                res.origin = new Address(p);
                res.seq = (int) System.currentTimeMillis();
                               
                send(m.origin, res); 	// Route directly via IP and Port
            } else {
                System.out.println("Node " + p + " key not found, attempting to forward...");
                forward(m);
            }
        }
        case REPLY -> {
            System.out.println("\n<<< SUCCESS: Key '" + m.k + "' -> '" + m.v + "' (from Node " + m.origin.port + ")");
            System.out.print("> "); 
        }
        default -> System.out.println("Unknown type");
    }
	}
      
    private void forward(Message m) {
        if (m.hops < MAX_HOPS) {
            m.hops++;
            Address prev = m.last;
            m.last = new Address(p); 
            
            System.out.println("Node " + p + " forwarding (Hop: " + m.hops + ")");
            for (Address peer : peers) {
                // Ensure we don't route back to the immediate previous Address
                if (!peer.equals(prev)) {
                    send(peer, m); 
                }
            }
        } else {
            System.out.println("Node " + p + ": Max hops reached for " + m.k);
        }
    }
        
    // Direct routing using the underlying network (IP + Port)
    public void send(Address dest, Message m) {
        try (Socket s = new Socket(dest.ip, dest.port);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            out.writeObject(m);
        } catch (Exception e) {
            System.err.println("Failed to route direct message to " + dest);
        }
    }

}

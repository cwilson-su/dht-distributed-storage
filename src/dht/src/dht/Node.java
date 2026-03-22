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
    
    // Tracks the last active timestamp for each peer
    Map<Address, Long> lastSeen = new ConcurrentHashMap<>();

    // ANSI Colour Codes for terminal formatting
    static final String C_RESET = "\u001B[0m";
    static final String C_CYAN = "\u001B[36m";   // for PING
    static final String C_PURPLE = "\u001B[35m"; // for PONG
    static final String C_RED = "\u001B[31m";    // for Disconnects
    
    static final int MAX_HOPS = 10; // Global TTL

    public Node(int port) { this.p = port; }
    
    public Node(int port, Address... peerAddrs) {
        this.p = port;
        for (Address peer : peerAddrs) peers.add(peer);
    }

    // Start the server thread and lifecycle hooks
    public void start() {
        Thread t = new Thread(this::listen);
        t.start();
        System.out.println("Node " + p + " Started and listening...");

        // 1. Announce presence to initial peers (Bootstrap)
        Message jm = new Message();
        jm.type = Message.Type.JOIN;
        jm.origin = new Address(p);
        jm.seq = (int) System.currentTimeMillis();
        for (Address peer : peers) send(peer, jm);

        // 2. Start Heartbeat (PING & Timeout checker)
        Thread ticker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000); // Ping every 5 seconds
                    long now = System.currentTimeMillis();
                    Message ping = new Message();
                    ping.type = Message.Type.PING;
                    ping.origin = new Address(p);
                    ping.seq = (int) System.currentTimeMillis();
                    
                    for (Address peer : peers) {
                        // If no response for 15 seconds, drop the peer
                        if (lastSeen.containsKey(peer) && (now - lastSeen.get(peer) > 15000)) {
                            System.out.println(C_RED + "--- Peer " + peer.port + " TIMED OUT. Dropping." + C_RESET);
                            peers.remove(peer);
                            lastSeen.remove(peer);
                        } else {
                            System.out.println(C_CYAN + "[PING -> " + peer.port + "]" + C_RESET);
                            send(peer, ping);
                        }
                    }
                } catch (InterruptedException e) { break; }
            }
        });
        ticker.setDaemon(true);
        ticker.start();

        // 3. Graceful Shutdown (LEAVE)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nNode " + p + " shutting down. Notifying peers...");
            Message lm = new Message();
            lm.type = Message.Type.LEAVE;
            lm.origin = new Address(p);
            lm.seq = (int) System.currentTimeMillis() + 1;
            for (Address peer : peers) send(peer, lm);
        }));
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
        lastSeen.put(m.origin, System.currentTimeMillis());		// update liveness tracker for any incoming message
        
        if (seenSeq.getOrDefault(m.origin, -1) >= m.seq) return;	// drop if we've seen this sequence from this specific Address
        seenSeq.put(m.origin, m.seq);
        
        // Omit processing prints for heartbeats to keep terminal clean
        if (m.type != Message.Type.PING && m.type != Message.Type.PONG) {
            System.out.println("Node " + p + " processing " + m.type + " for " + m.k);
        } 

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
        case JOIN -> {
            if (!peers.contains(m.origin)) {
                peers.add(m.origin);
                System.out.println("+++ Node " + m.origin.port + " JOINED the network.");
            }
        }
        case LEAVE -> {
            peers.remove(m.origin);
            System.out.println("--- Node " + m.origin.port + " LEFT the network.");
        }
        case PING -> {
            System.out.println(C_CYAN + "[PING <- " + m.origin.port + "]" + C_RESET);
            Message res = new Message();
            res.type = Message.Type.PONG;
            res.origin = new Address(p);
            res.seq = (int) System.currentTimeMillis();
            System.out.println(C_PURPLE + "[PONG -> " + m.origin.port + "]" + C_RESET);
            send(m.origin, res);            
        }
        case PONG -> {
            System.out.println(C_PURPLE + "[PONG <- " + m.origin.port + "]" + C_RESET);
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

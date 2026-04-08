package dht;

import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NodeHandler {
    private final Address self;
    private final PeerRegistry peerRegistry;
    private final IntSupplier nextSeq;

    private final ConcurrentMap<String, String> store = new ConcurrentHashMap<>();

    // ANSI colour codes for console readability
    static final String C_RESET = "\u001B[0m";
    static final String C_CYAN = "\u001B[36m";
    static final String C_PURPLE = "\u001B[35m";
    static final String C_RED = "\u001B[31m";

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int MAX_HOPS = 10;

    public NodeHandler(Address self, PeerRegistry peerRegistry, IntSupplier nextSeq) {
        this.self = self;
        this.peerRegistry = peerRegistry;
        this.nextSeq = nextSeq;
    }

    // Central entry point for all incoming messages
    public void process(Message m) {
        if (m == null || m.getType() == null || m.getOrigin() == null) {
            return;
        }

        // Use last hop when available, otherwise fall back to origin
        Address sender = (m.getLast() != null) ? m.getLast() : m.getOrigin();
        peerRegistry.markAlive(sender);

        // Drop duplicate messages
        if (peerRegistry.alreadySeen(m)) {
            return;
        }

        if (m.getType() != Message.Type.PING && m.getType() != Message.Type.PONG) {
            String target = (m.getKey() == null || m.getKey().isEmpty()) ? "network" : "'" + m.getKey() + "'";
            System.out.println("Node " + self.getPort() + " processing " + m.getType() + " for " + target);
        }

        switch (m.getType()) {
            case PUT -> handlePut(m);
            case GET -> handleGet(m);
            case REPLY -> handleReply(m);
            case JOIN -> handleJoin(m);
            case LEAVE -> handleLeave(m);
            case PING -> handlePing(m);
            case PONG -> handlePong(m);
            case DELETE -> handleDelete(m);
            default -> System.out.println("Unknown type");
        }
    }

    private void handlePut(Message m) {
        store.put(m.getKey(), m.getValue());
        System.out.println(">>> Node " + self.getPort() + " stored [" + m.getKey() + "] locally.");
        
        // Continue flooding if we want other nodes also store it
        // forward(m);
    }

    private void handleGet(Message m) {
        if (store.containsKey(m.getKey())) {
            String val = store.get(m.getKey());
            System.out.println(">>> Node " + self.getPort() + " HIT! Sending reply.");

            Message reply = new Message(
                    Message.Type.REPLY,
                    m.getKey(),
                    val,
                    self,
                    self,
                    m.getSeq(),
                    0
            );
            send(m.getOrigin(), reply);
            // We found it, so we stop flooding here.
        } else {
            System.out.println(">>> Node " + self.getPort() + " MISS. Flooding GET...");
            forward(m);
        }
    }
  
    private void handleReply(Message m) {
        if ("SUCCESSFULLY_DELETED".equals(m.getValue())) {
            System.out.println("\n<<< REPLY: Key '" + m.getKey() + "' was DELETED by Node " + m.getOrigin().getPort());
        } else {
            System.out.println("\n<<< REPLY: Key '" + m.getKey() + "' -> '" + m.getValue()
                    + "' (from Node " + m.getOrigin().getPort() + ")");
        }
        System.out.print("> "); // Reprint the terminal prompt
    }

    private void handleJoin(Message m) {
        boolean added = peerRegistry.addPeer(m.getOrigin());

        if (added) {
            System.out.println("+++ Node " + m.getOrigin().getPort() + " JOINED the network.");

            // Send our own presence back to the new node
            Message selfJoin = new Message(
                    Message.Type.JOIN,
                    "",
                    "",
                    self,
                    self,
                    nextSeq.getAsInt(),
                    0
            );
            send(m.getOrigin(), selfJoin);

            // Re-broadcast JOIN to improve network-view convergence
            Message rebroadcast = m.withLast(self);
            for (Address peer : peerRegistry.getPeersSnapshot()) {
                if (!peer.equals(m.getOrigin()) && !peer.equals(self)) {
                    send(peer, rebroadcast);
                }
            }
        }
    }

    private void handleLeave(Message m) {
        peerRegistry.removePeer(m.getOrigin());
        System.out.println("--- Node " + m.getOrigin().getPort() + " LEFT the network.");

        Message rebroadcast = m.withLast(self);
        for (Address peer : peerRegistry.getPeersSnapshot()) {
            if (!peer.equals(m.getOrigin()) && !peer.equals(self)) {
                send(peer, rebroadcast);
            }
        }
    }

    @Deprecated
    private void handlePingv0(Message m) {
        System.out.println(C_CYAN + "[PING <- " + m.getOrigin().getPort() + "]" + C_RESET);

        Message pong = new Message(
                Message.Type.PONG,
                "",
                "",
                self,
                self,
                nextSeq.getAsInt(),
                0
        );

        System.out.println(C_PURPLE + "[PONG -> " + m.getOrigin().getPort() + "]" + C_RESET);
        send(m.getOrigin(), pong);
    }
    
    private void handlePing(Message m) {
        System.out.println(C_CYAN + "[PING <- " + m.getOrigin().getPort() + "]" + C_RESET); // comment out later to reduce terminal spam

        // Reply for ourselves. We put our Address string in the 'value' field!
        Message selfPong = new Message(
                Message.Type.PONG,
                "",
                self.toString(), 
                self,
                self,
                m.getSeq(),
                0
        );
        send(m.getOrigin(), selfPong);

        // Pong Caching: Send up to 2 known peers to help the sender discover the network
        List<Address> cachedPeers = peerRegistry.getPeersSnapshot();
        int sent = 0;
        
        for (Address cachedPeer : cachedPeers) {
            if (sent >= 2) break; // Limit to 2 cached peers to prevent overloading
            if (cachedPeer.equals(m.getOrigin())) continue; // Don't bounce the sender's own address back to them
            
            Message cachedPong = new Message(
                    Message.Type.PONG,
                    "",
                    cachedPeer.toString(), //<---
                    self, 
                    self,
                    m.getSeq(),
                    0
            );
            send(m.getOrigin(), cachedPong);
            sent++;
        }
    }

    @Deprecated
    private void handlePongv0(Message m) {
        peerRegistry.markAlive(m.getOrigin());
        System.out.println(C_PURPLE + "[PONG <- " + m.getOrigin().getPort() + "]" + C_RESET);
    }
    
    private void handlePong(Message m) {
        Address discoveredPeer = m.getOrigin(); // Default to the node that sent it

        // If the PONG contains a cached address string (e.g, 127.0.0.1:8004), parse it
        if (m.getValue() != null && !m.getValue().isBlank()) {
            try {
                discoveredPeer = Address.parse(m.getValue());
            } catch (Exception e) {
                // Ignore gracefully if the address format is corrupted
            }
        }

        // Attempt to add this peer to our registry
        boolean isNew = peerRegistry.addPeer(discoveredPeer);
        peerRegistry.markAlive(discoveredPeer);
        
        if (isNew) {
            System.out.println(C_PURPLE + "+++ Discovered new peer via PONG cache: " + discoveredPeer.getPort() + C_RESET);
            
            // Say hello to our newly discovered friend!
            Message hello = new Message(Message.Type.JOIN, "", "", self, self, nextSeq.getAsInt(), 0);
            send(discoveredPeer, hello);
        }
    }
    
    private void handleDelete(Message m) {
        if (store.containsKey(m.getKey())) {
            store.remove(m.getKey());
            System.out.println(">>> Node " + self.getPort() + " DELETED [" + m.getKey() + "] locally.");
            
            // Generate a success reply using a specific value flag
            Message reply = new Message(
                    Message.Type.REPLY,
                    m.getKey(),
                    "SUCCESSFULLY_DELETED", // We use the value field to pass the status
                    self,
                    self,
                    m.getSeq(),
                    0
            );
            send(m.getOrigin(), reply); // Send direct TCP reply to the client
            
        } else {
            System.out.println(">>> Node " + self.getPort() + " does not have [" + m.getKey() + "]. Flooding DELETE...");
        }

        // Always forward just in case there are duplicate copies in the network
        forward(m);
    }

    // flooding helper kept for naive experimentation
    public void forward(Message m) {
        if (m.getHops() >= MAX_HOPS) {
            System.out.println("Node " + self.getPort() + ": Max hops reached for " + m.getKey());
            return;
        }

        Address prev = m.getLast();
        Message forwarded = m.withLast(self);

        System.out.println("Node " + self.getPort() + " forwarding (Hop: " + forwarded.getHops() + ")");

        List<Address> peers = peerRegistry.getPeersSnapshot();
        for (Address peer : peers) {
            if (!peer.equals(prev)) {
                send(peer, forwarded);
            }
        }
    }

    // Send a message to a remote node using TCP
    public void send(Address dest, Message m) {
        if (dest == null || m == null) {
            return;
        }

        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), CONNECT_TIMEOUT_MS);

            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(m);
                out.flush();
            }

        } catch (Exception e) {
            System.err.println("Failed to route direct message to " + dest + " : " + e.getMessage());
        }
    }
}
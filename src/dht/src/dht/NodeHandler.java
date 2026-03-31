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

    // ANSI color codes for console readability
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
            System.out.println("Node " + self.getPort() + " processing " + m.getType() + " for " + m.getKey());
        }

        switch (m.getType()) {
            case PUT -> handlePut(m);
            case GET -> handleGet(m);
            case REPLY -> handleReply(m);
            case JOIN -> handleJoin(m);
            case LEAVE -> handleLeave(m);
            case PING -> handlePing(m);
            case PONG -> handlePong(m);
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
        System.out.println("\n<<< REPLY: Key '" + m.getKey() + "' -> '" + m.getValue()
                + "' (from Node " + m.getOrigin().getPort() + ")");
        System.out.print("> ");
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

    private void handlePing(Message m) {
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

    private void handlePong(Message m) {
        peerRegistry.markAlive(m.getOrigin());
        System.out.println(C_PURPLE + "[PONG <- " + m.getOrigin().getPort() + "]" + C_RESET);
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
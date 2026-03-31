#!/bin/bash

echo "Repairing the ID-to-Port mapping..."

# 1. Cleanly update PeerNode.java
cat << 'EOF' > src/node/PeerNode.java
package node;

import core.Address;
import core.INode;
import core.IRouter;
import core.Message;
import net.NodeServer;

import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PeerNode implements INode {
    private final Address self;
    private final IRouter router;
    private final PeerRegistry peers;
    private final NodeServer server;
    private final HeartbeatService beat;
    
    private final AtomicInteger seq = new AtomicInteger(0);
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    private static final String C_RESET = "\u001B[0m";
    private static final String C_CYAN = "\u001B[36m";
    private static final String C_PURPLE = "\u001B[35m";

    public PeerNode(Address addr, IRouter router) {
        this.self = addr;
        this.router = router;
        this.peers = new PeerRegistry(self);
        
        // CRITICAL FIX: The server MUST bind to the actual TCP port, not the ID
        this.server = new NodeServer(self.getPort(), this);
        this.beat = new HeartbeatService(self, peers, this, seq::incrementAndGet);
    }

    @Override
    public void init() {
        server.start();
        beat.start();
        System.out.println("Node " + self.getId() + " initialised.");
    }

    @Override
    public void join(Address entry) {
        if (entry == null || entry.equals(self)) return;
        Message msg = new Message(Message.Type.JOIN, "", "", self, self, seq.incrementAndGet(), 0);
        send(entry, msg);
    }

    @Override
    public void leave() {
        Message msg = new Message(Message.Type.LEAVE, "", "", self, self, seq.incrementAndGet(), 0);
        for (Address peer : peers.getPeersSnapshot()) {
            send(peer, msg);
        }
        server.stop();
        System.out.println("Node " + self.getId() + " left the network.");
    }

    @Override
    public void handleMsg(Message msg) {
        if (msg == null) return;
        
        Address sender = (msg.getLast() != null) ? msg.getLast() : msg.getOrigin();
        peers.markAlive(sender);

        if (peers.alreadySeen(msg)) return;

        switch (msg.getType()) {
            case PING -> {
                System.out.println(C_CYAN + "[PING <- " + msg.getOrigin().getId() + "]" + C_RESET);
                System.out.println(C_PURPLE + "[PONG -> " + msg.getOrigin().getId() + "]" + C_RESET);
                Message pong = new Message(Message.Type.PONG, "", "", self, self, seq.incrementAndGet(), 0);
                send(msg.getOrigin(), pong);
                return;
            }
            case PONG -> {
                System.out.println(C_PURPLE + "[PONG <- " + msg.getOrigin().getId() + "]" + C_RESET);
                return;
            }
            case REPLY -> {
                System.out.println("\n<<< Node " + self.getId() + " received REPLY: '" + msg.getKey() + "' -> '" + msg.getValue() + "' (from Node " + msg.getOrigin().getId() + ")");
                System.out.print("> ");
                return;
            }
            case JOIN -> peers.addPeer(msg.getOrigin());
            case LEAVE -> peers.removePeer(msg.getOrigin());
            default -> {}
        }

        List<Address> nextHops = router.getNext(msg.getKey(), self, peers.getPeersSnapshot());
        
        if (nextHops.contains(self)) {
            if (msg.getType() == Message.Type.PUT) {
                store.put(msg.getKey(), msg.getValue());
                System.out.println(">>> Node " + self.getId() + " stored locally: " + msg.getKey());
            } else if (msg.getType() == Message.Type.GET) {
                if (store.containsKey(msg.getKey())) {
                    System.out.println(">>> Node " + self.getId() + " FOUND IT locally: " + store.get(msg.getKey()));
                    Message reply = new Message(Message.Type.REPLY, msg.getKey(), store.get(msg.getKey()), self, self, msg.getSeq(), 0);
                    send(msg.getOrigin(), reply);
                } else {
                    System.out.println(">>> Node " + self.getId() + " MISS. Flooding GET...");
                }
            }
        }

        Message fwdMsg = msg.withLast(self);
        for (Address dest : nextHops) {
            if (!dest.equals(self) && !dest.equals(sender)) {
                send(dest, fwdMsg);
            }
        }
    }

    @Override
    public void send(Address dest, Message msg) {
        if (dest == null || msg == null) return;

        // CRITICAL FIX: Ensure we connect using the hidden port, not the ID
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), 2000);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public Address getAddr() {
        return self;
    }

    @Override
    public List<Address> getKnownPeers() {
        return peers.getPeersSnapshot();
    }
}
EOF

# 2. Cleanly update HeartbeatService.java
cat << 'EOF' > src/node/HeartbeatService.java
package node;

import core.Address;
import core.INode;
import core.Message;

import java.util.List;
import java.util.function.IntSupplier;

public class HeartbeatService {
    private final Address self;
    private final PeerRegistry peerRegistry;
    private final INode handler;
    private final IntSupplier nextSeq;

    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int PEER_TIMEOUT_MS = 15000;

    private static final String C_RESET = "\u001B[0m";
    private static final String C_CYAN = "\u001B[36m";
    private static final String C_RED = "\u001B[31m";

    public HeartbeatService(Address self, PeerRegistry peerRegistry, INode handler, IntSupplier nextSeq) {
        this.self = self;
        this.peerRegistry = peerRegistry;
        this.handler = handler;
        this.nextSeq = nextSeq;
    }

    public void start() {
        Thread ticker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);

                    Message ping = new Message(Message.Type.PING, "", "", self, self, nextSeq.getAsInt(), 0);

                    for (Address peer : peerRegistry.getPeersSnapshot()) {
                        System.out.println(C_CYAN + "[PING -> " + peer.getId() + "]" + C_RESET);
                        handler.send(peer, ping);
                    }

                    List<Address> timedOut = peerRegistry.collectTimedOutPeers(PEER_TIMEOUT_MS);
                    for (Address dead : timedOut) {
                        System.out.println(C_RED + "--- Node " + dead.getId() + " TIMED OUT. Dropping." + C_RESET);
                        peerRegistry.removePeer(dead);
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        ticker.setDaemon(true);
        ticker.start();
    }
}
EOF

# 3. Cleanly update AsciiPrinter.java
cat << 'EOF' > src/sim/AsciiPrinter.java
package sim;

import core.INode;
import java.util.List;

public class AsciiPrinter {

    public static void print(String type, List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;

        System.out.println("\n--- Topology Map ---");
        switch (type.toLowerCase()) {
            case "line" -> printLine(nodes);
            case "star" -> printStar(nodes);
            case "ring" -> printRing(nodes);
            default -> printRing(nodes);
        }
        System.out.println("--------------------");
    }

    private static void printLine(List<INode> nodes) {
        int n = nodes.size();
        System.out.print("[" + nodes.get(0).getAddr().getId() + "]");
        
        if (n > 2) {
            int mid = n / 2;
            System.out.print(" <--> ... <--> [" + nodes.get(mid).getAddr().getId() + "] <--> ... <--> ");
        } else {
            System.out.print(" <--> ");
        }
        System.out.println("[" + nodes.get(n - 1).getAddr().getId() + "]");
    }

    private static void printStar(List<INode> nodes) {
        int n = nodes.size();
        String p0 = String.valueOf(nodes.get(0).getAddr().getId());
        String p1 = String.valueOf(nodes.get(1).getAddr().getId());
        String p2 = n > 2 ? String.valueOf(nodes.get(2).getAddr().getId()) : "----";
        String p3 = n > 3 ? String.valueOf(nodes.get(3).getAddr().getId()) : "----";
        String p4 = n > 4 ? "..." : "    ";

        System.out.println("        [" + p1 + "]");
        System.out.println("          |");
        System.out.println(" [" + p2 + "]-[-HUB " + p0 + "-]-[" + p3 + "]");
        System.out.println("          |");
        System.out.println("        [" + p4 + "]");
    }

    private static void printRing(List<INode> nodes) {
        int n = nodes.size();
        String p0 = String.valueOf(nodes.get(0).getAddr().getId());
        String p1 = String.valueOf(nodes.get(1).getAddr().getId());
        String last = String.valueOf(nodes.get(n - 1).getAddr().getId());
        
        System.out.println("   +-> [" + p0 + "] <--> [" + p1 + "] --+");
        System.out.println("   |                        |");
        System.out.println("   +- [" + last + "] <--- ... <---+");
    }
}
EOF

echo "Compiling..."
javac -d bin src/*/*.java

if [ $? -eq 0 ]; then
    echo "Fix applied successfully! Launching..."
    java -cp bin sim.Simulator -n 3 -t line -r flooding
else
    echo "Compilation failed."
fi

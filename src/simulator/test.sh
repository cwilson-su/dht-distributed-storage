#!/bin/bash

echo "Updating PeerNode and Simulator..."

# 1. Update PeerNode.java to include PING, PONG, and REPLY logs
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

    // ANSI colours
    private static final String C_RESET = "\u001B[0m";
    private static final String C_CYAN = "\u001B[36m";
    private static final String C_PURPLE = "\u001B[35m";

    public PeerNode(Address addr, IRouter router) {
        this.self = addr;
        this.router = router;
        this.peers = new PeerRegistry(self);
        this.server = new NodeServer(self.getPort(), this);
        this.beat = new HeartbeatService(self, peers, this, seq::incrementAndGet);
    }

    @Override
    public void init() {
        server.start();
        beat.start();
        System.out.println("Node " + self.getPort() + " initialised.");
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
        System.out.println("Node " + self.getPort() + " left the network.");
    }

    @Override
    public void handleMsg(Message msg) {
        if (msg == null) return;
        
        Address sender = (msg.getLast() != null) ? msg.getLast() : msg.getOrigin();
        peers.markAlive(sender);

        if (peers.alreadySeen(msg)) return;

        switch (msg.getType()) {
            case PING -> {
                System.out.println(C_CYAN + "[PING <- " + msg.getOrigin().getPort() + "]" + C_RESET);
                System.out.println(C_PURPLE + "[PONG -> " + msg.getOrigin().getPort() + "]" + C_RESET);
                Message pong = new Message(Message.Type.PONG, "", "", self, self, seq.incrementAndGet(), 0);
                send(msg.getOrigin(), pong);
                return;
            }
            case PONG -> {
                System.out.println(C_PURPLE + "[PONG <- " + msg.getOrigin().getPort() + "]" + C_RESET);
                return;
            }
            case REPLY -> {
                System.out.println("\n<<< Node " + self.getPort() + " received REPLY: '" + msg.getKey() + "' -> '" + msg.getValue() + "' (from Node " + msg.getOrigin().getPort() + ")");
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
                System.out.println(">>> Node " + self.getPort() + " stored locally: " + msg.getKey());
            } else if (msg.getType() == Message.Type.GET) {
                if (store.containsKey(msg.getKey())) {
                    System.out.println(">>> Node " + self.getPort() + " FOUND IT locally: " + store.get(msg.getKey()));
                    Message reply = new Message(Message.Type.REPLY, msg.getKey(), store.get(msg.getKey()), self, self, msg.getSeq(), 0);
                    send(msg.getOrigin(), reply);
                } else {
                    System.out.println(">>> Node " + self.getPort() + " MISS. Flooding GET...");
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
}
EOF

# 2. Update Simulator.java to parse target ports
cat << 'EOF' > src/sim/Simulator.java
package sim;

import core.Address;
import core.INode;
import core.IRouter;
import core.ITopology;
import core.Message;
import node.PeerNode;
import routing.Flooding;
import routing.ModuloHasher;
import topology.LineTopo;
import topology.RingTopo;
import topology.StarTopo;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Simulator {
    public static void main(String[] args) {
        int numNodes = 5;
        String topoType = "line";
        String routeType = "flooding";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n") && i + 1 < args.length) numNodes = Integer.parseInt(args[++i]);
            if (args[i].equals("-t") && i + 1 < args.length) topoType = args[++i].toLowerCase();
            if (args[i].equals("-r") && i + 1 < args.length) routeType = args[++i].toLowerCase();
        }

        System.out.println("=== Initialising Simulator ===");
        System.out.println("Nodes: " + numNodes + " | Topo: " + topoType + " | Route: " + routeType);

        IRouter router = switch (routeType) {
            case "flooding" -> new Flooding();
            default -> new ModuloHasher();
        };

        ITopology topo = switch (topoType) {
            case "line" -> new LineTopo();
            case "star" -> new StarTopo();
            default -> new RingTopo();
        };

        List<INode> nodes = new ArrayList<>();
        int basePort = 8000;

        for (int i = 0; i < numNodes; i++) {
            Address addr = new Address(basePort + i);
            INode n = new PeerNode(addr, router);
            n.init();
            nodes.add(n);
        }

        try { Thread.sleep(500); } catch (Exception ignored) {}

        System.out.println("Building topology...");
        topo.build(nodes);

        try { Thread.sleep(1500); } catch (Exception ignored) {}

        System.out.println("\n=== Network Ready ===");
        System.out.println("Commands: PUT <port> <key> <val> | GET <port> <key> | EXIT");
        
        Scanner sc = new Scanner(System.in);
        AtomicInteger seq = new AtomicInteger(0);

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line.equalsIgnoreCase("EXIT")) break;

            String[] parts = line.split(" ");
            if (parts.length < 3) {
                System.out.println("Usage: PUT <port> <key> <val> OR GET <port> <key>");
                continue;
            }

            String cmd = parts[0].toUpperCase();
            int tgtPort;
            try {
                tgtPort = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number.");
                continue;
            }

            INode tgtNode = null;
            for (INode n : nodes) {
                if (n.getAddr().getPort() == tgtPort) {
                    tgtNode = n;
                    break;
                }
            }

            if (tgtNode == null) {
                System.out.println("Node " + tgtPort + " not found.");
                continue;
            }

            int s = seq.incrementAndGet();

            if (cmd.equals("PUT") && parts.length == 4) {
                Message m = new Message(Message.Type.PUT, parts[2], parts[3], tgtNode.getAddr(), tgtNode.getAddr(), s, 0);
                tgtNode.handleMsg(m);
            } else if (cmd.equals("GET") && parts.length == 3) {
                Message m = new Message(Message.Type.GET, parts[2], "", tgtNode.getAddr(), tgtNode.getAddr(), s, 0);
                tgtNode.handleMsg(m);
            } else {
                System.out.println("Usage: PUT <port> <key> <val> OR GET <port> <key>");
            }
        }

        System.out.println("Shutting down...");
        for (INode n : nodes) n.leave();
        sc.close();
        System.exit(0);
    }
}
EOF

echo "Compiling and launching..."
javac -d bin src/*/*.java

if [ $? -eq 0 ]; then
    java -cp bin sim.Simulator -n 5 -t line -r flooding
else
    echo "Compilation failed."
fi

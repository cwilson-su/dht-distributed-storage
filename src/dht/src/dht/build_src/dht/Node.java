package dht;

import java.util.concurrent.atomic.AtomicInteger;

public class Node {
    private final Address self;
    private final PeerRegistry peerRegistry;
    private final NodeHandler handler;
    private final NodeServer server;
    private final HeartbeatService heartbeat;
    private final AtomicInteger localSeq = new AtomicInteger(0);

    public Node(int port) {
        this.self = new Address(port);
        this.peerRegistry = new PeerRegistry(self);
        this.handler = new NodeHandler(self, peerRegistry, this::nextSeq);
        this.server = new NodeServer(self.getPort(), handler);
        this.heartbeat = new HeartbeatService(self, peerRegistry, handler, this::nextSeq);
    }

    public Node(int port, Address... peerAddrs) {
        this(port);
        if (peerAddrs != null) {
            for (Address peer : peerAddrs) {
                peerRegistry.addPeer(peer);
            }
        }
    }

    public void start() {
        server.start();
        //System.out.println("Node " + self.getPort() + " started and listening...");

        broadcastJoin();
        heartbeat.start();
        registerShutdownHook();
    }

    private void broadcastJoin() {
        Message join = new Message(
                Message.Type.JOIN,
                "",
                "",
                self,
                self,
                nextSeq(),
                0
        );

        for (Address peer : peerRegistry.getPeersSnapshot()) {
            handler.send(peer, join);
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //System.out.println("\nNode " + self.getPort() + " shutting down. Notifying peers...");

            Message leave = new Message(
                    Message.Type.LEAVE,
                    "",
                    "",
                    self,
                    self,
                    nextSeq(),
                    0
            );

            for (Address peer : peerRegistry.getPeersSnapshot()) {
                handler.send(peer, leave);
            }

            server.stop();
        }));
    }

    private int nextSeq() {
        return localSeq.incrementAndGet();
    }

    // Kept for compatibility with the current Client class
    public void send(Address dest, Message m) {
        handler.send(dest, m);
    }
}
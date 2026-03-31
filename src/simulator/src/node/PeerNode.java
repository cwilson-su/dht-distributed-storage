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
        
        // Calculate the TCP port locally based on the ID
        this.server = new NodeServer(8000 + self.getId(), this);
        this.beat = new HeartbeatService(self, peers, this, seq::incrementAndGet);
    }

    @Override
    public void init() {
        server.start();
        beat.start();
        // System.out.println("Node " + self.getId() + " initialised.");
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
        // System.out.println("Node " + self.getId() + " left the network.");
    }

    @Override
    public void handleMsg(Message msg) {
        if (msg == null) return;
        
        Address sender = (msg.getLast() != null) ? msg.getLast() : msg.getOrigin();
        peers.markAlive(sender);

        if (peers.alreadySeen(msg)) return;

        switch (msg.getType()) {
            case PING -> {
                Message pong = new Message(Message.Type.PONG, "", "", self, self, seq.incrementAndGet(), 0);
                send(msg.getOrigin(), pong);
                return;
            }
            case PONG -> { return; }
            case REPLY -> {
                System.out.println("\n<<< Node " + self.getId() + " received REPLY: '" + msg.getKey() + "' -> '" + msg.getValue() + "' (from Node " + msg.getOrigin().getId() + ")");
                // System.out.print("\033[1;32m[SIM] ❯ \033[0m");
                return;
            }
            case JOIN -> peers.addPeer(msg.getOrigin());
            case LEAVE -> peers.removePeer(msg.getOrigin());
            default -> {}
        }

        // We now pass the full message to the router
        List<Address> nextHops = router.getNext(msg, self, peers.getPeersSnapshot());
        
        if (nextHops.contains(self)) {
            if (msg.getType() == Message.Type.PUT) {
                store.put(msg.getKey(), msg.getValue());
                System.out.println(">>> Node " + self.getId() + " stored locally: " + msg.getKey());
            } else if (msg.getType() == Message.Type.GET) {
                if (store.containsKey(msg.getKey())) {
                    System.out.println(">>> Node " + self.getId() + " FOUND IT locally! (" + store.get(msg.getKey()) + ")");
                    Message reply = new Message(Message.Type.REPLY, msg.getKey(), store.get(msg.getKey()), self, self, msg.getSeq(), 0);
                    send(msg.getOrigin(), reply);
                    
                    // CRITICAL FIX: Stop flooding once the data is located
                    return; 
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

        try (Socket s = new Socket()) {
            // Calculate the target TCP port locally based on the destination ID
            s.connect(new InetSocketAddress("127.0.0.1", 8000 + dest.getId()), 2000);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public Address getAddr() { return self; }

    @Override
    public List<Address> getKnownPeers() { return peers.getPeersSnapshot(); }
}

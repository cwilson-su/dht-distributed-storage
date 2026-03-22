package dht;

import java.util.List;
import java.util.function.IntSupplier;

public class HeartbeatService {
    private final Address self;
    private final PeerRegistry peerRegistry;
    private final NodeHandler handler;
    private final IntSupplier nextSeq;

    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private static final int PEER_TIMEOUT_MS = 15000;

    public HeartbeatService(Address self, PeerRegistry peerRegistry, NodeHandler handler, IntSupplier nextSeq) {
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

                    Message ping = new Message(
                            Message.Type.PING,
                            "",
                            "",
                            self,
                            self,
                            nextSeq.getAsInt(),
                            0
                    );

                    for (Address peer : peerRegistry.getPeersSnapshot()) {
                        System.out.println(NodeHandler.C_CYAN + "[PING -> " + peer.getPort() + "]" + NodeHandler.C_RESET);
                        handler.send(peer, ping);
                    }

                    List<Address> timedOut = peerRegistry.collectTimedOutPeers(PEER_TIMEOUT_MS);
                    for (Address dead : timedOut) {
                        System.out.println(NodeHandler.C_RED + "--- Peer " + dead.getPort() + " TIMED OUT. Dropping." + NodeHandler.C_RESET);
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
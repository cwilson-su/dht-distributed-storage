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

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
                    
                      //previous (dumb version; was sending a ping every 5 seconds regardless)
//                    for (Address peer : peerRegistry.getPeersSnapshot()) {
//                        //System.out.println(NodeHandler.C_CYAN + "[PING -> " + peer.getPort() + "]" + NodeHandler.C_RESET);
//                        handler.send(peer, ping);
//                    }
                    
                    long now = System.currentTimeMillis();

                    for (Address peer : peerRegistry.getPeersSnapshot()) {
                        // OPTIMISATION: Check if they have been active recently!
                        long lastActive = peerRegistry.getLastSeen(peer);
                        
                        // Only send a PING if we haven't heard from them in the last 5 seconds
                        if (now - lastActive >= HEARTBEAT_INTERVAL_MS) {
                            //System.out.println(NodeHandler.C_CYAN + "[PING -> " + peer.getPort() + "]" + NodeHandler.C_RESET);
                            handler.send(peer, ping);
                        } else {
                            // Suppressed! They sent us a GET/PUT/etc recently, so we know they are alive.
                            //System.out.println("Suppressing PING to " + peer.getPort() + " (recently active)");
                        }
                    }

                    List<Address> timedOut = peerRegistry.collectTimedOutPeers(PEER_TIMEOUT_MS);
                    for (Address dead : timedOut) {
                        //System.out.println(NodeHandler.C_RED + "--- Peer " + dead.getPort() + " TIMED OUT. Dropping." + NodeHandler.C_RESET);
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
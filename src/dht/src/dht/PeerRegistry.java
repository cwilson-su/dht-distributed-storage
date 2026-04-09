package dht;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PeerRegistry {
    private final Address self;

    private final List<Address> peers = new CopyOnWriteArrayList<>();
    private final Map<Address, Long> lastSeen = new ConcurrentHashMap<>();

    // Store seen message IDs to avoid processing duplicates
    private final Set<String> seenMessages = ConcurrentHashMap.newKeySet();

    public PeerRegistry(Address self) {
        this.self = self;
    }

    public boolean addPeer(Address peer) {
        if (peer == null || peer.equals(self) || peers.contains(peer)) {
            return false;
        }
        peers.add(peer);
        return true;
    }

    public void removePeer(Address peer) {
        peers.remove(peer);
        lastSeen.remove(peer);
    }

    public List<Address> getPeersSnapshot() {
        return new ArrayList<>(peers);
    }

    public void markAlive(Address peer) {
        if (peer != null && !peer.equals(self)) {
            lastSeen.put(peer, System.currentTimeMillis());
        }
    }

    public List<Address> collectTimedOutPeers(long timeoutMs) {
        long now = System.currentTimeMillis();
        List<Address> timedOut = new ArrayList<>();

        for (Address peer : peers) {
            Long ts = lastSeen.get(peer);
            if (ts != null && now - ts > timeoutMs) {
                timedOut.add(peer);
            }
        }
        return timedOut;
    }

    public boolean alreadySeen(Message m) {
        String key = requestKey(m);
        return !seenMessages.add(key);
    }

    private String requestKey(Message m) {
        return m.getType() + "|" + m.getOrigin() + "|" + m.getSeq();
    }
    
    public long getLastSeen(Address peer) {
        return lastSeen.getOrDefault(peer, 0L);
    }
}
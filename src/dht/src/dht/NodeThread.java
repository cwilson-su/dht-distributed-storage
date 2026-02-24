package dht;

import java.util.*;
import java.util.concurrent.*;

public class NodeThread extends Thread implements Node {
    private final long id;
    private final Map<String, String> localStore = new ConcurrentHashMap<>();
    private final List<Node> peers = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Message> mailbox = new LinkedBlockingQueue<>();
    private boolean running = true;

    public NodeThread(long id) {
        this.id = id;
    }

    @Override
    public void run() {
        System.out.println("Node " + id + " started.");
        while (running) {
            try {
                // Wait for a message from another thread
                Message msg = mailbox.poll(1, TimeUnit.SECONDS);
                if (msg != null) {
                    processMessage(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processMessage(Message msg) {
    	String originId = (msg.origin != null) ? String.valueOf(msg.origin.getId()) : "Unknown";
        switch (msg.type) {
            case PUT:
                localStore.put(msg.key, msg.value);
                log("STORED key: '" + msg.key + "' (Origin: Node " + msg.origin.getId() + ")");
                break;

            case GET:
                if (localStore.containsKey(msg.key)) {
                    log("FOUND key: '" + msg.key + "' for Origin: Node " + msg.origin.getId() + " (Total Hops: " + msg.hops + ")");
                } else {
                    log("Key not found. Forwarding to peers...");
                    forwardToPeers(msg);
                }
                break;
        }
    }

    private void forwardToPeers(Message msg) {
        msg.hops++; // Increment hop count before passing it on
        for (Node peer : peers) {
            // Don't send the message back to the person who just gave it to you
            if (peer != msg.origin && peer.getId() != this.id) {
                log("Passing request to Node " + peer.getId());
                peer.receiveMessage(msg);
            }
        }
    }

    public void addPeer(Node peer) {
        if (peer != this) peers.add(peer);
    }

    @Override
    public void receiveMessage(Message msg) {
        mailbox.offer(msg);
    }

    @Override
    public long getId() { return id; }
    
   //Helper function for synchronised prints
    private void log(String text) {
        // This ensures only one thread prints to the console at a time
        synchronized (System.out) {
            System.out.println("[Node " + id + "] " + text);
        }
    }
}

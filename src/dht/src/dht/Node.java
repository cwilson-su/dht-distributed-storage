package dht;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Node {
    int p; // port

    Map<String, String> store = new ConcurrentHashMap<>();
    List<Address> peers = new CopyOnWriteArrayList<>();

    // Tracks the last sequence number seen from each source
    Map<Address, Integer> seenSeq = new ConcurrentHashMap<>();

    // Tracks the last active timestamp for each peer
    Map<Address, Long> lastSeen = new ConcurrentHashMap<>();

    // ANSI Colour Codes for terminal formatting
    static final String C_RESET = "\u001B[0m";
    static final String C_CYAN = "\u001B[36m";   // for PING
    static final String C_PURPLE = "\u001B[35m"; // for PONG
    static final String C_RED = "\u001B[31m";    // for Disconnects

    static final int MAX_HOPS = 10; // Global TTL

    public Node(int port) {
        this.p = port;
    }

    public Node(int port, Address... peerAddrs) {
        this.p = port;
        for (Address peer : peerAddrs) {
            peers.add(peer);
        }
    }

    // Start the server thread and lifecycle hooks
    public void start() {
        Thread t = new Thread(this::listen);
        t.start();
        System.out.println("Node " + p + " Started and listening...");

        // 1. Announce presence to initial peers (Bootstrap)
        Message jm = new Message(
                Message.Type.JOIN,
                "",
                "",
                new Address(p),
                new Address(p),
                (int) System.currentTimeMillis(),
                0
        );

        for (Address peer : peers) {
            send(peer, jm);
        }

        // 2. Start Heartbeat (PING & Timeout checker)
        Thread ticker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000); // Ping every 5 seconds
                    long now = System.currentTimeMillis();

                    Message ping = new Message(
                            Message.Type.PING,
                            "",
                            "",
                            new Address(p),
                            new Address(p),
                            (int) System.currentTimeMillis(),
                            0
                    );

                    for (Address peer : peers) {
                        // If no response for 15 seconds, drop the peer
                        if (lastSeen.containsKey(peer) && (now - lastSeen.get(peer) > 15000)) {
                            System.out.println(C_RED + "--- Peer " + peer.getPort() + " TIMED OUT. Dropping." + C_RESET);
                            peers.remove(peer);
                            lastSeen.remove(peer);
                        } else {
                            System.out.println(C_CYAN + "[PING -> " + peer.getPort() + "]" + C_RESET);
                            send(peer, ping);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        ticker.setDaemon(true);
        ticker.start();

        // 3. Graceful Shutdown (LEAVE)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nNode " + p + " shutting down. Notifying peers...");

            Message lm = new Message(
                    Message.Type.LEAVE,
                    "",
                    "",
                    new Address(p),
                    new Address(p),
                    (int) System.currentTimeMillis() + 1,
                    0
            );

            for (Address peer : peers) {
                send(peer, lm);
            }
        }));
    }

    private void listen() {
        try (ServerSocket ss = new ServerSocket(p)) {
            while (!Thread.currentThread().isInterrupted()) {
                try (
                    Socket s = ss.accept();
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream())
                ) {
                    process((Message) in.readObject());
                } catch (SocketException e) {
                    break;// this here happens if the socket is closed while waiting for a connection
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + p);
        }

        System.out.println("Node " + p + " stopped.");
    }

    private void process(Message m) {
    	// update liveness tracker for any incoming message
        lastSeen.put(m.getOrigin(), System.currentTimeMillis());

        // drop if we've seen this sequence from this specific Address
        if (seenSeq.getOrDefault(m.getOrigin(), -1) >= m.getSeq()) {
            return;
        }
        seenSeq.put(m.getOrigin(), m.getSeq());

        // Omit processing prints for heartbeats to keep terminal clean
        if (m.getType() != Message.Type.PING && m.getType() != Message.Type.PONG) {
            System.out.println("Node " + p + " processing " + m.getType() + " for " + m.getKey());
        }

        switch (m.getType()) {
            case PUT -> {
            	// First, ask the Hasher who should own this data
                Address myAddr = new Address(p);
                Address target = Hasher.getTargetNode(m.getKey(), myAddr, peers);

                if (target.equals(myAddr)) {
                	// It belongs to me! Store it.
                    store.put(m.getKey(), m.getValue());
                    System.out.println(">>> Stored [" + m.getKey() + " -> " + m.getValue() + "] locally at Node " + p);
                } else {
                	// It belongs to someone else. Route it to them!
                    System.out.println("Node " + p + " hashing key '" + m.getKey() + "' -> routing to " + target);
                    send(target, m);
                }
            }

            case GET -> {
            	// First, ask the Hasher who should have this data
                Address myAddr = new Address(p);
                Address target = Hasher.getTargetNode(m.getKey(), myAddr, peers);

                if (target.equals(myAddr)) {
                	 // It should be in my local store
                    if (store.containsKey(m.getKey())) {
                        System.out.println(">>> Node " + p + " FOUND IT locally: " + store.get(m.getKey()));

                        Message res = new Message(
                                Message.Type.REPLY,
                                m.getKey(),
                                store.get(m.getKey()),
                                myAddr,
                                myAddr,
                                (int) System.currentTimeMillis(),
                                0
                        );

                        // Send straight back to the original client/node
                        send(m.getOrigin(), res);
                    } else {
                        System.out.println("Node " + p + " is the target, but key '" + m.getKey() + "' is missing.");
                    }
                } else {
                	// I don't own it. Route the GET request to the correct node
                    System.out.println("Node " + p + " hashing key '" + m.getKey() + "' -> routing GET to " + target);
                    send(target, m);
                }
            }

            case REPLY -> {
                System.out.println("\n<<< SUCCESS: Key '" + m.getKey() + "' -> '" + m.getValue()
                        + "' (from Node " + m.getOrigin().getPort() + ")");
                System.out.print("> ");
            }

            case JOIN -> {
                if (!peers.contains(m.getOrigin())) {
                    peers.add(m.getOrigin());
                    System.out.println("+++ Node " + m.getOrigin().getPort() + " JOINED the network.");
                }
            }

            case LEAVE -> {
                peers.remove(m.getOrigin());
                System.out.println("--- Node " + m.getOrigin().getPort() + " LEFT the network.");
            }

            case PING -> {
                System.out.println(C_CYAN + "[PING <- " + m.getOrigin().getPort() + "]" + C_RESET);

                Message res = new Message(
                        Message.Type.PONG,
                        "",
                        "",
                        new Address(p),
                        new Address(p),
                        (int) System.currentTimeMillis(),
                        0
                );

                System.out.println(C_PURPLE + "[PONG -> " + m.getOrigin().getPort() + "]" + C_RESET);
                send(m.getOrigin(), res);
            }

            case PONG -> {
                System.out.println(C_PURPLE + "[PONG <- " + m.getOrigin().getPort() + "]" + C_RESET);
            }

            default -> System.out.println("Unknown type");
        }
    }

    private void forward(Message m) {
        if (m.getHops() < MAX_HOPS) {
            Address prev = m.getLast();
            Message forwarded = m.withLast(new Address(p));

            System.out.println("Node " + p + " forwarding (Hop: " + forwarded.getHops() + ")");

            for (Address peer : peers) {
            	// Ensure we don't route back to the immediate previous Address
                if (!peer.equals(prev)) {
                    send(peer, forwarded);
                }
            }
        } else {
            System.out.println("Node " + p + ": Max hops reached for " + m.getKey());
        }
    }

    // Direct routing using the underlying network (IP + Port)
    public void send(Address dest, Message m) {
        try (
            Socket s = new Socket(dest.getIp(), dest.getPort());
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())
        ) {
            out.writeObject(m);
        } catch (Exception e) {
            System.err.println("Failed to route direct message to " + dest);
        }
    }
}
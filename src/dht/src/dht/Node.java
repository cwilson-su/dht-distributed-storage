package dht;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Node {
    int p; // port
    Map<String, String> store = new ConcurrentHashMap<>();
    List<Integer> peers = new CopyOnWriteArrayList<>();
    
    static final int MAX_HOPS = 10; // Global TTL

    public Node(int port) { this.p = port; }
    
    public Node(int port, int... peerPorts) {
        this.p = port;
        for (int peer : peerPorts) peers.add(peer);
    }

    // Start the server thread
    public void start() {
        Thread t = new Thread(this::listen);
        t.start();
        System.out.println("Node " + p + " Started and listening...");
    }

    private void listen() {
        try (ServerSocket ss = new ServerSocket(p)) {
            while (!Thread.currentThread().isInterrupted()) {	// better coding practice than while (true) {PS: lectures ;) }
                try (Socket s = ss.accept();
                    ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                    process((Message) in.readObject());
                } catch (SocketException e) {
                    break; // this here happens if the socket is closed while waiting for a connection
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
        System.out.println("Node " + p + " processing " + m.type + " for " + m.k);

        switch (m.type) {
            case PUT -> {
                store.put(m.k, m.v);
                System.out.println("Stored [" + m.k + " -> " + m.v + "] at Node " + p);
            }
            case GET -> {
                if (store.containsKey(m.k)) {
                    System.out.println(">>> Node " + p + " FOUND IT: " + store.get(m.k));
                } else {
                    forward(m);
                }
            }
            default -> System.out.println("Unknown type");
        }
    }

    private void forward(Message m) {
        if (m.hops < MAX_HOPS) {
            System.out.println("Node " + p + " missing key. Forwarding...");
            m.hops++;
            for (int peerPort : peers) send(peerPort, m);
        } else {
            System.out.println("Max hops (" + MAX_HOPS + ") reached at Node " + p);
        }
    }

    public void send(int dest, Message m) {
        try (Socket s = new Socket("localhost", dest);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            out.writeObject(m);
        } catch (Exception e) {}
    }
}

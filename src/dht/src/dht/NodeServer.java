package dht;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class NodeServer {
    private final int port;
    private final NodeHandler handler;

    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private Thread listenerThread;

    public NodeServer(int port, NodeHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() {
        listenerThread = new Thread(this::listen, "Node-Listener-" + port);
        listenerThread.start();
    }

    private void listen() {
        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket s = ss.accept();

                    // One thread per accepted connection
                    Thread worker = new Thread(new ConnectionHandler(s, handler));
                    worker.setName("ConnHandler-" + port + "-" + System.nanoTime());
                    worker.start();

                } catch (SocketException e) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + port + ": " + e.getMessage());
        }

        System.out.println("Node " + port + " stopped.");
    }

    public void stop() {
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }
}
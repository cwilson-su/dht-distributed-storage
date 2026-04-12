package moduloHashing;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Node {
    private final Address self;
    private final Address coordinator;
    private final Map<String, String> store = new ConcurrentHashMap<>();

    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private Thread listenerThread;
    private Thread heartbeatThread;

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;

    public Node(int port, Address coordinator) {
        this.self = new Address(port);
        this.coordinator = coordinator;
    }

    public void start() {
        listenerThread = new Thread(this::listen, "StorageNode-" + self.getPort());
        listenerThread.start();

        System.out.println("Storage node " + self + " started.");
        registerToCoordinator();
        startHeartbeat();
        registerShutdownHook();
    }

    private void registerToCoordinator() {
        Message response = sendRequest(coordinator, Message.registerNode(self));
        if (response != null) {
            System.out.println("[REGISTER] " + response.getType() + " - " + response.getInfo());
        }
    }

    private void unregisterFromCoordinator() {
        Message response = sendRequest(coordinator, Message.unregisterNode(self));
        if (response != null) {
            System.out.println("[UNREGISTER] " + response.getType() + " - " + response.getInfo());
        }
    }

    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    Message response = sendRequest(coordinator, Message.heartbeat(self));
                    if (response != null && response.getType() == Message.Type.HEARTBEAT_ACK) {
                        System.out.println("[HEARTBEAT] ACK from coordinator");
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        }, "NodeHeartbeat-" + self.getPort());

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nNode " + self + " shutting down...");
            try {
                unregisterFromCoordinator();
            } catch (Exception ignored) {
            }
            stop();
        }));
    }

    private void listen() {
        try (ServerSocket ss = new ServerSocket(self.getPort())) {
            this.serverSocket = ss;

            while (running && !Thread.currentThread().isInterrupted()) {
                Socket socket = ss.accept();

                // One thread per accepted connection
                Thread worker = new Thread(() -> handleConnection(socket));
                worker.setName("NodeConn-" + self.getPort() + "-" + System.nanoTime());
                worker.start();
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("Node " + self + " listen error: " + e.getMessage());
            }
        }

        System.out.println("Node " + self + " stopped.");
    }

    private void handleConnection(Socket socket) {
        try (socket;
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.flush();

            ObjectInputFilter filter = info -> {
                Class<?> c = info.serialClass();
                if (c == null) return ObjectInputFilter.Status.UNDECIDED;
                String name = c.getName();
                if (name.startsWith("moduloHashing.") || name.startsWith("java.lang.") || name.startsWith("java.util.")) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                return ObjectInputFilter.Status.REJECTED;
            };
            in.setObjectInputFilter(filter);

            Object obj = in.readObject();
            Message response;

            if (obj instanceof Message msg) {
                response = processRequest(msg);
            } else {
                response = Message.error("Unsupported object type");
            }

            out.writeObject(response);
            out.flush();

        } catch (Exception e) {
            System.err.println("Node " + self + " connection error: " + e.getMessage());
        }
    }

    private Message processRequest(Message msg) {
        if (msg == null || msg.getType() == null) return Message.error("Invalid message");
 
        return switch (msg.getType()) {
            case NODE_PUT      -> handleNodePut(msg);
            case NODE_GET      -> handleNodeGet(msg);
            case NODE_DELETE -> handleNodeDelete(msg);
            case DUMP_REQUEST  -> handleDumpRequest();
            case CLEAR_STORE   -> handleClearStore();
            case REBALANCE     -> handleRebalanceNotice(msg);
            case REBALANCE_DONE -> handleRebalanceDone();
            case TRANSFER_KEYS -> handleTransferKeys(msg);
            default            -> Message.error("Unsupported request on storage node: " + msg.getType());
        };
    }
 
   
 
    private Message handleNodePut(Message msg) {
        if (msg.getKey() == null) return Message.error("NODE_PUT missing key");
        store.put(msg.getKey(), msg.getValue());
        System.out.println("[STORE] " + self + " stored [" + msg.getKey() + " -> " + msg.getValue() + "]");
        return Message.ack("Stored on " + self);
    }
 
    private Message handleNodeGet(Message msg) {
        if (msg.getKey() == null) return Message.error("NODE_GET missing key");
        String value = store.get(msg.getKey());
        boolean found = value != null;
        System.out.println("[READ] " + self + " lookup key=" + msg.getKey() + " found=" + found);
        return Message.nodeResponse(found, msg.getKey(), value);
    }
 
    private Message handleNodeDelete(Message msg) {
        if (msg.getKey() == null) return Message.error("NODE_DELETE missing key");
        boolean existed = store.remove(msg.getKey()) != null;
        System.out.println("[DELETE] " + self + " delete key=" + msg.getKey() + " existed=" + existed);
        return Message.nodeResponse(existed, msg.getKey(), null);
    }
    
    private Message handleDumpRequest() {
        return Message.dumpResponse(self, new HashMap<>(store));
    }
 
    private Message handleClearStore() {
        store.clear();
        System.out.println("[CLEAR] " + self + " local store cleared");
        return Message.ack("Store cleared on " + self);
    }
 
    private Message handleRebalanceNotice(Message msg) {
        System.out.println("[REBALANCE START] " + self + " notified → " + msg.getInfo());
        return Message.ack("Rebalance notice received by " + self);
    }
 
    private Message handleRebalanceDone() {
        System.out.println("[REBALANCE DONE] " + self + " resuming normal operations.");
        return Message.ack("Rebalance done acknowledged by " + self);
    }
    
    private Message handleTransferKeys(Message msg) {
        Address destination = msg.getTransferDest();
        List<String> keys   = msg.getTransferKeys();
 
        if (destination == null || keys == null || keys.isEmpty()) {
            return Message.error("TRANSFER_KEYS: missing destination or keys");
        }
 
        System.out.printf("[TRANSFER] %s → %s : %d keys%n", self, destination, keys.size());
 
        int success = 0;
        int failed  = 0;
 
        for (String key : keys) {
            String value = store.get(key);
            if (value == null) {
                System.err.println("[TRANSFER] Key not found locally: " + key);
                failed++;
                continue;
            }
 
           
            Message putResp = sendRequest(destination, Message.nodePut(key, value));
 
            if (putResp != null && putResp.getType() == Message.Type.ACK) {
                store.remove(key);  
                success++;
            } else {
                System.err.println("[TRANSFER] Failed to push key=" + key + " to " + destination);
                failed++;
            }
        }
 
        System.out.printf("[TRANSFER] Done: %d OK, %d FAILED%n", success, failed);
 
        if (failed == 0) {
            return Message.ack("Transferred " + success + " keys to " + destination);
        } else {
            return Message.error("Transfer partial: " + success + " OK, " + failed + " failed");
        }
    }
    

    private Message sendRequest(Address dest, Message message) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), CONNECT_TIMEOUT_MS);

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.flush();
                out.writeObject(message);
                out.flush();

                Object obj = in.readObject();
                if (obj instanceof Message response) {
                    return response;
                }
                return Message.error("Invalid response type");
            }
        } catch (Exception e) {
            return Message.error("Request to " + dest + " failed: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
        }

        if (listenerThread != null) {
            listenerThread.interrupt();
        }

        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
    }
}
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
    private volatile boolean rebalancing = false;

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
             ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {

            out.flush();

            ObjectInputFilter filter = info -> {
                Class<?> c = info.serialClass();
                if (c == null) return ObjectInputFilter.Status.UNDECIDED;
                String name = c.getName();
                if (name.startsWith("moduloHashing.")
                        || name.startsWith("java.lang.")
                        || name.startsWith("java.util.")
                        || name.startsWith("[")) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                System.err.println("[FILTER REJECTED] " + name);
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
            System.err.println("Node " + self + " connection error: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (Error e) {
           
            System.err.println("Node " + self + " FATAL error: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Message processRequest(Message msg) {
        if (msg == null || msg.getType() == null) return Message.error("Invalid message");
 
        return switch (msg.getType()) {
            case NODE_PUT      -> handleNodePut(msg);
            case NODE_GET      -> handleNodeGet(msg);
            case NODE_DELETE -> handleNodeDelete(msg);
            case NODE_PUT_INTERNAL -> handleNodePutInternal(msg);
            case DUMP_REQUEST  -> handleDumpRequest();
            case CLEAR_STORE   -> handleClearStore();
            case REBALANCE     -> handleRebalanceNotice(msg);
            case REBALANCE_DONE -> handleRebalanceDone();
            case TRANSFER_KEYS -> handleTransferKeys(msg);
            case NODE_BATCH_PUT -> handleNodeBatchPut(msg);
            default            -> Message.error("Unsupported request on storage node: " + msg.getType());
        };
    }
 
   
 
    private Message handleNodePut(Message msg) {
    	if (rebalancing) return Message.rebalancing();
        if (msg.getKey() == null) return Message.error("NODE_PUT missing key");
        store.put(msg.getKey(), msg.getValue());
        System.out.println("[STORE] " + self + " stored [" + msg.getKey() + " -> " + msg.getValue() + "]");
        return Message.ack("Stored on " + self);
    }
 
    private Message handleNodeGet(Message msg) {
    	if (rebalancing) return Message.rebalancing();
        if (msg.getKey() == null) return Message.error("NODE_GET missing key");
        String value = store.get(msg.getKey());
        boolean found = value != null;
        System.out.println("[READ] " + self + " lookup key=" + msg.getKey() + " found=" + found);
        return Message.nodeResponse(found, msg.getKey(), value);
    }
 
    private Message handleNodeDelete(Message msg) {
    	if (rebalancing) return Message.rebalancing();
        if (msg.getKey() == null) return Message.error("NODE_DELETE missing key");
        boolean existed = store.remove(msg.getKey()) != null;
        System.out.println("[DELETE] " + self + " delete key=" + msg.getKey() + " existed=" + existed);
        return Message.nodeResponse(existed, msg.getKey(), null);
    }
    
    private Message handleNodePutInternal(Message msg) {
        if (msg.getKey() == null) return Message.error("NODE_PUT_INTERNAL missing key");
        store.put(msg.getKey(), msg.getValue());
        System.out.println("[TRANSFER-STORE] " + self + " received [" + msg.getKey() + "]");
        return Message.ack("Internal store on " + self);
    }
    
    private Message handleNodeBatchPut(Message msg) {
        Map<String, String> batch = msg.getData();
        if (batch == null || batch.isEmpty()) {
            return Message.error("NODE_BATCH_PUT: empty batch");
        }
        store.putAll(batch);
        System.out.println("[BATCH-STORE] " + self + " received " + batch.size() + " keys");
        return Message.ack("Batch stored " + batch.size() + " keys on " + self);
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
    	System.out.println("[REBALANCE START] " + self + " — blocking client requests.");
        rebalancing = true;
        return Message.ack("Rebalance notice received by " + self);
    }
 
    private Message handleRebalanceDone() {
        System.out.println("[REBALANCE DONE] " + self + " resuming normal operations.");
        rebalancing = false;
        return Message.ack("Rebalance done acknowledged by " + self);
    }
    
    private Message handleTransferKeys(Message msg) {
        Address destination = msg.getTransferDest();
        List<String> keys   = msg.getTransferKeys();

        if (destination == null || keys == null || keys.isEmpty()) {
            return Message.error("TRANSFER_KEYS: missing destination or keys");
        }

        System.out.printf("[TRANSFER] %s → %s : %d keys%n", self, destination, keys.size());

       
        Map<String, String> batch = new HashMap<>();
        for (String key : keys) {
            String value = store.get(key);
            if (value != null) {
                batch.put(key, value);
            } else {
                System.err.println("[TRANSFER] Key not found locally: " + key);
            }
        }

        if (batch.isEmpty()) {
            return Message.error("Transfer: no keys found locally");
        }

        
        Message putResp = sendRequest(destination, Message.nodeBatchPut(batch));

        if (putResp != null && putResp.getType() == Message.Type.ACK) {
            
            batch.keySet().forEach(store::remove);
            System.out.printf("[TRANSFER] Done: %d OK%n", batch.size());
            return Message.ack("Transferred " + batch.size() + " keys to " + destination);
        } else {
            System.err.println("[TRANSFER] Batch failed to " + destination);
            return Message.error("Transfer batch failed");
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
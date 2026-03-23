package moduloHashing;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator {
    private final Address self;
    private final List<Address> storageNodes = new ArrayList<>();
    private final Map<Address, Long> lastSeen = new ConcurrentHashMap<>();

    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private Thread listenerThread;
    private Thread heartbeatMonitorThread;

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int HEARTBEAT_TIMEOUT_MS = 15000;
    private static final int HEARTBEAT_CHECK_INTERVAL_MS = 5000;

    public Coordinator(int port) {
        this.self = new Address(port);
    }

    public void start() {
        listenerThread = new Thread(this::listen, "Coordinator-" + self.getPort());
        listenerThread.start();

        heartbeatMonitorThread = new Thread(this::monitorHeartbeats, "Coordinator-HBMonitor-" + self.getPort());
        heartbeatMonitorThread.setDaemon(true);
        heartbeatMonitorThread.start();

        System.out.println("Coordinator " + self + " started.");
    }

    private void listen() {
        try (ServerSocket ss = new ServerSocket(self.getPort())) {
            this.serverSocket = ss;

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = ss.accept();

                    // One thread per accepted connection
                    Thread worker = new Thread(() -> handleConnection(socket));
                    worker.setName("CoordinatorConn-" + self.getPort() + "-" + System.nanoTime());
                    worker.start();

                } catch (SocketException e) {
                    if (!running) break;
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("Coordinator listen error: " + e.getMessage());
            }
        }

        System.out.println("Coordinator " + self + " stopped.");
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
            System.err.println("Coordinator connection error: " + e.getMessage());
        }
    }

    private synchronized Message processRequest(Message msg) {
        if (msg == null || msg.getType() == null) {
            return Message.error("Invalid message");
        }

        switch (msg.getType()) {
            case REGISTER_NODE -> {
                return handleRegisterNode(msg);
            }
            case UNREGISTER_NODE -> {
                return handleUnregisterNode(msg);
            }
            case HEARTBEAT -> {
                return handleHeartbeat(msg);
            }
            case CLIENT_PUT -> {
                return handleClientPut(msg);
            }
            case CLIENT_GET -> {
                return handleClientGet(msg);
            }
            default -> {
                return Message.error("Unsupported request on coordinator: " + msg.getType());
            }
        }
    }

    private Message handleRegisterNode(Message msg) {
        Address node = msg.getSource();
        if (node == null) {
            return Message.error("REGISTER_NODE missing source");
        }

        if (!storageNodes.contains(node)) {
            storageNodes.add(node);
            lastSeen.put(node, System.currentTimeMillis());
            System.out.println("[JOIN] Node registered: " + node);
            rebalance("Node joined: " + node, Map.of());
        } else {
            lastSeen.put(node, System.currentTimeMillis());
        }

        return Message.ack("Node registered: " + node);
    }

    private Message handleUnregisterNode(Message msg) {
        Address leavingNode = msg.getSource();
        if (leavingNode == null) {
            return Message.error("UNREGISTER_NODE missing source");
        }

        System.out.println("[LEAVE] Node unregister request: " + leavingNode);

        // Try to salvage data from leaving node before removal
        Map<String, String> leavingData = requestDump(leavingNode);

        storageNodes.remove(leavingNode);
        lastSeen.remove(leavingNode);

        rebalance("Node left: " + leavingNode, leavingData);
        return Message.ack("Node unregistered: " + leavingNode);
    }

    private Message handleHeartbeat(Message msg) {
        Address node = msg.getSource();
        if (node == null) {
            return Message.error("HEARTBEAT missing source");
        }

        if (!storageNodes.contains(node)) {
            // If a node sends heartbeat without being registered, re-register it logically
            storageNodes.add(node);
            System.out.println("[HEARTBEAT] Auto-adding unknown node: " + node);
            rebalance("Heartbeat from unknown node, auto-registered: " + node, Map.of());
        }

        lastSeen.put(node, System.currentTimeMillis());
        return Message.heartbeatAck("Heartbeat received from " + node);
    }

    private Message handleClientPut(Message msg) {
        if (msg.getKey() == null) {
            return Message.error("CLIENT_PUT missing key");
        }
        if (storageNodes.isEmpty()) {
            return Message.error("No storage nodes available");
        }

        Address target = Hasher.getTargetNode(msg.getKey(), snapshotNodes());
        Message nodeResp = sendRequest(target, Message.nodePut(msg.getKey(), msg.getValue()));

        if (nodeResp == null || nodeResp.getType() == Message.Type.ERROR) {
            return Message.error("PUT failed on target " + target);
        }

        return Message.clientResponse(true, msg.getKey(), msg.getValue(), "Stored on " + target);
    }

    private Message handleClientGet(Message msg) {
        if (msg.getKey() == null) {
            return Message.error("CLIENT_GET missing key");
        }
        if (storageNodes.isEmpty()) {
            return Message.error("No storage nodes available");
        }

        Address target = Hasher.getTargetNode(msg.getKey(), snapshotNodes());
        Message nodeResp = sendRequest(target, Message.nodeGet(msg.getKey()));

        if (nodeResp == null || nodeResp.getType() == Message.Type.ERROR) {
            return Message.error("GET failed on target " + target);
        }

        if (nodeResp.getType() == Message.Type.NODE_RESPONSE) {
            return Message.clientResponse(nodeResp.isFound(), msg.getKey(), nodeResp.getValue(), "Read from " + target);
        }

        return Message.error("Unexpected response from " + target);
    }

    private void monitorHeartbeats() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(HEARTBEAT_CHECK_INTERVAL_MS);

                List<Address> deadNodes = new ArrayList<>();
                long now = System.currentTimeMillis();

                synchronized (this) {
                    for (Address node : storageNodes) {
                        Long ts = lastSeen.get(node);
                        if (ts != null && now - ts > HEARTBEAT_TIMEOUT_MS) {
                            deadNodes.add(node);
                        }
                    }

                    for (Address dead : deadNodes) {
                        System.out.println("[TIMEOUT] Node considered dead: " + dead);
                        storageNodes.remove(dead);
                        lastSeen.remove(dead);
                        rebalance("Node timeout detected: " + dead, Map.of());
                    }
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void rebalance(String reason, Map<String, String> extraData) {
        if (storageNodes.isEmpty()) {
            System.out.println("[REBALANCE] No nodes available. Rebalance skipped.");
            return;
        }

        System.out.println("[REBALANCE] Starting -> " + reason);

        // Notify nodes that rebalance is happening
        broadcastRebalanceNotice(reason);

        // Gather all current data from active nodes
        Map<String, String> allData = new HashMap<>();
        if (extraData != null) {
            allData.putAll(extraData);
        }

        for (Address node : snapshotNodes()) {
            Map<String, String> dump = requestDump(node);
            allData.putAll(dump);
        }

        // Clear all stores
        for (Address node : snapshotNodes()) {
            sendRequest(node, Message.clearStore());
        }

        // Redistribute every key according to the authoritative global view
        for (Map.Entry<String, String> entry : allData.entrySet()) {
            Address target = Hasher.getTargetNode(entry.getKey(), snapshotNodes());
            sendRequest(target, Message.nodePut(entry.getKey(), entry.getValue()));
        }

        System.out.println("[REBALANCE] Completed. Total keys redistributed = " + allData.size());
    }

    private void broadcastRebalanceNotice(String reason) {
        for (Address node : snapshotNodes()) {
            sendRequest(node, Message.rebalance(reason));
        }
    }

    private Map<String, String> requestDump(Address node) {
        Message resp = sendRequest(node, Message.dumpRequest());
        if (resp != null && resp.getType() == Message.Type.DUMP_RESPONSE) {
            return resp.getData();
        }
        return new HashMap<>();
    }

    private List<Address> snapshotNodes() {
        return new ArrayList<>(storageNodes);
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

        if (heartbeatMonitorThread != null) {
            heartbeatMonitorThread.interrupt();
        }
    }
}
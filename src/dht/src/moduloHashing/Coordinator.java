package moduloHashing;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import metrics.MetricsLogger;

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
            MetricsLogger.get().log("JOIN", 0, 0, "", "node=" + node);
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
        MetricsLogger.get().log("LEAVE", 0, 0, "", "node=" + leavingNode);

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
        if (msg.getKey() == null) return Message.error("CLIENT_PUT missing key");
        if (storageNodes.isEmpty()) return Message.error("No storage nodes available");

        Address target = Hasher.getTargetNode(msg.getKey(), snapshotNodes());

        
        Message nodeMsg  = Message.nodePut(msg.getKey(), msg.getValue()).withNextHop();
        Message nodeResp = sendRequest(target, nodeMsg);
        // hop 3 : node → coordinator  
        if (nodeResp == null || nodeResp.getType() == Message.Type.ERROR) {
            return Message.error("PUT failed on target " + target);
        }

        // hop 4 : coordinator → client 
        int finalHops = nodeMsg.getHopCount() + 1 + 1; // coord→node(2) + ack node(3) 
        return Message.clientResponse(true, msg.getKey(), msg.getValue(),
                "Stored on " + target, finalHops);
    }

    private Message handleClientGet(Message msg) {
        if (msg.getKey() == null) return Message.error("CLIENT_GET missing key");
        if (storageNodes.isEmpty()) return Message.error("No storage nodes available");

        Address target = Hasher.getTargetNode(msg.getKey(), snapshotNodes());

        // hop 2 : coordinator → node
        Message nodeMsg  = Message.nodeGet(msg.getKey()).withNextHop();
        Message nodeResp = sendRequest(target, nodeMsg);
        // hop 3 : node → coordinator

        if (nodeResp == null || nodeResp.getType() == Message.Type.ERROR) {
            return Message.error("GET failed on target " + target);
        }

        if (nodeResp.getType() == Message.Type.NODE_RESPONSE) {
            // hop 4 : coordinator → client
            int finalHops = nodeMsg.getHopCount() + 1 + 1;
            return Message.clientResponse(nodeResp.isFound(), msg.getKey(),
                    nodeResp.getValue(), "Read from " + target, finalHops);
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
    	List<Address> nodes = snapshotNodes();
    	
    	if (nodes.isEmpty()) {
            System.out.println("[REBALANCE] No nodes available. Rebalance skipped.");
            return;
        }

        System.out.println("[REBALANCE] Starting (node-to-node) -> " + reason);

        long rebalanceStart = System.currentTimeMillis();
        
        broadcastMessage(nodes, Message.rebalance(reason));
 
        Map<Address, List<String>> keysByNode = new HashMap<>();
        
        if (extraData != null && !extraData.isEmpty()) {
            for (Map.Entry<String, String> entry : extraData.entrySet()) {
                Address dest = Hasher.getTargetNode(entry.getKey(), nodes);
                sendRequest(dest, Message.nodePut(entry.getKey(), entry.getValue()));
            }
            System.out.println("[REBALANCE] Injected " + extraData.size() + " orphan keys directly to targets.");
        }
 
        for (Address node : nodes) {
            Message dumpResp = sendRequest(node, Message.dumpRequest());
            if (dumpResp != null && dumpResp.getType() == Message.Type.DUMP_RESPONSE) {
                keysByNode.put(node, new ArrayList<>(dumpResp.getData().keySet()));
            } else {
                keysByNode.put(node, Collections.emptyList());
            }
        }
 
        Map<Address, Map<Address, List<String>>> migrationPlan = new HashMap<>();
        int keysToMove = 0;
 
        for (Map.Entry<Address, List<String>> entry : keysByNode.entrySet()) {
            Address source = entry.getKey();
            for (String key : entry.getValue()) {
                Address correctDest = Hasher.getTargetNode(key, nodes);
                if (!correctDest.equals(source)) {
                    migrationPlan.computeIfAbsent(source, k -> new HashMap<>()).computeIfAbsent(correctDest, k -> new ArrayList<>()).add(key);
                    keysToMove++;
                }
            }
        }
 
        if (keysToMove == 0) {
            System.out.println("[REBALANCE] Nothing to move. Already balanced.");
            broadcastMessage(nodes, Message.rebalanceDone());
            return;
        }
 
        System.out.println("[REBALANCE] Plan: " + keysToMove + " keys to move across "
                + migrationPlan.size() + " source nodes.");
 
        
        int successfulTransfers = 0;
 
        for (Map.Entry<Address, Map<Address, List<String>>> sourceEntry : migrationPlan.entrySet()) {
            Address source = sourceEntry.getKey();
 
            for (Map.Entry<Address, List<String>> destEntry : sourceEntry.getValue().entrySet()) {
                Address      dest = destEntry.getKey();
                List<String> keys = destEntry.getValue();
 
                Message transferMsg = Message.transferKeys(dest, keys);
                Message ack         = sendRequest(source, transferMsg);
 
                if (ack != null && ack.getType() == Message.Type.ACK) {
                    successfulTransfers += keys.size();
                    System.out.printf("[REBALANCE] %s → %s : %d keys transferred OK%n",
                            source, dest, keys.size());
                } else {
                    System.err.printf("[REBALANCE] FAILED transfer %s → %s : %s%n",
                            source, dest, ack != null ? ack.getInfo() : "null response");
                }
            }
        }
 
        
        broadcastMessage(nodes, Message.rebalanceDone());
 
        long elapsed = System.currentTimeMillis() - rebalanceStart;
        System.out.printf("[REBALANCE] Completed in %d ms. Keys moved: %d/%d%n",
                elapsed, successfulTransfers, keysToMove);
    }

    private void broadcastMessage(List<Address> nodes, Message msg) {
        for (Address node : nodes) {
            sendRequest(node, msg);
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
            } catch (Exception e) {
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
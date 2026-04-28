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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import metrics.MetricsLogger;

public class Coordinator {
    private final Address self;
    private final List<Address> storageNodes = new ArrayList<>();
    private final Map<Address, Long> lastSeen = new ConcurrentHashMap<>();
    private final java.util.Set<Address> recentlyLeft = ConcurrentHashMap.newKeySet();

    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private Thread listenerThread;
    private Thread heartbeatMonitorThread;
    private final ExecutorService workerPool = Executors.newFixedThreadPool(20);

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
                    workerPool.submit(() -> handleConnection(socket));

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
            System.err.println("Coordinator connection error: " + e.getMessage());
        }
    }

    private Message processRequest(Message msg) {
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
            case CLIENT_DELETE -> {
                return handleClientDelete(msg);
            }
            case SNAPSHOT_BEFORE -> {
                return handleSnapshotBefore();
            }
            default -> {
                return Message.error("Unsupported request on coordinator: " + msg.getType());
            }
        }
    }
    
    private Message handleSnapshotBefore() {
        List<Address> nodes;
        synchronized (this) { nodes = snapshotNodes(); }
        for (Address node : nodes) {
            Message dumpResp = sendRequest(node, Message.dumpRequest());
            if (dumpResp != null && dumpResp.getType() == Message.Type.DUMP_RESPONSE) {
                int size = dumpResp.getData().size();
                MetricsLogger.get().log("DATA_SKEW_BEFORE", 0, 0, "",
                    "node=" + node.getPort() + ";keys_count=" + size);
            }
        }
        System.out.println("[SNAPSHOT] DATA_SKEW_BEFORE logged for all nodes.");
        return Message.ack("Snapshot taken");
    }

    private Message handleRegisterNode(Message msg) {
        Address node = msg.getSource();
        if (node == null) return Message.error("REGISTER_NODE missing source");

        boolean shouldRebalance = false;
        synchronized (this) {
            if (!storageNodes.contains(node)) {
                storageNodes.add(node);
                lastSeen.put(node, System.currentTimeMillis());
                recentlyLeft.remove(node);
                System.out.println("[JOIN] Node registered: " + node);
                MetricsLogger.get().log("JOIN", 0, 0, "", "node=" + node);
                shouldRebalance = true;
            } else {
                lastSeen.put(node, System.currentTimeMillis());
            }
        }
        if (shouldRebalance) rebalance("Node joined: " + node, Map.of());
        return Message.ack("Node registered: " + node);
    }

    private Message handleUnregisterNode(Message msg) {
        Address leavingNode = msg.getSource();
        if (leavingNode == null) return Message.error("UNREGISTER_NODE missing source");

        System.out.println("[LEAVE] Node unregister request: " + leavingNode);
        MetricsLogger.get().log("LEAVE", 0, 0, "", "node=" + leavingNode);

        Map<String, String> leavingData = requestDump(leavingNode);

        synchronized (this) {
            storageNodes.remove(leavingNode);
            lastSeen.remove(leavingNode);
        }
        recentlyLeft.add(leavingNode);
        rebalance("Node left: " + leavingNode, leavingData);
        return Message.ack("Node unregistered: " + leavingNode);
    }

    private Message handleHeartbeat(Message msg) {
        Address node = msg.getSource();
        if (node == null) return Message.error("HEARTBEAT missing source");

        synchronized (this) {
            if (!storageNodes.contains(node)) {
                if (recentlyLeft.contains(node)) {
                    lastSeen.put(node, System.currentTimeMillis());
                    return Message.heartbeatAck("Heartbeat received from " + node);
                }
                storageNodes.add(node);
                System.out.println("[HEARTBEAT] Auto-adding unknown node (no rebalance): " + node);
            }
            lastSeen.put(node, System.currentTimeMillis());
        }
        return Message.heartbeatAck("Heartbeat received from " + node);
    }

    private Message handleClientPut(Message msg) {
        if (msg.getKey() == null) return Message.error("CLIENT_PUT missing key");

        Address target;
        synchronized (this) {
            if (storageNodes.isEmpty()) return Message.error("No storage nodes available");
            target = Hasher.getTargetNode(msg.getKey(), snapshotNodes());
        }

        Message nodeMsg = new Message(Message.Type.NODE_PUT, null,msg.getKey(), msg.getValue(), false, null, null, msg.getHopCount() + 1, null, null);
        Message nodeResp = sendRequest(target, nodeMsg);

        if (nodeResp == null || nodeResp.getType() == Message.Type.ERROR)
            return Message.error("PUT failed on target " + target);

        if (nodeResp.getType() == Message.Type.REBALANCING)
            return Message.rebalancing();

        int finalHops = nodeMsg.getHopCount() + 1 + 1;
        return Message.clientResponse(true, msg.getKey(), msg.getValue(),
                "Stored on " + target, finalHops);
    }

    private Message handleClientGet(Message msg) {
        if (msg.getKey() == null) return Message.error("CLIENT_GET missing key");

        Address target;
        synchronized (this) {
            if (storageNodes.isEmpty()) return Message.error("No storage nodes available");
            target = Hasher.getTargetNode(msg.getKey(), snapshotNodes());
        }

        Message nodeMsg = new Message(Message.Type.NODE_GET, null,msg.getKey(), null, false, null, null, msg.getHopCount() + 1, null, null);
        Message nodeResp = sendRequest(target, nodeMsg);

        if (nodeResp == null || nodeResp.getType() == Message.Type.ERROR)
            return Message.error("GET failed on target " + target);

        if (nodeResp.getType() == Message.Type.REBALANCING)
            return Message.rebalancing();

        if (nodeResp.getType() == Message.Type.NODE_RESPONSE) {
            int finalHops = nodeMsg.getHopCount() + 1 + 1;
            return Message.clientResponse(nodeResp.isFound(), msg.getKey(),
                    nodeResp.getValue(), "Read from " + target, finalHops);
        }
        return Message.error("Unexpected response from " + target);
    }
    
    private Message handleClientDelete(Message msg) {
        if (msg.getKey() == null) return Message.error("CLIENT_DELETE missing key");

        Address target;
        synchronized (this) {
            if (storageNodes.isEmpty()) return Message.error("No storage nodes available");
            target = Hasher.getTargetNode(msg.getKey(), snapshotNodes());
        }

        Message nodeMsg = new Message(Message.Type.NODE_DELETE, null,msg.getKey(), null, false, null, null, msg.getHopCount() + 1, null, null);
        Message nodeResp = sendRequest(target, nodeMsg);

        if (nodeResp == null || nodeResp.getType() == Message.Type.ERROR)
            return Message.error("DELETE failed on target " + target);

        if (nodeResp.getType() == Message.Type.REBALANCING)
            return Message.rebalancing();

        if (nodeResp.getType() == Message.Type.NODE_RESPONSE) {
            int finalHops = nodeMsg.getHopCount() + 1 + 1;
            boolean existed = nodeResp.isFound();
            String info = existed ? "Deleted from " + target : "Key not found on " + target;
            return Message.clientResponse(existed, msg.getKey(), null, info, finalHops);
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
                        recentlyLeft.remove(dead);
                    }
                }
                for (Address dead : deadNodes) {
                    MetricsLogger.get().log("NODE_TIMEOUT", 0, 0, "", "node=" + dead);
                    rebalance("Node timeout detected: " + dead, Map.of());
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void rebalance(String reason, Map<String, String> extraData) {
    	List<Address> nodes;
        synchronized (this) {
            nodes = snapshotNodes();
        }

        if (nodes.isEmpty()) {
            System.out.println("[REBALANCE] No nodes available. Rebalance skipped.");
            return;
        }

        System.out.println("[REBALANCE] Starting (node-to-node) -> " + reason);
        long rebalanceStart = System.currentTimeMillis();

       
        if (extraData != null && !extraData.isEmpty()) {
            for (Map.Entry<String, String> entry : extraData.entrySet()) {
                Address dest = Hasher.getTargetNode(entry.getKey(), nodes);
                sendRequest(dest, Message.nodePutInternal(entry.getKey(), entry.getValue()));
            }
            System.out.println("[REBALANCE] Injected " + extraData.size() + " orphan keys directly to targets.");
        }

      
        Map<Address, List<String>> keysByNode = new HashMap<>();
        for (Address node : nodes) {
            Message dumpResp = sendRequest(node, Message.dumpRequest());
            if (dumpResp != null && dumpResp.getType() == Message.Type.DUMP_RESPONSE) {
                List<String> keys = new ArrayList<>(dumpResp.getData().keySet());
                keysByNode.put(node, keys);
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
                    migrationPlan
                        .computeIfAbsent(source, k -> new HashMap<>())
                        .computeIfAbsent(correctDest, k -> new ArrayList<>())
                        .add(key);
                    keysToMove++;
                }
            }
        }

        if (keysToMove == 0) {
            System.out.println("[REBALANCE] Nothing to move. Already balanced.");
            MetricsLogger.get().log("REBALANCE", System.currentTimeMillis() - rebalanceStart, 0, "", "keys_moved=0");
            return;
        }

      
        broadcastMessage(nodes, Message.rebalance(reason));

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
                    
                    
                    int estimatedBytes = keys.size() * 64; 
                    MetricsLogger.get().log("TRANSFER_BATCH", 0, 1, "", 
                        "source=" + source.getPort() + ";dest=" + dest.getPort() + ";keys=" + keys.size() + ";bytes=" + estimatedBytes);
                } else {
                    System.err.printf("[REBALANCE] FAILED transfer %s → %s : %s%n",
                            source, dest, ack != null ? ack.getInfo() : "null response");
                }
            }
        }

        broadcastMessage(nodes, Message.rebalanceDone());
        
        for (Address node : nodes) {
            Message finalDump = sendRequest(node, Message.dumpRequest());
            if (finalDump != null && finalDump.getType() == Message.Type.DUMP_RESPONSE) {
                MetricsLogger.get().log("DATA_SKEW_AFTER", 0, 0, "",
                    "node=" + node.getPort() + ";keys_count=" + finalDump.getData().size()
                    + ";active_nodes=" + nodes.size());
            } else {
                MetricsLogger.get().log("DATA_SKEW_AFTER", 0, 0, "",
                    "node=" + node.getPort() + ";keys_count=0;active_nodes=" + nodes.size());
            }
        }
        
        long elapsed = System.currentTimeMillis() - rebalanceStart;
        System.out.printf("[REBALANCE] Completed in %d ms. Keys moved: %d/%d%n", elapsed, successfulTransfers, keysToMove);
        
        
        MetricsLogger.get().log("REBALANCE", elapsed, 0, "", "keys_moved=" + successfulTransfers + ";active_nodes=" + nodes.size());
    
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
        if (serverSocket != null && !serverSocket.isClosed()) { try { serverSocket.close(); } catch (Exception ignored) {} }
        if (listenerThread != null) listenerThread.interrupt();
        if (heartbeatMonitorThread != null) heartbeatMonitorThread.interrupt();
        
        if (workerPool != null && !workerPool.isShutdown()) {
            workerPool.shutdown();
        }
    }
}
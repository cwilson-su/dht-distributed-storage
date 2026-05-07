package chord;

import dht.Address;
import dht.Message;
import dht.NodeHandler;
import dht.PeerRegistry;
import metrics.MetricsLogger;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChordLoadClient — Phase 1 stress test for Chord.
 *
 * Usage: java chord.ChordLoadClient <nodeIP:port> <numClients> <reqPerClient>
 *
 * Sends concurrent PUT requests directly to a Chord node and logs:
 *   - latency_ms  (end-to-end round-trip, measured here at client side)
 *   - hop_count   (from the REPLY message's hops field — set by ChordNodeHandler)
 *   - extra       clients=N  (to match the grouping used by plot_metrics.py)
 */
public class ChordLoadClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REPLY_TIMEOUT_MS   = 10_000;

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 3) {
            System.out.println("Usage: java chord.ChordLoadClient <nodeIP:port> <numClients> <reqPerClient>");
            return;
        }

        MetricsLogger.configure("results/chord_metrics.csv");

        Address entryNode   = Address.parse(args[0]);
        int     numClients  = Integer.parseInt(args[1]);
        int     reqPerClient = Integer.parseInt(args[2]);

        System.out.println("--- Chord Load Benchmark ---");
        System.out.printf("Entry node: %s | Concurrent clients: %d | Requests/client: %d%n",
                entryNode, numClients, reqPerClient);

        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        long globalStart = System.currentTimeMillis();

        AtomicInteger seqBase = new AtomicInteger(0);

        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            pool.submit(() -> {
                // Each thread gets its own ephemeral listening port for replies
                int replyPort = 20000 + (int)(Math.random() * 30000);
                Address clientAddr = null;
                try { clientAddr = new Address(replyPort); } catch (Exception e) {
                    replyPort = 20000 + (int)(Math.random() * 30000);
                    clientAddr = new Address(replyPort);
                }
                final Address myAddr = clientAddr;

                for (int r = 0; r < reqPerClient; r++) {
                    String key = "chordKey_" + clientId + "_" + r;
                    String val = UUID.randomUUID().toString().substring(0, 16);
                    int seq = seqBase.incrementAndGet();

                    Message putMsg = new Message(
                            Message.Type.PUT, key, val,
                            myAddr, myAddr, seq, 0);

                    long t0 = System.nanoTime();
                    // For Chord, PUT is fire-and-forget (the node routes it internally).
                    // We send and don't wait for a reply — just measure dispatch latency.
                    // To also capture hop counts we piggyback a GET immediately after.
                    sendFireAndForget(entryNode, putMsg);
                    long putLatencyMs = (System.nanoTime() - t0) / 1_000_000L;

                    // Log PUT latency; hop count captured on the node side (PUT_HOP rows)
                    // We store clients= in extra so plot_metrics.py can group by load level.
                    MetricsLogger.get().log("PUT", putLatencyMs, 0, key,
                            "clients=" + numClients);

                    // GET to measure routing hops end-to-end
                    int getSeq = seqBase.incrementAndGet();
                    Message getMsg = new Message(
                            Message.Type.GET, key, "",
                            myAddr, myAddr, getSeq, 0);

                    long tg0 = System.nanoTime();
                    Message reply = sendAndReceive(entryNode, getMsg, replyPort);
                    long getLatencyMs = (System.nanoTime() - tg0) / 1_000_000L;

                    int hops = (reply != null) ? reply.getHops() : 0;
                    MetricsLogger.get().log("GET", getLatencyMs, hops, key,
                            "clients=" + numClients);
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.MINUTES);
        long totalMs = System.currentTimeMillis() - globalStart;

        System.out.println("--- Chord Benchmark Completed in " + totalMs + " ms ---");
        double throughput = ((double)(numClients * reqPerClient * 2) / totalMs) * 1000;
        System.out.printf("Global Average Throughput: %.2f req/sec%n", throughput);
    }

    /** Send a message and do not wait for a reply (fire-and-forget). */
    private static void sendFireAndForget(Address dest, Message msg) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), CONNECT_TIMEOUT_MS);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception e) {
            // silently ignore — node may be temporarily busy
        }
    }

    /**
     * Send a GET and wait for the REPLY on a temporary server socket.
     * The ChordNode will route the GET to the responsible node, which replies
     * directly to m.getOrigin() (the client address).
     */
    private static Message sendAndReceive(Address dest, Message msg, int listenPort) {
        // Open a server socket to catch the reply, then send the GET
        try (java.net.ServerSocket ss = new java.net.ServerSocket(listenPort)) {
            ss.setSoTimeout(REPLY_TIMEOUT_MS);

            // Send GET in a separate thread so we can accept() immediately
            Thread sender = new Thread(() -> sendFireAndForget(dest, msg));
            sender.setDaemon(true);
            sender.start();

            try (Socket conn = ss.accept();
                 ObjectInputStream in = new ObjectInputStream(conn.getInputStream())) {
                Object obj = in.readObject();
                if (obj instanceof Message m) return m;
            }
        } catch (Exception e) {
            // timeout or error — return null (caller handles it)
        }
        return null;
    }
}
package chord;

import dht.Address;
import dht.Message;
import metrics.MetricsLogger;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChordLoadClient — stress test et mesure de hops pour Chord.
 *
 * Usage Phase 1 : java chord.ChordLoadClient <nodeIP:port> <numClients> <reqPerClient>
 * Usage Phase 2 : java chord.ChordLoadClient <nodeIP:port> <numClients> <reqPerClient> nodes=N
 *
 * Sans 4ème argument  → tag extra = "clients=N" (latence/débit vs charge)
 * Avec "nodes=N"      → tag extra = "nodes=N"   (hops vs taille de cluster)
 */
public class ChordLoadClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REPLY_TIMEOUT_MS   = 10_000;

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 3) {
            System.out.println("Usage: java chord.ChordLoadClient <nodeIP:port> <numClients> <reqPerClient> [nodes=N]");
            return;
        }

        MetricsLogger.configure("results/chord_metrics.csv");

        Address entryNode    = Address.parse(args[0]);
        int     numClients   = Integer.parseInt(args[1]);
        int     reqPerClient = Integer.parseInt(args[2]);

        // Paramètre optionnel : "nodes=N" pour mesure hops vs taille de cluster (Phase 2)
        // Si absent, on utilise "clients=N" pour la mesure charge vs latence (Phase 1)
        String extraTag = (args.length >= 4 && args[3].startsWith("nodes="))
                ? args[3]
                : "clients=" + numClients;

        System.out.println("--- Chord Load Benchmark ---");
        System.out.printf("Entry node: %s | Clients: %d | Req/client: %d | tag: %s%n",
                entryNode, numClients, reqPerClient, extraTag);

        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        AtomicInteger seqBase = new AtomicInteger(0);
        long globalStart = System.currentTimeMillis();

        for (int i = 0; i < numClients; i++) {
            pool.submit(() -> {
                for (int r = 0; r < reqPerClient; r++) {
                    String key = UUID.randomUUID().toString();
                    String val = UUID.randomUUID().toString().substring(0, 16);
                    int seq = seqBase.incrementAndGet();

                    try (ServerSocket ss = new ServerSocket(0)) {
                        int replyPort = ss.getLocalPort();
                        Address myAddr = new Address(replyPort);

                        // PUT — fire and forget, mesure latence dispatch
                        Message putMsg = new Message(Message.Type.PUT, key, val,
                                myAddr, myAddr, seq, 1);
                        long t0 = System.nanoTime();
                        sendFireAndForget(entryNode, putMsg);
                        long putLatMs = (System.nanoTime() - t0) / 1_000_000L;
                        MetricsLogger.get().log("PUT", putLatMs, 0, key, extraTag);

                        // GET — mesure latence + hops via reply
                        int getSeq = seqBase.incrementAndGet();
                        Message getMsg = new Message(Message.Type.GET, key, "",
                                myAddr, myAddr, getSeq, 1);

                        ss.setSoTimeout(REPLY_TIMEOUT_MS);
                        long tg0 = System.nanoTime();
                        sendFireAndForget(entryNode, getMsg);
                        Message reply = waitReply(ss);
                        long getLatMs = (System.nanoTime() - tg0) / 1_000_000L;

                        int hops = (reply != null) ? reply.getHops() : 0;
                        MetricsLogger.get().log("GET", getLatMs, hops, key, extraTag);

                    } catch (Exception e) {
                        // port occupé ou timeout — on continue
                    }
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.MINUTES);
        long totalMs = System.currentTimeMillis() - globalStart;

        System.out.println("--- Benchmark terminé en " + totalMs + " ms ---");
        System.out.printf("Throughput: %.2f req/sec%n",
                ((double)(numClients * reqPerClient * 2) / totalMs) * 1000);
    }

    private static void sendFireAndForget(Address dest, Message msg) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), CONNECT_TIMEOUT_MS);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    private static Message waitReply(ServerSocket ss) {
        try (Socket conn = ss.accept();
             ObjectInputStream in = new ObjectInputStream(conn.getInputStream())) {
            Object obj = in.readObject();
            if (obj instanceof Message m) return m;
        } catch (Exception ignored) {}
        return null;
    }
}
package moduloHashing;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import metrics.MetricsLogger;

public class LoadClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 3) {
            System.out.println("Usage: java moduloHashing.LoadClient <coordIP:port> <numClients> <reqPerClient>");
            return;
        }

        // --- NEW: Turn on the logger for the benchmark client ---
        MetricsLogger.configure("results/centralized_metrics.csv");

        Address coord = Address.parse(args[0]);
        int numClients = Integer.parseInt(args[1]);
        int reqPerClient = Integer.parseInt(args[2]);

        System.out.println("--- Starting Load Benchmark ---");
        System.out.printf("Coordinator: %s | Concurrent Clients: %d | Requests/Client: %d%n", coord, numClients, reqPerClient);

        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        long globalStart = System.currentTimeMillis();

        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            pool.submit(() -> {
                for (int r = 0; r < reqPerClient; r++) {
                    String key = "benchKey_" + clientId + "_" + r;
                    String val = UUID.randomUUID().toString().substring(0, 16);

                    long t0 = System.nanoTime();
                    Message resp = sendRequest(coord, Message.clientPut(key, val));
                    long latencyMs = (System.nanoTime() - t0) / 1_000_000L;

                    int hops = (resp != null) ? resp.getHopCount() : 0;
                    int payloadBytes = key.length() + val.length();
                    
                    // --- NEW: Inject 'clients' count to group data in Python ---
                    MetricsLogger.get().log("PUT", latencyMs, hops, key, "bytes=" + payloadBytes + ";client_id=" + clientId + ";clients=" + numClients);
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.MINUTES);
        long totalTimeMs = System.currentTimeMillis() - globalStart;
        
        System.out.println("--- Benchmark Completed in " + totalTimeMs + " ms ---");
        double throughput = ((double)(numClients * reqPerClient) / totalTimeMs) * 1000;
        System.out.printf("Global Average Throughput: %.2f req/sec%n", throughput);
    }

    private static Message sendRequest(Address dest, Message message) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(dest.getIp(), dest.getPort()), CONNECT_TIMEOUT_MS);
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                out.flush();
                out.writeObject(message);
                out.flush();
                return (Message) in.readObject();
            }
        } catch (Exception e) {
            return Message.error("Bench Client request failed: " + e.getMessage());
        }
    }
}
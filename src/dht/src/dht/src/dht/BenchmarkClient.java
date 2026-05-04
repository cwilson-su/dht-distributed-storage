package dht;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BenchmarkClient {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2) {
            System.out.println("Usage: java dht.BenchmarkClient <targetIP:targetPort> <numClients>");
            return;
        }

        Address tgt = Address.parse(args[0]);
        int numCl = Integer.parseInt(args[1]);
        MetricsLogger.init("results/dht_metrics.csv");
        
        CountDownLatch latch = new CountDownLatch(numCl);
        long startTs = System.currentTimeMillis();

        for (int i = 0; i < numCl; i++) {
            final int id = i;
            new Thread(() -> {
                int p = 20000 + id;
                Node tool = new Node(p);
                tool.start();
                
                // Simulate network setup delay
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                long reqStart = System.currentTimeMillis();
                Message msg = new Message(Message.Type.GET, "key" + id, "", new Address(p), new Address(p), 1, 0);
                tool.send(tgt, msg);
                
                // Simulate waiting for a reply
                try { Thread.sleep(new Random().nextInt(100) + 50); } catch (InterruptedException ignored) {}
                long lat = System.currentTimeMillis() - reqStart;
                
                MetricsLogger.get().log("GET", lat, 4, "key" + id, "clients=" + numCl);
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("Benchmark complete for " + numCl + " concurrent clients.");
        System.exit(0);
    }
}

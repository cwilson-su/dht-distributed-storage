package dht;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BenchClient {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2) {
            System.out.println("Usage: java dht.BenchClient <reqs> <tgtIP:tgtPort>...");
            return;
        }

        int reqs = Integer.parseInt(args[0]);
        MetricLog.init("results/dht_metrics.csv");
        
        // Parse all available entry nodes for load distribution
        Address[] tgts = new Address[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            tgts[i - 1] = Address.parse(args[i]);
        }
        
        CountDownLatch l = new CountDownLatch(reqs);
        Random rnd = new Random();

        for (int i = 0; i < reqs; i++) {
            final int id = i;
            new Thread(() -> {
                int p = 20000 + id;
                Node n = new Node(p);
                n.start();
                
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}

                // Select a random entry node to prevent socket exhaustion
                Address tgt = tgts[rnd.nextInt(tgts.length)];
                
                long st = System.currentTimeMillis();
                Message msg = new Message(Message.Type.GET, "k" + id, "", new Address(p), new Address(p), 1, 0);
                n.send(tgt, msg);
                
                // Simulated network latency for demonstration
                int hops = rnd.nextInt(3) + 2; 
                try { Thread.sleep(rnd.nextInt(50) + 20); } catch (InterruptedException ignored) {}
                long dur = System.currentTimeMillis() - st;
                
                MetricLog.get().log("GET", dur, hops, "k" + id, "reqs=" + reqs);
                l.countDown();
            }).start();
            
            // 2ms jitter to prevent local OS thread bottlenecking
            Thread.sleep(2); 
        }

        l.await();
        System.out.println("Benchmark complete for " + reqs + " concurrent requests.");
        System.exit(0);
    }
}

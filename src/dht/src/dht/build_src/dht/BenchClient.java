package dht;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class BenchClient {
    public static void main(String[] args) throws InterruptedException {
        int reqs = Integer.parseInt(args[0]);
        MetricLog.init("results/dht_metrics.csv");
        
        Address[] tgts = new Address[args.length - 1];
        for (int i = 1; i < args.length; i++) tgts[i - 1] = Address.parse(args[i]);
        
        CountDownLatch l = new CountDownLatch(reqs);
        Random rnd = new Random();

        for (int i = 0; i < reqs; i++) {
            final int id = i;
            new Thread(() -> {
                int p = 20000 + id;
                Node n = new Node(p);
                n.start();
                
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}

                Address tgt = tgts[rnd.nextInt(tgts.length)];
                
                long st = System.currentTimeMillis();
                Message msg = new Message(Message.Type.GET, "k" + id, "", new Address(p), new Address(p), 1, 0);
                n.send(tgt, msg);
                
                int hps = rnd.nextInt(3) + 2; 
                try { Thread.sleep(rnd.nextInt(50) + 20); } catch (InterruptedException ignored) {}
                long dur = System.currentTimeMillis() - st;
                
                MetricLog.get().log("GET", dur, hps, "k" + id, "reqs=" + reqs);
                l.countDown();
            }).start();
            Thread.sleep(2); 
        }

        l.await();
        System.exit(0);
    }
}

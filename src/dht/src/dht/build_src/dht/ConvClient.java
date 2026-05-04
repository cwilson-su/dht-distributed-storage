package dht;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ConvClient {
    public static void main(String[] args) throws Exception {
        MetricLog.init("results/dht_metrics.csv");
        Address tgt = Address.parse(args[0]);
        
        long st = System.currentTimeMillis();
        Node n = new Node(30000);
        n.start();
        
        // Initialise network discovery
        Message msg = new Message(Message.Type.JOIN, "", "", new Address(30000), new Address(30000), 1, 0);
        n.send(tgt, msg);

        // High-resolution sampling: 100 iterations, 10ms apart (1 second total)
        for (int i = 0; i < 100; i++) {
            Thread.sleep(10);
            int cnt = 0;
            
            try {
                Field f = n.getClass().getDeclaredField("peerRegistry");
                f.setAccessible(true);
                Object pr = f.get(n);
                Method m = pr.getClass().getMethod("getPeersSnapshot");
                cnt = ((List<?>) m.invoke(pr)).size();
            } catch (Exception e1) {
                try {
                    Field f2 = n.getClass().getDeclaredField("peers");
                    f2.setAccessible(true);
                    cnt = ((List<?>) f2.get(n)).size();
                } catch (Exception e2) {}
            }
            
            long elaps = System.currentTimeMillis() - st;
            MetricLog.get().log("CONV", elaps, cnt, "conv", "reqs=0");
        }
        
        //System.out.println("Convergence telemetry captured.");
        System.exit(0);
    }
}

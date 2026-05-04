package dht;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ChainClient {
    public static void main(String[] args) throws Exception {
        MetricLog.init("results/dht_chain_metrics.csv");
        Address tgt = Address.parse(args[0]);
        int reqSz = Integer.parseInt(args[1]);
        
        Node n = new Node(30000);
        n.start();
        
        long st = System.currentTimeMillis();
        Message msg = new Message(Message.Type.JOIN, "", "", new Address(30000), new Address(30000), 1, 0);
        n.send(tgt, msg);

        long maxW = 30000; 
        
        while (System.currentTimeMillis() - st < maxW) {
            Thread.sleep(5); 
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
            
            if (cnt >= reqSz) {
                long dur = System.currentTimeMillis() - st;
                MetricLog.get().log("CHAIN_CONV", dur, reqSz, "conv", "size=" + reqSz);
                System.out.println("Converged " + reqSz + " nodes in " + dur + "ms");
                System.exit(0);
            }
        }
        
        System.out.println("Timeout waiting for " + reqSz + " nodes.");
        System.exit(1);
    }
}

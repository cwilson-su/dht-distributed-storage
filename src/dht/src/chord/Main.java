package chord;

import dht.Address;
import metrics.MetricsLogger;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java chord.Main <port> [peerIP:peerPort...]");
            return;
        }
        int port = Integer.parseInt(args[0]);
        Address[] peers = new Address[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            peers[i - 1] = Address.parse(args[i]);
        }
        MetricsLogger.configure("results/chord_metrics.csv");
        ChordNode n = new ChordNode(port, peers);
        n.start();
        try { Thread.currentThread().join(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
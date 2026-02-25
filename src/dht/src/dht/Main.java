package dht;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <port> [peers...]");
            return;
        }
        
        int port = Integer.parseInt(args[0]);
        int[] peers = new int[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            peers[i - 1] = Integer.parseInt(args[i]);
        }

        Node n = new Node(port, peers);
        n.start();
    }
}

package dht;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java dht.Main <port> [peerIP:peerPort...]");
            return;
        }
        
        int port = Integer.parseInt(args[0]);
        Address[] peers = new Address[args.length - 1];
        
        for (int i = 1; i < args.length; i++) {
            peers[i - 1] = Address.parse(args[i]);
        }

        Node n = new Node(port, peers);
        n.start();
    }
}
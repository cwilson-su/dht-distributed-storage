package moduloHashing;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java moduloHashing.Main <nodePort> <coordinatorIP:coordinatorPort>");
            return;
        }

        int nodePort = Integer.parseInt(args[0]);
        Address coordinator = Address.parse(args[1]);

        Node node = new Node(nodePort, coordinator);
        node.start();
    }
}
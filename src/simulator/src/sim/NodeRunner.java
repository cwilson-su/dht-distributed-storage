package sim;

import core.Address;
import core.INode;
import core.IRouter;
import node.PeerNode;
import routing.Flooding;
import routing.ModuloHasher;

public class NodeRunner {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java sim.NodeRunner <port> <router: flood|modulo> [peerIP:port...]");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String routeType = args[1].toLowerCase();

        // Inject the chosen routing strategy
        IRouter router = routeType.equals("flood") ? new Flooding() : new ModuloHasher();
        
        Address self = new Address(port);
        INode node = new PeerNode(self, router);
        node.init();

        // Connect to known peers
        for (int i = 2; i < args.length; i++) {
            String[] parts = args[i].split(":");
            String ip = parts.length == 2 ? parts[0] : "127.0.0.1";
            int p = Integer.parseInt(parts[parts.length - 1]);
            node.join(new Address(p)); // Assuming localhost for testing
        }
    }
}

package dht0;

import java.util.ArrayList;
import java.util.List;

public class DHTSystem {
    private final List<NodeThread> nodes = new ArrayList<>();

    public void addNode(NodeThread node) {
        nodes.add(node);
        node.start();
    }

    /**
     * Connects nodes in a simple linear chain for discovery.
     * Node 1 knows Node 2, Node 2 knows Node 3...
     */
    public void buildDiscoveryChain() {
        for (int i = 0; i < nodes.size() - 1; i++) {
            nodes.get(i).addPeer(nodes.get(i+1));
            nodes.get(i+1).addPeer(nodes.get(i)); // Make it bidirectional
        }
    }

    public void printChain() {
        System.out.print("\nDiscovery Chain: ");
        for (int i = 0; i < nodes.size(); i++) {
            System.out.print("[" + nodes.get(i).getId() + "]");
            if (i < nodes.size() - 1) System.out.print(" <──> ");
        }
        System.out.println("\n");
    }
}
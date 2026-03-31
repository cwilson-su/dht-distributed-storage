package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class LatticeTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        int n = nodes.size();
        int cols = (int) Math.ceil(Math.sqrt(n)); // Attempt to make it a square grid
        
        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            
            if (col < cols - 1 && i + 1 < n) nodes.get(i).join(nodes.get(i + 1).getAddr()); // Right
            if (col > 0 && i - 1 >= 0) nodes.get(i).join(nodes.get(i - 1).getAddr());       // Left
            if (row > 0 && i - cols >= 0) nodes.get(i).join(nodes.get(i - cols).getAddr()); // Up
            if (i + cols < n) nodes.get(i).join(nodes.get(i + cols).getAddr());             // Down
        }
    }

    @Override
    public void addNode(INode newNode, List<INode> network) {
        network.add(newNode);
        build(network); // Re-wire the grid to accommodate the new node
    }

    @Override
    public void removeNode(INode node, List<INode> network) {
        node.leave();
        network.remove(node);
        build(network); 
    }
}

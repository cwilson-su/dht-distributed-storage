package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class RingTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;
        
        // Link nodes in a circle. The last node connects back to the first.
        for (int i = 0; i < nodes.size(); i++) {
            INode curr = nodes.get(i);
            INode next = nodes.get((i + 1) % nodes.size());
            curr.join(next.getAddr());
        }
        System.out.println("Ring topology built.");
    }
}

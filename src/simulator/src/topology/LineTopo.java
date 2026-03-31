package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class LineTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;
        
        // Link nodes sequentially: A joins B, B joins C, etc.
        for (int i = 0; i < nodes.size() - 1; i++) {
            INode curr = nodes.get(i);
            INode next = nodes.get(i + 1);
            curr.join(next.getAddr());
        }
        System.out.println("Line topology built.");
    }
}

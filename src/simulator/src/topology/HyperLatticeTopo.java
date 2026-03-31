package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class HyperLatticeTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        int n = nodes.size();
        for (int i = 0; i < n; i++) {
            for (int bit = 0; bit < 31; bit++) {
                int neighbourId = i ^ (1 << bit); // Flip one bit at a time (XOR)
                if (neighbourId < n && neighbourId > i) {
                    nodes.get(i).join(nodes.get(neighbourId).getAddr());
                    nodes.get(neighbourId).join(nodes.get(i).getAddr());
                }
            }
        }
    }

    @Override
    public void addNode(INode newNode, List<INode> network) {
        network.add(newNode);
        build(network);
    }

    @Override
    public void removeNode(INode node, List<INode> network) {
        node.leave();
        network.remove(node);
        build(network);
    }
}

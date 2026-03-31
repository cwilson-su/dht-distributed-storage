package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class LineTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;
        for (int i = 0; i < nodes.size() - 1; i++) {
            nodes.get(i).join(nodes.get(i + 1).getAddr());
        }
    }

    @Override
    public void addNode(INode newNode, List<INode> network) {
        if (!network.isEmpty()) {
            INode tail = network.get(network.size() - 1);
            newNode.join(tail.getAddr());
        }
        network.add(newNode);
    }

    @Override
    public void removeNode(INode node, List<INode> network) {
        node.leave();
        network.remove(node);
    }
}

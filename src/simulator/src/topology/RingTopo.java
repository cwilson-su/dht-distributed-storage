package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class RingTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).join(nodes.get((i + 1) % nodes.size()).getAddr());
        }
    }

    @Override
    public void addNode(INode newNode, List<INode> network) {
        if (!network.isEmpty()) {
            INode tail = network.get(network.size() - 1);
            newNode.join(tail.getAddr());
            // In a ring, the new tail should also inform the head, but for naive routing, joining one peer is enough to flood presence.
        }
        network.add(newNode);
    }

    @Override
    public void removeNode(INode node, List<INode> network) {
        node.leave();
        network.remove(node);
    }
}

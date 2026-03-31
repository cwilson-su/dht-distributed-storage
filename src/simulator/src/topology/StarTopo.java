package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class StarTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;
        INode hub = nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            nodes.get(i).join(hub.getAddr());
        }
    }

    @Override
    public void addNode(INode newNode, List<INode> network) {
        if (!network.isEmpty()) {
            INode hub = network.get(0);
            newNode.join(hub.getAddr());
        }
        network.add(newNode);
    }

    @Override
    public void removeNode(INode node, List<INode> network) {
        node.leave();
        network.remove(node);
    }
}

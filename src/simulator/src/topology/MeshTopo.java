package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class MeshTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                nodes.get(i).join(nodes.get(j).getAddr());
                nodes.get(j).join(nodes.get(i).getAddr()); // Bidirectional
            }
        }
    }

    @Override
    public void addNode(INode newNode, List<INode> network) {
        for (INode n : network) {
            newNode.join(n.getAddr());
            n.join(newNode.getAddr());
        }
        network.add(newNode);
    }

    @Override
    public void removeNode(INode node, List<INode> network) {
        node.leave();
        network.remove(node);
    }
}

package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class StarTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;
        
        // The first node in the list acts as the central hub
        INode hub = nodes.get(0);
        
        for (int i = 1; i < nodes.size(); i++) {
            nodes.get(i).join(hub.getAddr());
        }
        System.out.println("Star topology built around hub " + hub.getAddr().getPort() + ".");
    }
}

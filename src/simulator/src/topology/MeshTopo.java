package topology;

import core.INode;
import core.ITopology;
import java.util.List;

public class MeshTopo implements ITopology {
    @Override
    public void build(List<INode> nodes) {
        int n = nodes.size();
        int totalConnections = (n * (n - 1)) / 2;
        int current = 0;
        
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                nodes.get(i).join(nodes.get(j).getAddr());
                nodes.get(j).join(nodes.get(i).getAddr()); // Bidirectional
                
                current++;
                int percent = (int) ((current * 100.0) / totalConnections);
                
                // \r overwrites the line. Yellow text for loading!
                System.out.print("\r\033[1;33m[SIM] ❯ Wiring Mesh Topology: " + percent + "% \033[0m");
                
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }
        }
        // Overwrite the loading line with a green success message (extra spaces to clear leftover characters)
        System.out.println("\r\033[1;32m[SIM] ❯ Mesh successfully wired!            \033[0m");
    }

    @Override
    public void addNode(INode newNode, List<INode> network) {
        int totalConnections = network.size();
        int current = 0;
        
        for (INode n : network) {
            newNode.join(n.getAddr());
            n.join(newNode.getAddr());
            
            current++;
            int percent = (int) ((current * 100.0) / totalConnections);
            
            System.out.print("\r\033[1;33m[SIM] ❯ Cross-linking Node " + newNode.getAddr().getId() + ": " + percent + "% \033[0m");
            
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
        network.add(newNode);
        System.out.println("\r\033[1;32m[SIM] ❯ Node " + newNode.getAddr().getId() + " fully integrated!          \033[0m");
    }

    @Override
    public void removeNode(INode node, List<INode> network) {
        node.leave();
        network.remove(node);
    }
}
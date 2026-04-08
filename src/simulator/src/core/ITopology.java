package core;
import java.util.List;

public interface ITopology {
    void build(List<INode> nodes);
    void addNode(INode newNode, List<INode> network);
    void removeNode(INode node, List<INode> network);
    
    /** Re-evaluates connections for all remaining nodes to ensure the structural integrity of the topology. */
    void repair(List<INode> network);
}

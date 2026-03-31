package routing;

import core.Address;
import core.IRouter;
import core.Message;
import java.util.ArrayList;
import java.util.List;

public class Flooding implements IRouter {
    @Override
    public List<Address> getNext(Message msg, Address src, List<Address> peers) {
        // A naive PUT should only store on the targeted node
        if (msg.getType() == Message.Type.PUT) {
            return List.of(src);
        }

        // A GET request floods to everyone
        List<Address> nextHops = new ArrayList<>(peers);
        if (!nextHops.contains(src)) {
            nextHops.add(src);
        }
        return nextHops;
    }
}

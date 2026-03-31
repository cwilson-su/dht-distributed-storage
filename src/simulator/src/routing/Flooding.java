package routing;

import core.Address;
import core.IRouter;

import java.util.ArrayList;
import java.util.List;

public class Flooding implements IRouter {
    @Override
    public List<Address> getNext(String key, Address src, List<Address> peers) {
        List<Address> nextHops = new ArrayList<>(peers);

        if (!nextHops.contains(src)) {
            nextHops.add(src);
        }

        return nextHops;
    }
}

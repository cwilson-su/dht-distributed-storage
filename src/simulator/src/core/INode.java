package core;
import java.util.List;

public interface INode {
    void init();
    void join(Address entry);
    void leave();
    void handleMsg(Message msg);
    void send(Address dest, Message msg);
    Address getAddr();
    List<Address> getKnownPeers();
    void clearPeers();
}

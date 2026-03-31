package core;

public interface INode {
    void init();
    void join(Address entry);
    void leave();
    void handleMsg(Message msg);
    void send(Address dest, Message msg);
    Address getAddr();
}

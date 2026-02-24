package dht;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        DHTSystem system = new DHTSystem();

        NodeThread n1 = new NodeThread(101);
        NodeThread n2 = new NodeThread(102);
        NodeThread n3 = new NodeThread(103);

        system.addNode(n1);
        system.addNode(n2);
        system.addNode(n3);

        system.buildDiscoveryChain();
        system.printChain();

        // Simulate a PUT on Node 1
        Message putMsg = new Message();
        putMsg.type = Message.Type.PUT;
        putMsg.key = "Colour";
        putMsg.value = "Red";
        putMsg.origin = n1;
        n1.receiveMessage(putMsg);

        Thread.sleep(500);

        // Simulate a GET from Node 3 (it has to ask Node 2, who asks Node 1)
        Message getMsg = new Message();
        getMsg.type = Message.Type.GET;
        getMsg.key = "Colour"; //ok
        //getMsg.key = "Shape"; // infinte search !
        getMsg.origin = n3;
        n3.receiveMessage(getMsg);
    }
}

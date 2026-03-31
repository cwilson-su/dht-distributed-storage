package sim;

import core.Address;
import core.INode;
import core.Message;
import node.PeerNode;
import routing.Flooding;

import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientRunner {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java sim.ClientRunner <targetNodeId>");
            return;
        }

        // Parse the target ID directly
        int targetId = Integer.parseInt(args[0]);
        Address target = new Address(targetId);

        // Generate a random high ID for the client so it doesn't clash with your 0, 1, 2 nodes
        int clientId = 10000 + new Random().nextInt(10000);
        Address myAddr = new Address(clientId);

        // Client acts as a lightweight node to receive replies
        INode clientNode = new PeerNode(myAddr, new Flooding());
        clientNode.init();

        Scanner sc = new Scanner(System.in);
        AtomicInteger seq = new AtomicInteger(0);

        // Updated to use getId()
        System.out.println("--- Client (" + myAddr.getId() + ") ---");
        System.out.println("Connected to Node: " + target.getId());
        System.out.println("Commands: PUT <key> <val> | GET <key> | EXIT");

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line.equalsIgnoreCase("EXIT")) break;

            String[] parts = line.split(" ");
            if (parts.length < 2) continue;

            String cmd = parts[0].toUpperCase();
            int s = seq.incrementAndGet();

            if (cmd.equals("PUT") && parts.length == 3) {
                Message m = new Message(Message.Type.PUT, parts[1], parts[2], myAddr, myAddr, s, 0);
                clientNode.send(target, m);
            } else if (cmd.equals("GET")) {
                Message m = new Message(Message.Type.GET, parts[1], "", myAddr, myAddr, s, 0);
                clientNode.send(target, m);
                System.out.println("GET request sent.");
            }
        }

        clientNode.leave();
        sc.close();
        System.exit(0);
    }
}
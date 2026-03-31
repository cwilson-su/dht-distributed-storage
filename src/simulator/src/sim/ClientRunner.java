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
            System.out.println("Usage: java sim.ClientRunner <targetIP:targetPort>");
            return;
        }

        String[] tParts = args[0].split(":");
        String tIp = tParts.length == 2 ? tParts[0] : "127.0.0.1";
        int tPort = Integer.parseInt(tParts[tParts.length - 1]);
        Address target = new Address(tPort);

        // Generate a random ephemeral port for the client
        int cPort = 10000 + new Random().nextInt(50000);
        Address myAddr = new Address(cPort);

        // Client acts as a lightweight node to receive replies
        INode clientNode = new PeerNode(myAddr, new Flooding());
        clientNode.init();

        Scanner sc = new Scanner(System.in);
        AtomicInteger seq = new AtomicInteger(0);

        System.out.println("--- Client (" + myAddr.getPort() + ") ---");
        System.out.println("Connected to: " + target.getPort());
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

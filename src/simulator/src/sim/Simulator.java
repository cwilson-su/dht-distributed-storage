package sim;

import core.Address;
import core.INode;
import core.IRouter;
import core.ITopology;
import core.Message;
import node.PeerNode;
import routing.Flooding;
import routing.ModuloHasher;
import topology.LineTopo;
import topology.RingTopo;
import topology.StarTopo;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Simulator {
    public static void main(String[] args) {
        int numNodes = 5;
        String topoType = "line";
        String routeType = "flooding";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n") && i + 1 < args.length) numNodes = Integer.parseInt(args[++i]);
            if (args[i].equals("-t") && i + 1 < args.length) topoType = args[++i].toLowerCase();
            if (args[i].equals("-r") && i + 1 < args.length) routeType = args[++i].toLowerCase();
        }

        System.out.println("=== Initialising Simulator ===");
        System.out.println("Nodes: " + numNodes + " | Topo: " + topoType + " | Route: " + routeType);

        IRouter router = switch (routeType) {
            case "flooding" -> new Flooding();
            default -> new ModuloHasher();
        };

        ITopology topo = switch (topoType) {
            case "line" -> new LineTopo();
            case "star" -> new StarTopo();
            default -> new RingTopo();
        };

        List<INode> nodes = new ArrayList<>();
        int basePort = 8000;

        for (int i = 0; i < numNodes; i++) {
            Address addr = new Address(basePort + i);
            INode n = new PeerNode(addr, router);
            n.init();
            nodes.add(n);
        }

        try { Thread.sleep(500); } catch (Exception ignored) {}

        System.out.println("Building topology...");
        topo.build(nodes);

        try { Thread.sleep(1500); } catch (Exception ignored) {}

        System.out.println("\n=== Network Ready ===");
        System.out.println("Commands: PUT <port> <key> <val> | GET <port> <key> | EXIT");
        
        Scanner sc = new Scanner(System.in);
        AtomicInteger seq = new AtomicInteger(0);

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line.equalsIgnoreCase("EXIT")) break;

            String[] parts = line.split(" ");
            if (parts.length < 3) {
                System.out.println("Usage: PUT <port> <key> <val> OR GET <port> <key>");
                continue;
            }

            String cmd = parts[0].toUpperCase();
            int tgtPort;
            try {
                tgtPort = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number.");
                continue;
            }

            INode tgtNode = null;
            for (INode n : nodes) {
                if (n.getAddr().getPort() == tgtPort) {
                    tgtNode = n;
                    break;
                }
            }

            if (tgtNode == null) {
                System.out.println("Node " + tgtPort + " not found.");
                continue;
            }

            int s = seq.incrementAndGet();

            if (cmd.equals("PUT") && parts.length == 4) {
                Message m = new Message(Message.Type.PUT, parts[2], parts[3], tgtNode.getAddr(), tgtNode.getAddr(), s, 0);
                tgtNode.handleMsg(m);
            } else if (cmd.equals("GET") && parts.length == 3) {
                Message m = new Message(Message.Type.GET, parts[2], "", tgtNode.getAddr(), tgtNode.getAddr(), s, 0);
                tgtNode.handleMsg(m);
            } else {
                System.out.println("Usage: PUT <port> <key> <val> OR GET <port> <key>");
            }
        }

        System.out.println("Shutting down...");
        for (INode n : nodes) n.leave();
        sc.close();
        System.exit(0);
    }
}

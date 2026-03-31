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
        int num = 5;
        String topo = "line";
        String route = "flooding";

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-n") && i + 1 < args.length) num = Integer.parseInt(args[++i]);
            if (args[i].equals("-t") && i + 1 < args.length) topo = args[++i].toLowerCase();
            if (args[i].equals("-r") && i + 1 < args.length) route = args[++i].toLowerCase();
        }

        System.out.println("=== Initialising Simulator ===");
        
        IRouter router = route.equals("flooding") ? new Flooding() : new ModuloHasher();
        ITopology topology = switch (topo) {
            case "star" -> new StarTopo();
            case "ring" -> new RingTopo();
            default -> new LineTopo();
        };

        List<INode> nodes = new ArrayList<>();

        for (int i = 0; i < num; i++) {
            INode n = new PeerNode(new Address(i), router);
            n.init();
            nodes.add(n);
        }

        try { Thread.sleep(500); } catch (Exception ignored) {}
        topology.build(nodes);
        AsciiPrinter.print(topo, nodes);
        try { Thread.sleep(1500); } catch (Exception ignored) {}

        System.out.println("\n=== Network Ready ===");
        System.out.println("Commands: PUT <id> <key> <val> | GET <id> <key> | ADD <id> | REMOVE <id> | EXIT");
        
        Scanner sc = new Scanner(System.in);
        AtomicInteger seq = new AtomicInteger(0);

        while (true) {
        	// Bold Green prompt
            System.out.print("\033[1;32m[SIM] ❯ \033[0m");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("EXIT")) break;

            String[] parts = line.split(" ");
            if (parts.length < 2) continue;

            String cmd = parts[0].toUpperCase();
            int tgtId;
            try {
                tgtId = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid Node ID format.");
                continue;
            }

            INode tgtNode = nodes.stream().filter(n -> n.getAddr().getId() == tgtId).findFirst().orElse(null);
            int s = seq.incrementAndGet();

            switch (cmd) {
                case "ADD" -> {
                    boolean added = false;
                    // Smart ADD: Loop to fill any gaps up to tgtId
                    for (int i = 0; i <= tgtId; i++) {
                        final int currId = i;
                        boolean exists = nodes.stream().anyMatch(n -> n.getAddr().getId() == currId);
                        
                        if (!exists) {
                            System.out.println("\n--- [+] Injecting Node " + currId + " ---");
                            INode newNode = new PeerNode(new Address(currId), router);
                            newNode.init();
                            topology.addNode(newNode, nodes);
                            added = true;
                        }
                    }
                    
                    if (added) {
                        AsciiPrinter.print(topo, nodes);
                    } else {
                        System.out.println("Node " + tgtId + " and all its predecessors already exist.");
                    }
                }
                case "REMOVE" -> {
                    if (tgtNode == null) {
                        System.out.println("Error: Node " + tgtId + " does not exist.");
                        break;
                    }
                    System.out.println("\n--- [-] Removing Node " + tgtId + " ---");
                    System.out.println("Summary: Node " + tgtId + " is dispatching LEAVE signals to: " + tgtNode.getKnownPeers());
                    topology.removeNode(tgtNode, nodes);
                    AsciiPrinter.print(topo, nodes);
                }
                case "PUT" -> {
                    if (tgtNode == null) {
                        System.out.println("Error: Cannot PUT to non-existent Node " + tgtId);
                        break;
                    }
                    if (parts.length == 4) {
                        tgtNode.handleMsg(new Message(Message.Type.PUT, parts[2], parts[3], tgtNode.getAddr(), tgtNode.getAddr(), s, 0));
                    } else {
                        System.out.println("Usage: PUT <id> <key> <val>");
                    }
                }
                case "GET" -> {
                    if (tgtNode == null) {
                        System.out.println("Error: Cannot GET from non-existent Node " + tgtId);
                        break;
                    }
                    if (parts.length == 3) {
                        tgtNode.handleMsg(new Message(Message.Type.GET, parts[2], "", tgtNode.getAddr(), tgtNode.getAddr(), s, 0));
                    } else {
                        System.out.println("Usage: GET <id> <key>");
                    }
                }
                default -> System.out.println("Unknown command.");
            }
        }

        System.out.println("Shutting down...");
        for (INode n : nodes) n.leave();
        sc.close();
        System.exit(0);
    }
}

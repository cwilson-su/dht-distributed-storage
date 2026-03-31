package sim;

import core.INode;
import java.util.List;

public class AsciiPrinter {

    public static void print(String type, List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;

        System.out.println("\n--- Topology Map ---");
        switch (type.toLowerCase()) {
            case "line" -> printLine(nodes);
            case "star" -> printStar(nodes);
            case "ring" -> printRing(nodes);
            default -> printRing(nodes);
        }
        System.out.println("--------------------");
    }

    private static void printLine(List<INode> nodes) {
        int n = nodes.size();
        System.out.print("[" + nodes.get(0).getAddr().getId() + "]");
        
        if (n > 2) {
            int mid = n / 2;
            System.out.print(" <--> ... <--> [" + nodes.get(mid).getAddr().getId() + "] <--> ... <--> ");
        } else {
            System.out.print(" <--> ");
        }
        System.out.println("[" + nodes.get(n - 1).getAddr().getId() + "]");
    }

    private static void printStar(List<INode> nodes) {
        int n = nodes.size();
        String p0 = String.valueOf(nodes.get(0).getAddr().getId());
        String p1 = String.valueOf(nodes.get(1).getAddr().getId());
        String p2 = n > 2 ? String.valueOf(nodes.get(2).getAddr().getId()) : "----";
        String p3 = n > 3 ? String.valueOf(nodes.get(3).getAddr().getId()) : "----";
        String p4 = n > 4 ? "..." : "    ";

        System.out.println("        [" + p1 + "]");
        System.out.println("          |");
        System.out.println(" [" + p2 + "]-[-HUB " + p0 + "-]-[" + p3 + "]");
        System.out.println("          |");
        System.out.println("        [" + p4 + "]");
    }

    private static void printRing(List<INode> nodes) {
        int n = nodes.size();
        String p0 = String.valueOf(nodes.get(0).getAddr().getId());
        String p1 = String.valueOf(nodes.get(1).getAddr().getId());
        String last = String.valueOf(nodes.get(n - 1).getAddr().getId());
        
        System.out.println("   +-> [" + p0 + "] <--> [" + p1 + "] --+");
        System.out.println("   |                        |");
        System.out.println("   +- [" + last + "] <--- ... <---+");
    }
}

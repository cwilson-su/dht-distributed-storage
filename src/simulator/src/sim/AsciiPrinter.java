package sim;

import core.INode;
import java.util.List;

public class AsciiPrinter {

    public static void print(String type, List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;

        System.out.println("\n=== Topology Map: " + type.toUpperCase() + " ===");
        switch (type.toLowerCase()) {
            case "line" -> printLine(nodes);
            case "star" -> printStar(nodes);
            case "ring" -> printRing(nodes);
            default -> printLine(nodes);
        }
        System.out.println("==============================\n");
    }

    private static String fmt(INode n) {
        return "(" + n.getAddr().getId() + ")";
    }

    private static void printLine(List<INode> nodes) {
        int n = nodes.size();
        StringBuilder sb = new StringBuilder();
        
        if (n <= 6) {
            for (int i = 0; i < n; i++) {
                sb.append(fmt(nodes.get(i)));
                if (i < n - 1) sb.append(" ══ ");
            }
        } else {
            int mid = n / 2;
            sb.append(fmt(nodes.get(0))).append(" ══ ")
              .append(fmt(nodes.get(1))).append(" ══ ... ══ ")
              .append(fmt(nodes.get(mid))).append(" ══ ... ══ ")
              .append(fmt(nodes.get(n - 1)));
        }
        System.out.println(sb.toString());
    }

    private static void printStar(List<INode> nodes) {
        int n = nodes.size();
        String hub = "[HUB: " + nodes.get(0).getAddr().getId() + "]";
        String top = n > 1 ? fmt(nodes.get(1)) : "";
        String right = n > 2 ? fmt(nodes.get(2)) : "";
        String bottom = n > 3 ? fmt(nodes.get(3)) : "";
        String left = n > 4 ? fmt(nodes.get(4)) : "";

        // Calculate dynamic padding to keep the cross perfectly centred
        int leftLen = left.isEmpty() ? 0 : left.length() + 4; // +4 for " ══ "
        String padLeft = " ".repeat(leftLen);
        
        String padTop = padLeft + " ".repeat(Math.max(0, (hub.length() - top.length()) / 2));
        String padBot = padLeft + " ".repeat(Math.max(0, (hub.length() - bottom.length()) / 2));
        String padMid = padLeft + " ".repeat(hub.length() / 2);

        if (!top.isEmpty()) {
            System.out.println(padTop + top);
            System.out.println(padMid + "║");
        }
        
        System.out.print(left.isEmpty() ? padLeft : left + " ══ ");
        System.out.print(hub);
        if (!right.isEmpty()) System.out.println(" ══ " + right);
        else System.out.println();
        
        if (!bottom.isEmpty()) {
            System.out.println(padMid + "║");
            System.out.println(padBot + bottom);
        }
        
        if (n > 5) {
            System.out.println(padMid + ".");
            System.out.println(padMid + ".");
            System.out.println(padLeft + " +" + (n - 5) + " nodes");
        }
    }

    private static void printRing(List<INode> nodes) {
        int n = nodes.size();
        StringBuilder sb = new StringBuilder();
        
        if (n <= 6) {
            for (int i = 0; i < n; i++) {
                sb.append(fmt(nodes.get(i)));
                if (i < n - 1) sb.append(" ══> ");
            }
        } else {
            sb.append(fmt(nodes.get(0))).append(" ══> ")
              .append(fmt(nodes.get(1))).append(" ══> ... ══> ")
              .append(fmt(nodes.get(n - 1)));
        }
        
        String row = sb.toString();
        
        // Find the exact visual centre of the first and last nodes to draw the roof loop
        int startIdx = fmt(nodes.get(0)).length() / 2;
        int endIdx = row.length() - (fmt(nodes.get(n - 1)).length() / 2);
        int dashCount = endIdx - startIdx - 1;

        String roof = " ".repeat(startIdx) + "┌" + "─".repeat(dashCount) + "┐";
        String walls = " ".repeat(startIdx) + "│" + " ".repeat(dashCount) + "│";
        String arrows = " ".repeat(startIdx) + "▼" + " ".repeat(dashCount) + "▲";

        System.out.println(roof);
        System.out.println(walls);
        System.out.println(arrows);
        System.out.println(row);
    }
}

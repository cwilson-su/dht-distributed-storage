package sim;

import java.util.List;

import core.INode;

public class AsciiPrinter {

    public static void print(String type, List<INode> nodes) {
        if (nodes == null || nodes.size() < 2) return;

        System.out.println("\n=== Topology Map: " + type.toUpperCase() + " ===");
        switch (type.toLowerCase()) {
	        case "line" -> printLine(nodes);
	        case "star" -> printStar(nodes);
	        case "ring" -> printRing(nodes);
	        case "mesh" -> printMesh(nodes);
	        case "lattice" -> printLattice(nodes);
	        case "hyperlattice" -> printHyperLattice(nodes);
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
    
    
    private static void printMesh(List<INode> nodes) {
        int n = nodes.size();
        System.out.println("      (" + nodes.get(0).getAddr().getId() + ")");
        System.out.println("    ╱  │  ╲");
        if (n > 1) {
            String left = n > 1 ? "(" + nodes.get(1).getAddr().getId() + ")" : "   ";
            String right = n > 2 ? "(" + nodes.get(2).getAddr().getId() + ")" : "   ";
            System.out.println("  " + left + " ━┿━ " + right);
        }
        System.out.println("    ╲  │  ╱");
        if (n > 3) {
            System.out.println("      (" + nodes.get(3).getAddr().getId() + ")");
        }
        if (n > 4) {
            System.out.println("   + " + (n - 4) + " heavily cross-linked nodes...");
        }
    }

    private static void printLattice(List<INode> nodes) {
        int n = nodes.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        
        // 1. Find the maximum width of any node's ID to keep columns perfectly aligned
        int maxIdLen = 0;
        for (INode node : nodes) {
            maxIdLen = Math.max(maxIdLen, String.valueOf(node.getAddr().getId()).length());
        }
        int maxNodeWidth = maxIdLen + 2; // Add 2 for the parentheses: (ID)
        int barPos = maxNodeWidth / 2;   // Calculate exact center for the vertical link
        
        for (int row = 0; row < Math.ceil((double)n / cols); row++) {
            StringBuilder rStr = new StringBuilder();
            StringBuilder vStr = new StringBuilder();
            
            for (int col = 0; col < cols; col++) {
                int i = row * cols + col;
                if (i < n) {
                    String rawId = "(" + nodes.get(i).getAddr().getId() + ")";
                    
                    // Centre the individual node string within the maxNodeWidth
                    int leftPad = (maxNodeWidth - rawId.length()) / 2;
                    int rightPad = maxNodeWidth - rawId.length() - leftPad;
                    String paddedNode = " ".repeat(leftPad) + rawId + " ".repeat(rightPad);
                    
                    rStr.append(paddedNode);
                    if (col < cols - 1 && i + 1 < n) rStr.append(" ══ ");
                    
                    // Draw vertical lines for the nodes that have a neighbour directly below them
                    if (i + cols < n) {
                        vStr.append(" ".repeat(barPos))
                            .append("║")
                            .append(" ".repeat(maxNodeWidth - barPos - 1));
                        
                        if (col < cols - 1) { 
                            vStr.append("    "); // Pad for the " ══ " horizontal gap
                        }
                    }
                }
            }
            System.out.println(rStr.toString());
            if (vStr.length() > 0) System.out.println(vStr.toString());
        }
    }

    private static void printHyperLattice(List<INode> nodes) {
        int n = nodes.size();
        System.out.println("       (4) ════════ (5)");
        System.out.println("       /║           /║");
        System.out.println("     (6) ════════ (7)║");
        System.out.println("      ║ ║          ║ ║");
        System.out.println("      ║(0) ════════║(1)");
        System.out.println("      ║/           ║/");
        System.out.println("     (2) ════════ (3)");
        if (n > 8) {
            System.out.println("\n   + " + (n - 8) + " nodes extending into higher dimensions...");
        } else if (n < 8) {
            System.out.println("\n   (Note: Add up to 8 nodes to complete the 3D Hypercube)");
        }
    }
}

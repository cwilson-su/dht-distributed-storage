package moduloHashing;

import metrics.MetricsLogger;

public class CoordinatorMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java moduloHashing.CoordinatorMain <coordinatorPort>");
            return;
        }
        
        MetricsLogger.configure("results/centralized_metrics.csv");
        int port = Integer.parseInt(args[0]);
        Coordinator coordinator = new Coordinator(port);
        coordinator.start();
    }
}
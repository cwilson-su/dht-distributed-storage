package moduloHashing;

public class CoordinatorMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java moduloHashing.CoordinatorMain <coordinatorPort>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        Coordinator coordinator = new Coordinator(port);
        coordinator.start();
    }
}
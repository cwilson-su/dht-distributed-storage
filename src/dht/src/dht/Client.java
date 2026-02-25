package dht;

import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java dht.Client <targetPort>");
            return;
        }
        
        int target = Integer.parseInt(args[0]); //target port
        Node tool = new Node(9999); // Temporary node to use its send() method
        Scanner sc = new Scanner(System.in);

        System.out.println("Client connected to Node " + target);
        System.out.println("Commands: PUT k v | GET k | exit");
        
        System.out.println("--- DHT Client (Connected to " + target + ") ---");

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line.equals("exit")) break;

            String[] parts = line.split(" ");
            Message m = new Message();

            String cmd = parts[0].toUpperCase();

            switch (cmd) {
                case "PUT" -> {
                    if (parts.length == 3) {
                        m.type = Message.Type.PUT;
                        m.k = parts[1]; 
                        m.v = parts[2];
                        tool.send(target, m);
                    }
                }
                case "GET" -> {
                    if (parts.length == 2) {
                        m.type = Message.Type.GET;
                        m.k = parts[1];
                        tool.send(target, m);
                    }
                }
                default -> System.out.println("Unknown command. Use PUT k v or GET k");
            }
        }
    }
}
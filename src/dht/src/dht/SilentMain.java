package dht;

import java.io.OutputStream;
import java.io.PrintStream;

public class SilentMain {
    public static void main(String[] args) {
        // Intercept and destroy all console output streams
        System.setOut(new PrintStream(new OutputStream() {
            @Override public void write(int b) { /* discard */ }
        }));
        System.setErr(new PrintStream(new OutputStream() {
            @Override public void write(int b) { /* discard */ }
        }));
        
        // Boot standard Main
        Main.main(args);
    }
}

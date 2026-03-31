package metrics;

import java.io.FileWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;


public class MetricsLogger {

    private static final String HEADER = "timestamp_ms,operation,latency_ms,hop_count,key,extra";

    private final PrintWriter writer;
    private final Object lock = new Object();

    private static volatile MetricsLogger instance;
    private static String configuredPath;

    
    public static void configure(String csvPath) {
        if (instance == null) {
            synchronized (MetricsLogger.class) {
                if (instance == null) {
                    try {
                        configuredPath = csvPath;
                        instance = new MetricsLogger(csvPath);
                    } catch (IOException e) {
                        System.err.println("[MetricsLogger] Cannot open CSV: " + e.getMessage());
                    }
                }
            }
        }
    }

    
    public static MetricsLogger get() {
        return instance != null ? instance : NOOP;
    }

   
    private MetricsLogger(String csvPath) throws IOException {
      
        Files.createDirectories(Paths.get(csvPath).getParent() != null
                ? Paths.get(csvPath).getParent()
                : Paths.get("."));

        boolean fileExists = Files.exists(Paths.get(csvPath));
        FileWriter fw = new FileWriter(csvPath, true);
        this.writer = new PrintWriter(fw, true);

        if (!fileExists) {
            writer.println(HEADER);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (lock) {
                writer.flush();
                writer.close();
            }
            System.out.println("[MetricsLogger] CSV fermé : " + csvPath);
        }, "MetricsLogger-Shutdown"));

        System.out.println("[MetricsLogger] Écriture dans : " + csvPath);
    }

    public void log(String operation, long latencyMs, int hopCount, String key, String extra) {
        long ts = Instant.now().toEpochMilli();
        String safeKey   = key   == null ? "" : key.replace(',', ';');
        String safeExtra = extra == null ? "" : extra.replace(',', ';');

        synchronized (lock) {
            writer.printf("%d,%s,%d,%d,%s,%s%n",
                    ts, operation, latencyMs, hopCount, safeKey, safeExtra);
        }
    }
    
    private static final MetricsLogger NOOP = new MetricsLogger();

    private MetricsLogger() {
        this.writer = new PrintWriter(System.out) {
            @Override public void println(String x) {} 
            @Override public PrintWriter printf(String f, Object... a) { return this; }
        };
    }
}
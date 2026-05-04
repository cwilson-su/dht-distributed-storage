package dht;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;

public class MetricsLogger {
    private static final String HDR = "ts_ms,op,lat_ms,hops,k,ext";
    private final PrintWriter pw;
    private final Object mtx = new Object();
    private static volatile MetricsLogger inst;

    public static void init(String path) {
        if (inst == null) {
            synchronized (MetricsLogger.class) {
                if (inst == null) {
                    try {
                        inst = new MetricsLogger(path);
                    } catch (IOException e) {
                        System.err.println("Failed CSV init: " + e.getMessage());
                    }
                }
            }
        }
    }

    public static MetricsLogger get() {
        return inst != null ? inst : NOOP;
    }

    private MetricsLogger(String path) throws IOException {
        Files.createDirectories(Paths.get(path).getParent() != null ? Paths.get(path).getParent() : Paths.get("."));
        boolean exists = Files.exists(Paths.get(path));
        this.pw = new PrintWriter(new FileWriter(path, true), true);

        if (!exists) pw.println(HDR);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (mtx) {
                pw.flush();
                pw.close();
            }
        }));
    }

    public void log(String op, long lat, int hops, String k, String ext) {
        long ts = Instant.now().toEpochMilli();
        String safeK = k == null ? "" : k.replace(',', ';');
        String safeExt = ext == null ? "" : ext.replace(',', ';');

        synchronized (mtx) {
            pw.printf("%d,%s,%d,%d,%s,%s%n", ts, op, lat, hops, safeK, safeExt);
        }
    }

    private static final MetricsLogger NOOP = new MetricsLogger();
    private MetricsLogger() {
        this.pw = new PrintWriter(System.out) {
            @Override public void println(String x) {}
            @Override public PrintWriter printf(String f, Object... a) { return this; }
        };
    }
}

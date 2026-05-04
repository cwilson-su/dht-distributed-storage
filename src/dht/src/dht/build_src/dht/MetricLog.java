package dht;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;

public class MetricLog {
    private static final String HDR = "ts_ms,op,lat_ms,hops,k,ext";
    private final PrintWriter pw;
    private final Object mtx = new Object();
    private static volatile MetricLog inst;

    public static void init(String pth) {
        if (inst == null) {
            synchronized (MetricLog.class) {
                if (inst == null) {
                    try { inst = new MetricLog(pth); } 
                    catch (IOException e) { System.err.println("CSV init fail: " + e.getMessage()); }
                }
            }
        }
    }

    public static MetricLog get() { return inst != null ? inst : NOOP; }

    private MetricLog(String pth) throws IOException {
        Files.createDirectories(Paths.get(pth).getParent() != null ? Paths.get(pth).getParent() : Paths.get("."));
        boolean ex = Files.exists(Paths.get(pth));
        this.pw = new PrintWriter(new FileWriter(pth, true), true);

        if (!ex) pw.println(HDR);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (mtx) { pw.flush(); pw.close(); }
        }));
    }

    public void log(String op, long lat, int hps, String k, String ext) {
        long ts = Instant.now().toEpochMilli();
        String sK = k == null ? "" : k.replace(',', ';');
        String sE = ext == null ? "" : ext.replace(',', ';');

        synchronized (mtx) { pw.printf("%d,%s,%d,%d,%s,%s%n", ts, op, lat, hps, sK, sE); }
    }

    private static final MetricLog NOOP = new MetricLog();
    private MetricLog() {
        this.pw = new PrintWriter(System.out) {
            @Override public void println(String x) {}
            @Override public PrintWriter printf(String f, Object... a) { return this; }
        };
    }
}

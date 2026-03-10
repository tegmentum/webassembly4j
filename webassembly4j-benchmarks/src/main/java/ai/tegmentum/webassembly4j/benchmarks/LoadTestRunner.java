package ai.tegmentum.webassembly4j.benchmarks;

import ai.tegmentum.webassembly4j.api.Engine;
import ai.tegmentum.webassembly4j.api.Function;
import ai.tegmentum.webassembly4j.api.Instance;
import ai.tegmentum.webassembly4j.api.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone load test runner that measures latency distributions, throughput
 * over time, and error rates for each engine variant under sustained load.
 *
 * <p>Unlike JMH benchmarks which focus on steady-state throughput, this tool
 * captures time-series data suitable for charting: throughput per second,
 * latency percentiles per interval, and cumulative error counts.</p>
 *
 * <p>Usage: {@code java LoadTestRunner [options]}
 * <ul>
 *   <li>{@code --duration <seconds>} - Test duration per variant (default: 30)</li>
 *   <li>{@code --threads <count>} - Concurrent threads (default: 4)</li>
 *   <li>{@code --warmup <seconds>} - Warmup duration (default: 5)</li>
 *   <li>{@code --output <file.json>} - Output file (default: load-test-results.json)</li>
 *   <li>{@code --workload <type>} - Workload: add, fibonacci, instantiate (default: add)</li>
 * </ul>
 */
public final class LoadTestRunner {

    private LoadTestRunner() {}

    public static void main(String[] args) throws Exception {
        int duration = 30;
        int threadCount = 4;
        int warmup = 5;
        int variantTimeout = 0; // 0 = auto (3x total test time)
        String output = "load-test-results.json";
        String workload = "add";
        Set<String> variantFilter = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--duration": duration = Integer.parseInt(args[++i]); break;
                case "--threads": threadCount = Integer.parseInt(args[++i]); break;
                case "--warmup": warmup = Integer.parseInt(args[++i]); break;
                case "--timeout": variantTimeout = Integer.parseInt(args[++i]); break;
                case "--output": output = args[++i]; break;
                case "--workload": workload = args[++i]; break;
                case "--variants":
                    variantFilter = new HashSet<>(Arrays.asList(args[++i].split(",")));
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
            }
        }

        if (variantTimeout <= 0) {
            variantTimeout = (duration + warmup) * 3;
        }

        System.out.printf("Load test: workload=%s, threads=%d, duration=%ds, warmup=%ds, timeout=%ds%n",
                workload, threadCount, duration, warmup, variantTimeout);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("workload", workload);
        root.put("threads", threadCount);
        root.put("durationSeconds", duration);
        root.put("warmupSeconds", warmup);
        ArrayNode variantResults = root.putArray("variants");

        for (EngineVariant variant : EngineVariant.values()) {
            if (variantFilter != null && !variantFilter.contains(variant.name())) {
                continue;
            }
            if (!BenchmarkSupport.isAvailable(variant)) {
                System.out.println("Skipping " + variant + " (not available)");
                continue;
            }

            System.out.println("Testing " + variant + "...");
            try {
                final int dur = duration, tc = threadCount, wu = warmup;
                final String wl = workload;
                final int timeout = variantTimeout;
                ExecutorService variantExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                });
                Future<ObjectNode> future = variantExecutor.submit(
                        () -> runVariant(mapper, variant, wl, tc, dur, wu));
                try {
                    ObjectNode result = future.get(timeout, TimeUnit.SECONDS);
                    variantResults.add(result);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    System.out.println("  " + variant + ": TIMED OUT after " + timeout + "s (skipped)");
                }
                variantExecutor.shutdownNow();
            } catch (Exception e) {
                System.out.println("  " + variant + ": FAILED - " + e.getMessage());
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(output), root);
        System.out.println("Results written to " + output);
    }

    private static ObjectNode runVariant(ObjectMapper mapper, EngineVariant variant,
                                         String workload, int threadCount,
                                         int durationSec, int warmupSec) throws Exception {
        Engine engine = BenchmarkSupport.createEngine(variant);
        Module addModule = engine.loadModule(BenchmarkModules.ADD_MODULE);
        Module fibModule = engine.loadModule(BenchmarkModules.FIBONACCI_MODULE);

        try {
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicLong totalOps = new AtomicLong();
            AtomicLong totalErrors = new AtomicLong();

            // Collect per-second snapshots
            List<SecondSnapshot> snapshots = Collections.synchronizedList(new ArrayList<>());

            // Per-thread latency recording (nanoseconds)
            ConcurrentLinkedQueue<long[]> allLatencies = new ConcurrentLinkedQueue<>();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();

            long startTime = System.nanoTime();
            long warmupEnd = startTime + TimeUnit.SECONDS.toNanos(warmupSec);
            long testEnd = warmupEnd + TimeUnit.SECONDS.toNanos(durationSec);

            // Snapshot collector thread
            Thread snapshotThread = new Thread(() -> {
                long lastOps = 0;
                long lastTime = warmupEnd;
                // Wait for warmup
                while (System.nanoTime() < warmupEnd && running.get()) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                }
                totalOps.set(0);
                totalErrors.set(0);

                while (running.get()) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                    long now = System.nanoTime();
                    long currentOps = totalOps.get();
                    long currentErrors = totalErrors.get();
                    double elapsed = (now - lastTime) / 1_000_000_000.0;
                    double opsPerSec = (currentOps - lastOps) / elapsed;
                    snapshots.add(new SecondSnapshot(
                            (now - warmupEnd) / 1_000_000_000.0,
                            opsPerSec,
                            currentOps,
                            currentErrors));
                    lastOps = currentOps;
                    lastTime = now;
                }
            });
            snapshotThread.setDaemon(true);
            snapshotThread.start();

            // Worker threads
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    Instance instance;
                    Function function;
                    Module module = "fibonacci".equals(workload) ? fibModule : addModule;
                    boolean isInstantiate = "instantiate".equals(workload);

                    if (!isInstantiate) {
                        instance = module.instantiate();
                        function = instance.function(
                                "fibonacci".equals(workload) ? "fibonacci" : "add").orElseThrow();
                    } else {
                        instance = null;
                        function = null;
                    }

                    // Pre-allocate latency buffer (estimate ~10M ops max)
                    long[] latencies = new long[1_000_000];
                    int latencyIdx = 0;
                    int counter = 0;
                    boolean pastWarmup = false;

                    try {
                        while (System.nanoTime() < testEnd) {
                            if (!pastWarmup && System.nanoTime() >= warmupEnd) {
                                pastWarmup = true;
                                latencyIdx = 0;
                            }

                            long opStart = System.nanoTime();
                            try {
                                if (isInstantiate) {
                                    module.instantiate();
                                } else if ("fibonacci".equals(workload)) {
                                    int n = 15 + (counter++ & 0x7);
                                    function.invoke(n);
                                } else {
                                    int a = counter++ & 0xFF;
                                    function.invoke(a, a + 1);
                                }
                                if (pastWarmup) {
                                    totalOps.incrementAndGet();
                                    long latency = System.nanoTime() - opStart;
                                    if (latencyIdx < latencies.length) {
                                        latencies[latencyIdx++] = latency;
                                    }
                                }
                            } catch (Exception e) {
                                if (pastWarmup) {
                                    long errCount = totalErrors.incrementAndGet();
                                    if (errCount == 1) {
                                        System.err.println("  First error in " + variant + ": " + e.getClass().getName() + ": " + e.getMessage());
                                    }
                                }
                            }
                        }
                    } finally {
                        // Instance lifecycle managed by Engine/Module
                    }
                    allLatencies.add(Arrays.copyOf(latencies, latencyIdx));
                }));
            }

            // Wait for completion
            for (Future<?> f : futures) {
                f.get();
            }
            running.set(false);
            snapshotThread.join(2000);
            executor.shutdown();

            // Merge and sort all latencies
            int totalLatencies = 0;
            for (long[] arr : allLatencies) totalLatencies += arr.length;
            long[] merged = new long[totalLatencies];
            int offset = 0;
            for (long[] arr : allLatencies) {
                System.arraycopy(arr, 0, merged, offset, arr.length);
                offset += arr.length;
            }
            Arrays.sort(merged);

            // Build JSON result
            ObjectNode result = mapper.createObjectNode();
            result.put("variant", variant.name());
            result.put("totalOps", totalOps.get());
            result.put("totalErrors", totalErrors.get());
            result.put("avgThroughput", totalOps.get() / (double) durationSec);

            // Latency percentiles (in microseconds)
            ObjectNode latencyNode = result.putObject("latency");
            if (merged.length > 0) {
                latencyNode.put("min", merged[0] / 1000.0);
                latencyNode.put("p50", percentile(merged, 50) / 1000.0);
                latencyNode.put("p90", percentile(merged, 90) / 1000.0);
                latencyNode.put("p95", percentile(merged, 95) / 1000.0);
                latencyNode.put("p99", percentile(merged, 99) / 1000.0);
                latencyNode.put("p999", percentile(merged, 99.9) / 1000.0);
                latencyNode.put("max", merged[merged.length - 1] / 1000.0);
                latencyNode.put("unit", "us");
            }

            // Latency histogram (log-scale buckets in microseconds)
            ObjectNode histogram = result.putObject("histogram");
            long[] bucketBounds = {1, 2, 5, 10, 20, 50, 100, 200, 500,
                    1000, 2000, 5000, 10000, 50000, 100000, 500000, 1000000};
            int[] bucketCounts = new int[bucketBounds.length + 1];
            for (long latNs : merged) {
                long latUs = latNs / 1000;
                int bucket = bucketBounds.length;
                for (int b = 0; b < bucketBounds.length; b++) {
                    if (latUs < bucketBounds[b]) {
                        bucket = b;
                        break;
                    }
                }
                bucketCounts[bucket]++;
            }
            ArrayNode bucketLabels = histogram.putArray("labels");
            ArrayNode bucketValues = histogram.putArray("counts");
            for (int b = 0; b < bucketBounds.length; b++) {
                bucketLabels.add("<" + bucketBounds[b] + "us");
                bucketValues.add(bucketCounts[b]);
            }
            bucketLabels.add(">=" + bucketBounds[bucketBounds.length - 1] + "us");
            bucketValues.add(bucketCounts[bucketBounds.length]);

            // Time series
            ArrayNode timeSeries = result.putArray("timeSeries");
            for (SecondSnapshot snap : snapshots) {
                ObjectNode point = mapper.createObjectNode();
                point.put("timeSeconds", Math.round(snap.timeSeconds * 10.0) / 10.0);
                point.put("opsPerSecond", Math.round(snap.opsPerSecond));
                point.put("cumulativeOps", snap.cumulativeOps);
                point.put("cumulativeErrors", snap.cumulativeErrors);
                timeSeries.add(point);
            }

            System.out.printf("  %s: %.0f ops/s, p50=%.1fus, p99=%.1fus, errors=%d%n",
                    variant,
                    totalOps.get() / (double) durationSec,
                    merged.length > 0 ? percentile(merged, 50) / 1000.0 : 0,
                    merged.length > 0 ? percentile(merged, 99) / 1000.0 : 0,
                    totalErrors.get());

            return result;
        } finally {
            addModule.close();
            fibModule.close();
            engine.close();
        }
    }

    private static double percentile(long[] sorted, double pct) {
        int index = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static final class SecondSnapshot {
        final double timeSeconds;
        final double opsPerSecond;
        final long cumulativeOps;
        final long cumulativeErrors;

        SecondSnapshot(double timeSeconds, double opsPerSecond, long cumulativeOps, long cumulativeErrors) {
            this.timeSeconds = timeSeconds;
            this.opsPerSecond = opsPerSecond;
            this.cumulativeOps = cumulativeOps;
            this.cumulativeErrors = cumulativeErrors;
        }
    }
}

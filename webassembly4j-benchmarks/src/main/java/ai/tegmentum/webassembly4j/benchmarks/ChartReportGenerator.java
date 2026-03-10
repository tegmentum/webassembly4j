package ai.tegmentum.webassembly4j.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Generates an interactive HTML report with Chart.js visualizations from
 * JMH JSON results and/or load test JSON results.
 *
 * <p>Produces bar charts for throughput comparisons, grouped bar charts for
 * concurrent scaling, latency percentile charts, latency histograms,
 * and throughput-over-time line charts.</p>
 *
 * <p>Usage: {@code java ChartReportGenerator [--jmh results.json] [--load load-test-results.json] output.html}</p>
 */
public final class ChartReportGenerator {

    private static final String CHART_JS_CDN = "https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js";

    private static final String[] COLORS = {
        "#2563eb", "#dc2626", "#16a34a", "#ca8a04",
        "#9333ea", "#0891b2", "#e11d48", "#65a30d"
    };

    private static final String[] VARIANT_ORDER = {
        "WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA",
        "WAMR_LLVM_JIT_JNI", "WAMR_LLVM_JIT_PANAMA", "GRAALWASM", "CHICORY"
    };

    private static final Map<String, String> VARIANT_LABELS = new LinkedHashMap<>();
    static {
        VARIANT_LABELS.put("WASMTIME_JNI", "Wasmtime JNI");
        VARIANT_LABELS.put("WASMTIME_PANAMA", "Wasmtime Panama");
        VARIANT_LABELS.put("WAMR_JNI", "WAMR JNI");
        VARIANT_LABELS.put("WAMR_PANAMA", "WAMR Panama");
        VARIANT_LABELS.put("WAMR_LLVM_JIT_JNI", "WAMR LLVM JNI");
        VARIANT_LABELS.put("WAMR_LLVM_JIT_PANAMA", "WAMR LLVM Panama");
        VARIANT_LABELS.put("GRAALWASM", "GraalWasm");
        VARIANT_LABELS.put("CHICORY", "Chicory");
    }

    private ChartReportGenerator() {}

    public static void main(String[] args) throws IOException {
        String jmhFile = null;
        String loadFile = null;
        String outputFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jmh": jmhFile = args[++i]; break;
                case "--load": loadFile = args[++i]; break;
                default: outputFile = args[i]; break;
            }
        }

        if (outputFile == null) {
            System.err.println("Usage: ChartReportGenerator [--jmh results.json] [--load load-test-results.json] output.html");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        PrintWriter out = new PrintWriter(outputFile);

        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("<meta charset=\"UTF-8\">");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.println("<title>WebAssembly4J Benchmark Report</title>");
        out.println("<script src=\"" + CHART_JS_CDN + "\"></script>");
        out.println("<style>");
        out.println(CSS);
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<h1>WebAssembly4J Benchmark Report</h1>");

        int chartId = 0;

        if (jmhFile != null) {
            JsonNode jmhResults = mapper.readTree(new File(jmhFile));
            chartId = generateJmhCharts(out, jmhResults, chartId);
        }

        if (loadFile != null) {
            JsonNode loadResults = mapper.readTree(new File(loadFile));
            chartId = generateLoadTestCharts(out, loadResults, chartId);
        }

        if (jmhFile == null && loadFile == null) {
            out.println("<p>No input files specified. Use --jmh and/or --load options.</p>");
        }

        out.println("</body>");
        out.println("</html>");
        out.flush();
        out.close();
        System.out.println("Report written to " + outputFile);
    }

    private static int generateJmhCharts(PrintWriter out, JsonNode results, int chartId) {
        out.println("<h2>JMH Benchmark Results</h2>");

        // Group by benchmark class -> method -> variant
        Map<String, Map<String, Map<String, Double>>> grouped = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();

        for (JsonNode result : results) {
            String fullName = result.get("benchmark").asText();
            JsonNode params = result.get("params");
            String variantName = params != null && params.has("variant")
                    ? params.get("variant").asText() : "UNKNOWN";

            String className = fullName.substring(fullName.lastIndexOf('.', fullName.lastIndexOf('.') - 1) + 1);
            int lastDot = className.lastIndexOf('.');
            String benchClass = className.substring(0, lastDot);
            String benchMethod = className.substring(lastDot + 1);

            // Include extra params
            StringBuilder methodKey = new StringBuilder(benchMethod);
            if (params != null) {
                Iterator<String> fieldNames = params.fieldNames();
                while (fieldNames.hasNext()) {
                    String field = fieldNames.next();
                    if (!"variant".equals(field) && !"threads".equals(field)) {
                        methodKey.append(" [").append(field).append("=")
                                .append(params.get(field).asText()).append("]");
                    }
                }
            }

            double score = result.get("primaryMetric").get("score").asDouble();
            String unit = result.get("primaryMetric").get("scoreUnit").asText();

            grouped.computeIfAbsent(benchClass, k -> new LinkedHashMap<>())
                    .computeIfAbsent(methodKey.toString(), k -> new LinkedHashMap<>())
                    .put(variantName, score);
            units.put(benchClass + "." + methodKey, unit);
        }

        // Generate a bar chart per benchmark class
        for (Map.Entry<String, Map<String, Map<String, Double>>> classEntry : grouped.entrySet()) {
            String benchClass = classEntry.getKey();
            Map<String, Map<String, Double>> methods = classEntry.getValue();

            out.println("<div class=\"chart-section\">");
            out.println("<h3>" + benchClass + "</h3>");

            // One chart per method
            for (Map.Entry<String, Map<String, Double>> methodEntry : methods.entrySet()) {
                String method = methodEntry.getKey();
                Map<String, Double> variantScores = methodEntry.getValue();
                String unit = units.getOrDefault(benchClass + "." + method, "ops/s");
                String canvasId = "chart_" + chartId++;

                out.println("<div class=\"chart-container\">");
                out.println("<h4>" + method + "</h4>");
                out.println("<canvas id=\"" + canvasId + "\"></canvas>");
                out.println("<script>");

                // Build ordered labels and data
                List<String> labels = new ArrayList<>();
                List<Double> data = new ArrayList<>();
                List<String> colors = new ArrayList<>();
                int colorIdx = 0;
                for (String v : VARIANT_ORDER) {
                    if (variantScores.containsKey(v)) {
                        labels.add(VARIANT_LABELS.getOrDefault(v, v));
                        data.add(variantScores.get(v));
                        colors.add(COLORS[colorIdx % COLORS.length]);
                    }
                    colorIdx++;
                }

                out.printf("new Chart(document.getElementById('%s'), {%n", canvasId);
                out.println("  type: 'bar',");
                out.println("  data: {");
                out.println("    labels: " + toJsonArray(labels) + ",");
                out.println("    datasets: [{");
                out.println("      label: '" + escape(method) + "',");
                out.println("      data: " + data + ",");
                out.println("      backgroundColor: " + toJsonArray(colors));
                out.println("    }]");
                out.println("  },");
                out.println("  options: {");
                out.println("    responsive: true,");
                out.println("    plugins: { legend: { display: false } },");
                out.println("    scales: {");
                out.println("      y: { beginAtZero: true, title: { display: true, text: '" + escape(unit) + "' } }");
                out.println("    }");
                out.println("  }");
                out.println("});");
                out.println("</script>");
                out.println("</div>");
            }
            out.println("</div>");
        }

        // Normalized comparison chart (all benchmarks on one chart)
        out.println("<div class=\"chart-section\">");
        out.println("<h3>Relative Performance (normalized to best)</h3>");
        String normalizedId = "chart_" + chartId++;
        out.println("<div class=\"chart-container chart-wide\">");
        out.println("<canvas id=\"" + normalizedId + "\"></canvas>");
        out.println("<script>");

        List<String> allMethods = new ArrayList<>();
        Map<String, List<Double>> normalizedData = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Map<String, Double>>> classEntry : grouped.entrySet()) {
            for (Map.Entry<String, Map<String, Double>> methodEntry : classEntry.getValue().entrySet()) {
                String label = classEntry.getKey().replace("Benchmark", "") + "." + methodEntry.getKey();
                allMethods.add(label);
                Map<String, Double> scores = methodEntry.getValue();
                double max = scores.values().stream().mapToDouble(d -> d).max().orElse(1.0);

                for (String v : VARIANT_ORDER) {
                    normalizedData.computeIfAbsent(v, k -> new ArrayList<>());
                    Double score = scores.get(v);
                    normalizedData.get(v).add(score != null ? score / max : 0.0);
                }
            }
        }

        out.printf("new Chart(document.getElementById('%s'), {%n", normalizedId);
        out.println("  type: 'bar',");
        out.println("  data: {");
        out.println("    labels: " + toJsonArray(allMethods) + ",");
        out.println("    datasets: [");
        int dsIdx = 0;
        for (String v : VARIANT_ORDER) {
            List<Double> values = normalizedData.get(v);
            if (values == null || values.stream().allMatch(d -> d == 0.0)) continue;
            if (dsIdx > 0) out.println(",");
            out.println("      {");
            out.println("        label: '" + escape(VARIANT_LABELS.getOrDefault(v, v)) + "',");
            out.println("        data: " + formatDoubles(values) + ",");
            out.println("        backgroundColor: '" + COLORS[dsIdx % COLORS.length] + "'");
            out.print("      }");
            dsIdx++;
        }
        out.println();
        out.println("    ]");
        out.println("  },");
        out.println("  options: {");
        out.println("    responsive: true,");
        out.println("    plugins: { legend: { position: 'bottom' } },");
        out.println("    scales: {");
        out.println("      y: { beginAtZero: true, max: 1.0, title: { display: true, text: 'Relative (1.0 = best)' } },");
        out.println("      x: { ticks: { maxRotation: 45, minRotation: 45 } }");
        out.println("    }");
        out.println("  }");
        out.println("});");
        out.println("</script>");
        out.println("</div>");
        out.println("</div>");

        return chartId;
    }

    private static int generateLoadTestCharts(PrintWriter out, JsonNode loadResults, int chartId) {
        out.println("<h2>Load Test Results</h2>");
        out.println("<p>Workload: <strong>" + loadResults.path("workload").asText("unknown") + "</strong>");
        out.println(" | Threads: <strong>" + loadResults.path("threads").asInt() + "</strong>");
        out.println(" | Duration: <strong>" + loadResults.path("durationSeconds").asInt() + "s</strong></p>");

        JsonNode variants = loadResults.get("variants");
        if (variants == null || !variants.isArray() || variants.size() == 0) {
            out.println("<p>No load test data available.</p>");
            return chartId;
        }

        // 1. Throughput comparison bar chart
        chartId = generateThroughputChart(out, variants, chartId);

        // 2. Latency percentile comparison
        chartId = generateLatencyPercentilesChart(out, variants, chartId);

        // 3. Latency histogram per variant
        chartId = generateLatencyHistograms(out, variants, chartId);

        // 4. Throughput over time (line chart)
        chartId = generateThroughputTimeline(out, variants, chartId);

        // 5. Summary table
        generateSummaryTable(out, variants);

        return chartId;
    }

    private static int generateThroughputChart(PrintWriter out, JsonNode variants, int chartId) {
        String canvasId = "chart_" + chartId++;
        out.println("<div class=\"chart-section\">");
        out.println("<h3>Throughput Comparison</h3>");
        out.println("<div class=\"chart-container\">");
        out.println("<canvas id=\"" + canvasId + "\"></canvas>");
        out.println("<script>");

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();
        List<String> colors = new ArrayList<>();

        for (JsonNode v : variants) {
            String name = v.get("variant").asText();
            labels.add(VARIANT_LABELS.getOrDefault(name, name));
            data.add(v.get("avgThroughput").asDouble());
            int idx = indexOf(VARIANT_ORDER, name);
            colors.add(COLORS[idx >= 0 ? idx % COLORS.length : 0]);
        }

        out.printf("new Chart(document.getElementById('%s'), {%n", canvasId);
        out.println("  type: 'bar',");
        out.println("  data: {");
        out.println("    labels: " + toJsonArray(labels) + ",");
        out.println("    datasets: [{ label: 'ops/s', data: " + formatDoubles(data) + ", backgroundColor: " + toJsonArray(colors) + " }]");
        out.println("  },");
        out.println("  options: {");
        out.println("    responsive: true,");
        out.println("    plugins: { legend: { display: false } },");
        out.println("    scales: { y: { beginAtZero: true, title: { display: true, text: 'Operations / second' } } }");
        out.println("  }");
        out.println("});");
        out.println("</script>");
        out.println("</div></div>");
        return chartId;
    }

    private static int generateLatencyPercentilesChart(PrintWriter out, JsonNode variants, int chartId) {
        String canvasId = "chart_" + chartId++;
        out.println("<div class=\"chart-section\">");
        out.println("<h3>Latency Percentiles</h3>");
        out.println("<div class=\"chart-container chart-wide\">");
        out.println("<canvas id=\"" + canvasId + "\"></canvas>");
        out.println("<script>");

        String[] percentiles = {"min", "p50", "p90", "p95", "p99", "p999", "max"};
        String[] pLabels = {"min", "p50", "p90", "p95", "p99", "p99.9", "max"};

        out.printf("new Chart(document.getElementById('%s'), {%n", canvasId);
        out.println("  type: 'line',");
        out.println("  data: {");
        out.println("    labels: " + toJsonArray(Arrays.asList(pLabels)) + ",");
        out.println("    datasets: [");

        int dsIdx = 0;
        for (JsonNode v : variants) {
            String name = v.get("variant").asText();
            JsonNode latency = v.get("latency");
            if (latency == null) continue;

            if (dsIdx > 0) out.println(",");
            List<Double> values = new ArrayList<>();
            for (String p : percentiles) {
                values.add(latency.has(p) ? latency.get(p).asDouble() : 0.0);
            }
            int idx = indexOf(VARIANT_ORDER, name);
            String color = COLORS[idx >= 0 ? idx % COLORS.length : dsIdx % COLORS.length];

            out.println("      {");
            out.println("        label: '" + escape(VARIANT_LABELS.getOrDefault(name, name)) + "',");
            out.println("        data: " + formatDoubles(values) + ",");
            out.println("        borderColor: '" + color + "',");
            out.println("        backgroundColor: '" + color + "22',");
            out.println("        tension: 0.3,");
            out.println("        fill: false");
            out.print("      }");
            dsIdx++;
        }
        out.println();
        out.println("    ]");
        out.println("  },");
        out.println("  options: {");
        out.println("    responsive: true,");
        out.println("    plugins: { legend: { position: 'bottom' } },");
        out.println("    scales: {");
        out.println("      y: { type: 'logarithmic', title: { display: true, text: 'Latency (us)' } }");
        out.println("    }");
        out.println("  }");
        out.println("});");
        out.println("</script>");
        out.println("</div></div>");
        return chartId;
    }

    private static int generateLatencyHistograms(PrintWriter out, JsonNode variants, int chartId) {
        out.println("<div class=\"chart-section\">");
        out.println("<h3>Latency Distribution</h3>");
        out.println("<div class=\"chart-grid\">");

        for (JsonNode v : variants) {
            String name = v.get("variant").asText();
            JsonNode histogram = v.get("histogram");
            if (histogram == null) continue;

            String canvasId = "chart_" + chartId++;
            int idx = indexOf(VARIANT_ORDER, name);
            String color = COLORS[idx >= 0 ? idx % COLORS.length : 0];

            out.println("<div class=\"chart-small\">");
            out.println("<h4>" + VARIANT_LABELS.getOrDefault(name, name) + "</h4>");
            out.println("<canvas id=\"" + canvasId + "\"></canvas>");
            out.println("<script>");

            JsonNode labels = histogram.get("labels");
            JsonNode counts = histogram.get("counts");

            List<String> labelList = new ArrayList<>();
            List<Integer> countList = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++) {
                labelList.add(labels.get(i).asText());
                countList.add(counts.get(i).asInt());
            }

            out.printf("new Chart(document.getElementById('%s'), {%n", canvasId);
            out.println("  type: 'bar',");
            out.println("  data: {");
            out.println("    labels: " + toJsonArray(labelList) + ",");
            out.println("    datasets: [{ data: " + countList + ", backgroundColor: '" + color + "' }]");
            out.println("  },");
            out.println("  options: {");
            out.println("    responsive: true,");
            out.println("    plugins: { legend: { display: false } },");
            out.println("    scales: {");
            out.println("      y: { beginAtZero: true, title: { display: true, text: 'Count' } },");
            out.println("      x: { ticks: { maxRotation: 45, minRotation: 45, font: { size: 9 } } }");
            out.println("    }");
            out.println("  }");
            out.println("});");
            out.println("</script>");
            out.println("</div>");
        }

        out.println("</div></div>");
        return chartId;
    }

    private static int generateThroughputTimeline(PrintWriter out, JsonNode variants, int chartId) {
        String canvasId = "chart_" + chartId++;
        out.println("<div class=\"chart-section\">");
        out.println("<h3>Throughput Over Time</h3>");
        out.println("<div class=\"chart-container chart-wide\">");
        out.println("<canvas id=\"" + canvasId + "\"></canvas>");
        out.println("<script>");

        // Find max time across all variants for x-axis
        out.printf("new Chart(document.getElementById('%s'), {%n", canvasId);
        out.println("  type: 'line',");
        out.println("  data: {");
        out.println("    datasets: [");

        int dsIdx = 0;
        for (JsonNode v : variants) {
            String name = v.get("variant").asText();
            JsonNode timeSeries = v.get("timeSeries");
            if (timeSeries == null || timeSeries.size() == 0) continue;

            if (dsIdx > 0) out.println(",");
            int idx = indexOf(VARIANT_ORDER, name);
            String color = COLORS[idx >= 0 ? idx % COLORS.length : dsIdx % COLORS.length];

            out.println("      {");
            out.println("        label: '" + escape(VARIANT_LABELS.getOrDefault(name, name)) + "',");
            out.print("        data: [");
            for (int i = 0; i < timeSeries.size(); i++) {
                JsonNode point = timeSeries.get(i);
                if (i > 0) out.print(", ");
                out.printf("{ x: %.1f, y: %.0f }",
                        point.get("timeSeconds").asDouble(),
                        point.get("opsPerSecond").asDouble());
            }
            out.println("],");
            out.println("        borderColor: '" + color + "',");
            out.println("        backgroundColor: '" + color + "22',");
            out.println("        tension: 0.3,");
            out.println("        fill: false,");
            out.println("        pointRadius: 2");
            out.print("      }");
            dsIdx++;
        }
        out.println();
        out.println("    ]");
        out.println("  },");
        out.println("  options: {");
        out.println("    responsive: true,");
        out.println("    plugins: { legend: { position: 'bottom' } },");
        out.println("    scales: {");
        out.println("      x: { type: 'linear', title: { display: true, text: 'Time (seconds)' } },");
        out.println("      y: { beginAtZero: true, title: { display: true, text: 'Operations / second' } }");
        out.println("    }");
        out.println("  }");
        out.println("});");
        out.println("</script>");
        out.println("</div></div>");
        return chartId;
    }

    private static void generateSummaryTable(PrintWriter out, JsonNode variants) {
        out.println("<div class=\"chart-section\">");
        out.println("<h3>Summary</h3>");
        out.println("<table class=\"summary-table\">");
        out.println("<thead><tr>");
        out.println("<th>Engine</th><th>Throughput (ops/s)</th>");
        out.println("<th>p50 (us)</th><th>p95 (us)</th><th>p99 (us)</th><th>p99.9 (us)</th>");
        out.println("<th>Max (us)</th><th>Errors</th>");
        out.println("</tr></thead>");
        out.println("<tbody>");

        for (JsonNode v : variants) {
            String name = v.get("variant").asText();
            JsonNode latency = v.get("latency");
            out.println("<tr>");
            out.printf("<td>%s</td>", VARIANT_LABELS.getOrDefault(name, name));
            out.printf("<td>%,.0f</td>", v.get("avgThroughput").asDouble());
            if (latency != null) {
                out.printf("<td>%.1f</td>", latency.path("p50").asDouble());
                out.printf("<td>%.1f</td>", latency.path("p95").asDouble());
                out.printf("<td>%.1f</td>", latency.path("p99").asDouble());
                out.printf("<td>%.1f</td>", latency.path("p999").asDouble());
                out.printf("<td>%.1f</td>", latency.path("max").asDouble());
            } else {
                out.print("<td>-</td><td>-</td><td>-</td><td>-</td><td>-</td>");
            }
            out.printf("<td>%d</td>", v.get("totalErrors").asLong());
            out.println("</tr>");
        }

        out.println("</tbody></table>");
        out.println("</div>");
    }

    // --- Helpers ---

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(escape(items.get(i))).append("'");
        }
        return sb.append("]").toString();
    }

    private static String formatDoubles(List<Double> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.2f", values.get(i)));
        }
        return sb.append("]").toString();
    }

    private static String escape(String s) {
        return s.replace("'", "\\'").replace("\n", "\\n");
    }

    private static int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return -1;
    }

    private static final String CSS = String.join("\n",
        "* { box-sizing: border-box; margin: 0; padding: 0; }",
        "body {",
        "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;",
        "  max-width: 1400px; margin: 0 auto; padding: 24px;",
        "  background: #f8fafc; color: #1e293b;",
        "}",
        "h1 { font-size: 28px; margin-bottom: 24px; color: #0f172a; }",
        "h2 { font-size: 22px; margin: 32px 0 16px; color: #1e293b; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px; }",
        "h3 { font-size: 18px; margin-bottom: 12px; color: #334155; }",
        "h4 { font-size: 14px; margin-bottom: 8px; color: #64748b; }",
        "p { margin-bottom: 16px; color: #475569; }",
        ".chart-section { margin-bottom: 40px; }",
        ".chart-container {",
        "  background: white; border-radius: 8px; padding: 20px;",
        "  box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 16px;",
        "  max-width: 700px;",
        "}",
        ".chart-container.chart-wide { max-width: 100%; }",
        ".chart-grid {",
        "  display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));",
        "  gap: 16px;",
        "}",
        ".chart-small {",
        "  background: white; border-radius: 8px; padding: 16px;",
        "  box-shadow: 0 1px 3px rgba(0,0,0,0.1);",
        "}",
        ".summary-table {",
        "  width: 100%; border-collapse: collapse; background: white;",
        "  border-radius: 8px; overflow: hidden;",
        "  box-shadow: 0 1px 3px rgba(0,0,0,0.1);",
        "}",
        ".summary-table th {",
        "  background: #f1f5f9; padding: 10px 14px; text-align: left;",
        "  font-size: 13px; color: #475569; border-bottom: 2px solid #e2e8f0;",
        "}",
        ".summary-table td {",
        "  padding: 10px 14px; border-bottom: 1px solid #f1f5f9;",
        "  font-size: 14px; font-variant-numeric: tabular-nums;",
        "}",
        ".summary-table tr:hover td { background: #f8fafc; }"
    );
}

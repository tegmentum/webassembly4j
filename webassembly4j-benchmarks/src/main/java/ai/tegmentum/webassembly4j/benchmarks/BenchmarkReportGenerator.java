package ai.tegmentum.webassembly4j.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public final class BenchmarkReportGenerator {

    private static final String[] VARIANT_ORDER = {
        "WASMTIME_JNI", "WASMTIME_PANAMA", "WAMR_JNI", "WAMR_PANAMA", "GRAALWASM", "CHICORY"
    };

    private static final Map<String, String> VARIANT_LABELS = new LinkedHashMap<>();
    static {
        VARIANT_LABELS.put("WASMTIME_JNI", "Wasmtime JNI");
        VARIANT_LABELS.put("WASMTIME_PANAMA", "Wasmtime Panama");
        VARIANT_LABELS.put("WAMR_JNI", "WAMR JNI");
        VARIANT_LABELS.put("WAMR_PANAMA", "WAMR Panama");
        VARIANT_LABELS.put("GRAALWASM", "GraalWasm");
        VARIANT_LABELS.put("CHICORY", "Chicory");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: BenchmarkReportGenerator <results.json> [output.md]");
            System.exit(1);
        }

        File inputFile = new File(args[0]);
        PrintWriter out = args.length > 1 ? new PrintWriter(args[1]) : new PrintWriter(System.out);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode results = mapper.readTree(inputFile);

        // Group results by benchmark class and method
        Map<String, Map<String, Map<String, BenchmarkResult>>> grouped = new LinkedHashMap<>();

        for (JsonNode result : results) {
            String fullName = result.get("benchmark").asText();
            JsonNode params = result.get("params");
            String variantName = params != null && params.has("variant")
                    ? params.get("variant").asText() : "UNKNOWN";

            String className = fullName.substring(fullName.lastIndexOf('.', fullName.lastIndexOf('.') - 1) + 1);
            int lastDot = className.lastIndexOf('.');
            String benchClass = className.substring(0, lastDot);
            String benchMethod = className.substring(lastDot + 1);

            // Include extra params in the method name
            StringBuilder methodKey = new StringBuilder(benchMethod);
            if (params != null) {
                Iterator<String> fieldNames = params.fieldNames();
                while (fieldNames.hasNext()) {
                    String field = fieldNames.next();
                    if (!"variant".equals(field)) {
                        methodKey.append(" [").append(field).append("=")
                                .append(params.get(field).asText()).append("]");
                    }
                }
            }

            double score = result.get("primaryMetric").get("score").asDouble();
            String unit = result.get("primaryMetric").get("scoreUnit").asText();

            grouped.computeIfAbsent(benchClass, k -> new LinkedHashMap<>())
                    .computeIfAbsent(methodKey.toString(), k -> new LinkedHashMap<>())
                    .put(variantName, new BenchmarkResult(score, unit));
        }

        // Generate Markdown report
        out.println("# WebAssembly4J Benchmark Report");
        out.println();

        for (Map.Entry<String, Map<String, Map<String, BenchmarkResult>>> classEntry : grouped.entrySet()) {
            out.println("## " + classEntry.getKey());
            out.println();

            // Header
            StringBuilder header = new StringBuilder("| Benchmark |");
            StringBuilder separator = new StringBuilder("|-----------|");
            for (String v : VARIANT_ORDER) {
                header.append(" ").append(VARIANT_LABELS.get(v)).append(" |");
                separator.append(pad(VARIANT_LABELS.get(v).length())).append("|");
            }
            out.println(header);
            out.println(separator);

            for (Map.Entry<String, Map<String, BenchmarkResult>> methodEntry : classEntry.getValue().entrySet()) {
                Map<String, BenchmarkResult> variantResults = methodEntry.getValue();

                // Find the fastest score for normalization
                double maxScore = variantResults.values().stream()
                        .mapToDouble(r -> r.score)
                        .max().orElse(1.0);

                StringBuilder row = new StringBuilder("| ").append(methodEntry.getKey()).append(" |");
                for (String v : VARIANT_ORDER) {
                    BenchmarkResult br = variantResults.get(v);
                    if (br == null) {
                        row.append(" N/A |");
                    } else {
                        double relative = br.score / maxScore;
                        row.append(String.format(" %.2fx |", relative));
                    }
                }
                out.println(row);
            }
            out.println();
        }

        // Absolute values section
        out.println("## Absolute Values");
        out.println();

        for (Map.Entry<String, Map<String, Map<String, BenchmarkResult>>> classEntry : grouped.entrySet()) {
            out.println("### " + classEntry.getKey());
            out.println();

            StringBuilder header = new StringBuilder("| Benchmark |");
            StringBuilder separator = new StringBuilder("|-----------|");
            for (String v : VARIANT_ORDER) {
                header.append(" ").append(VARIANT_LABELS.get(v)).append(" |");
                separator.append(pad(VARIANT_LABELS.get(v).length())).append("|");
            }
            out.println(header);
            out.println(separator);

            for (Map.Entry<String, Map<String, BenchmarkResult>> methodEntry : classEntry.getValue().entrySet()) {
                Map<String, BenchmarkResult> variantResults = methodEntry.getValue();
                StringBuilder row = new StringBuilder("| ").append(methodEntry.getKey()).append(" |");
                for (String v : VARIANT_ORDER) {
                    BenchmarkResult br = variantResults.get(v);
                    if (br == null) {
                        row.append(" N/A |");
                    } else {
                        row.append(String.format(" %.2f %s |", br.score, br.unit));
                    }
                }
                out.println(row);
            }
            out.println();
        }

        out.flush();
        if (args.length > 1) {
            out.close();
            System.out.println("Report written to " + args[1]);
        }
    }

    private static String pad(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length + 1; i++) {
            sb.append("-");
        }
        return sb.toString();
    }

    private static final class BenchmarkResult {
        final double score;
        final String unit;

        BenchmarkResult(double score, String unit) {
            this.score = score;
            this.unit = unit;
        }
    }
}

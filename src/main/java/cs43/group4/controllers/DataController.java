package cs43.group4.controllers;

import cs43.group4.utils.Log;
import io.javalin.http.Context;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataController {

    public void getBarangays(Context ctx) {
        Log.info("Barangay data requested");
        try {
            var csvPath = Path.of("data", "barangays.csv");
            if (!Files.exists(csvPath)) {
                Log.error("CSV not found at %s", csvPath.toAbsolutePath().toString());
                ctx.status(500)
                        .json(Map.of(
                                "status", "error",
                                "message", "barangays.csv not found"));
                return;
            }

            List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                ctx.json(Map.of("status", "success", "count", 0, "data", List.of()));
                return;
            }

            // Parse header, skip empty column names (e.g., the second column in the provided CSV)
            String[] rawHeader = splitCsv(lines.get(0));
            List<Integer> headerIdx = new ArrayList<>();
            List<String> headerNames = new ArrayList<>();
            for (int i = 0; i < rawHeader.length; i++) {
                String h = rawHeader[i] == null ? "" : rawHeader[i].trim();
                if (!h.isEmpty()) {
                    headerIdx.add(i);
                    headerNames.add(h);
                }
            }

            List<Map<String, Object>> data = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) continue;
                String[] row = splitCsv(line);
                Map<String, Object> obj = new HashMap<>();
                for (int c = 0; c < headerIdx.size(); c++) {
                    int col = headerIdx.get(c);
                    String key = headerNames.get(c);
                    String cell = col < row.length ? row[col] : "";
                    Object val = castValue(key, cell);
                    obj.put(key, val);
                }
                data.add(obj);
            }

            ctx.json(Map.of("status", "success", "count", data.size(), "data", data));
        } catch (IOException e) {
            Log.error("Failed to read barangays.csv: %s", e.getMessage(), e);
            ctx.status(500)
                    .json(Map.of(
                            "status", "error",
                            "message", "Failed to read barangays.csv"));
        } catch (Exception e) {
            Log.error("Unexpected error while parsing barangays.csv: %s", e.getMessage(), e);
            ctx.status(500)
                    .json(Map.of(
                            "status", "error",
                            "message", "Unexpected error while parsing CSV"));
        }
    }

    private static String[] splitCsv(String line) {
        String[] parts = line.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i] == null ? "" : parts[i].trim();
        }
        return parts;
    }

    private static Object castValue(String key, String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // Column-specific typing
        switch (key.toLowerCase()) {
            case "id":
            case "name":
            case "hazard_level_text":
                return s;
            case "flood_depth_ft":
            case "exposure":
            case "lat":
            case "lon":
                return parseDoubleOrNull(s);
            case "population":
            case "total_personnel":
            case "sar_current":
            case "ems_current":
                return parseIntOrNull(s);
            default:
                // Fallback: try number, else string
                Double d = parseDoubleOrNull(s);
                return d != null ? d : s;
        }
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseDoubleOrNull(String s) {
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

package cs43.group4.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV loader for thesis data.
 * - Reads data/barangays.csv and data/classes.csv
 * - Handles optional columns and derives exposure/AC when missing
 */
public final class DataLoader {

    public static final class Data {
        public final int Z; // barangays
        public final int C; // classes
        public final String[] barangayIds;
        public final String[] barangayNames;
        public final double[] r;   // hazard 1..3
        public final double[] f;   // flood depth (ft)
        public final double[] E;   // exposure
        public final double[] AC;  // adaptive capacity (total personnel in barangay)
        public final double[] sarCurrent; // optional per-barangay current SAR
        public final double[] emsCurrent; // optional per-barangay current EMS
    public final double[] lat; // optional latitude per barangay (degrees)
    public final double[] lon; // optional longitude per barangay (degrees)
        public final String[] classIds;
        public final String[] classNames;
        public final double[] lambda; // per class
        public final double[] supply; // per class

        public Data(int Z, int C,
                    String[] barangayIds, String[] barangayNames,
            double[] r, double[] f, double[] E, double[] AC,
            double[] sarCurrent, double[] emsCurrent,
            double[] lat, double[] lon,
                    String[] classIds, String[] classNames,
                    double[] lambda, double[] supply) {
            this.Z = Z; this.C = C;
            this.barangayIds = barangayIds; this.barangayNames = barangayNames;
            this.r = r; this.f = f; this.E = E; this.AC = AC;
        this.sarCurrent = sarCurrent; this.emsCurrent = emsCurrent;
        this.lat = lat; this.lon = lon;
            this.classIds = classIds; this.classNames = classNames;
            this.lambda = lambda; this.supply = supply;
        }
    }

    private static String[] splitCsv(String line) {
        // Simple split on commas; trim cells
        String[] parts = line.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    public static Data load(Path barangaysCsv, Path classesCsv) throws IOException {
        List<String> bLines = Files.readAllLines(barangaysCsv, StandardCharsets.UTF_8);
        List<String> cLines = Files.readAllLines(classesCsv, StandardCharsets.UTF_8);

        if (bLines.isEmpty() || cLines.isEmpty()) {
            throw new IOException("Empty CSV file(s)");
        }

        // Parse classes
        String[] cHeader = splitCsv(cLines.get(0));
        int idxClassId = indexOf(cHeader, "class_id");
        int idxClassName = indexOf(cHeader, "class_name");
        int idxLambda = indexOf(cHeader, "lambda");
        int idxSupply = indexOf(cHeader, "supply");
        List<String> classIds = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        List<Double> lambda = new ArrayList<>();
        List<Double> supply = new ArrayList<>();
        for (int i = 1; i < cLines.size(); i++) {
            if (cLines.get(i).isBlank()) continue;
            String[] row = splitCsv(cLines.get(i));
            classIds.add(get(row, idxClassId));
            classNames.add(get(row, idxClassName));
            lambda.add(parseDoubleSafe(get(row, idxLambda), 1.0));
            supply.add(parseDoubleSafe(get(row, idxSupply), 0.0));
        }
        int C = classIds.size();

        // Parse barangays
        String[] bHeader = splitCsv(bLines.get(0));
    int idxId = indexOf(bHeader, "id");
    int idxName = indexOf(bHeader, "name");
        int idxHazardText = indexOf(bHeader, "hazard_level_text");
        int idxDepthFt = indexOf(bHeader, "flood_depth_ft");
        int idxPopulation = indexOf(bHeader, "population");
        int idxExposure = indexOf(bHeader, "exposure");
        int idxTotalPersonnel = indexOf(bHeader, "total_personnel");
        int idxSarCurrent = indexOfOptional(bHeader, "sar_current");
        int idxEmsCurrent = indexOfOptional(bHeader, "ems_current");
    // Optional coordinates: support either lat/lon or latitude/longitude
    int idxLat = indexOfOptional(bHeader, "lat");
    if (idxLat < 0) idxLat = indexOfOptional(bHeader, "latitude");
    int idxLon = indexOfOptional(bHeader, "lon");
    if (idxLon < 0) idxLon = indexOfOptional(bHeader, "longitude");

        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Double> r = new ArrayList<>();
        List<Double> f = new ArrayList<>();
        List<Double> population = new ArrayList<>();
        List<Double> exposure = new ArrayList<>();
        List<Double> totalPersonnel = new ArrayList<>();
        List<Double> sarCur = new ArrayList<>();
        List<Double> emsCur = new ArrayList<>();
        List<Double> latList = new ArrayList<>();
        List<Double> lonList = new ArrayList<>();

        for (int i = 1; i < bLines.size(); i++) {
            String line = bLines.get(i);
            if (line.isBlank()) continue;
            String[] row = splitCsv(line);
            ids.add(get(row, idxId));
            names.add(get(row, idxName));
            r.add(hazardTextToLevel(get(row, idxHazardText)));
            f.add(parseDoubleSafe(get(row, idxDepthFt), 0.0));
            population.add(parseDoubleNullable(get(row, idxPopulation)));
            exposure.add(parseDoubleNullable(get(row, idxExposure)));
            totalPersonnel.add(parseDoubleNullable(get(row, idxTotalPersonnel)));
            if (idxSarCurrent >= 0) sarCur.add(parseDoubleNullable(get(row, idxSarCurrent))); else sarCur.add(null);
            if (idxEmsCurrent >= 0) emsCur.add(parseDoubleNullable(get(row, idxEmsCurrent))); else emsCur.add(null);
            if (idxLat >= 0) latList.add(parseDoubleNullable(get(row, idxLat))); else latList.add(null);
            if (idxLon >= 0) lonList.add(parseDoubleNullable(get(row, idxLon))); else lonList.add(null);
        }
        int Z = ids.size();

        // Build arrays and derive missing pieces
        double[] rArr = toPrimitive(r, 0.0);
        double[] fArr = toPrimitive(f, 0.0);

        // Exposure: if missing, use population normalized; else default 1.0
    double[] E = new double[Z];
    double[] popArr = new double[Z];
    double popSum = 0.0; int popCount = 0;
        for (int i = 0; i < Z; i++) {
            Double p = population.get(i);
            popArr[i] = p == null ? Double.NaN : p;
            if (p != null) { popSum += p; popCount++; }
        }
    double popMean = popCount > 0 ? popSum / popCount : 1.0;
        for (int i = 0; i < Z; i++) {
            Double e = exposure.get(i);
            if (e != null && e > 0) {
                E[i] = e;
            } else if (!Double.isNaN(popArr[i]) && popMean > 0) {
                E[i] = popArr[i] / popMean; // normalized population
            } else {
                E[i] = 1.0;
            }
        }

        // AC: if missing, estimate proportionally to population and a rough total personnel (sum of provided totals) if available
    double providedTotal = 0.0;
    for (Double tp : totalPersonnel) { if (tp != null) { providedTotal += tp; } }
        double fallbackTotal = providedTotal > 0 ? providedTotal : 1.0;

        double[] AC = new double[Z];
        for (int i = 0; i < Z; i++) {
            Double tp = totalPersonnel.get(i);
            if (tp != null) {
                AC[i] = tp;
            } else if (!Double.isNaN(popArr[i]) && popSum > 0) {
                AC[i] = (popArr[i] / popSum) * fallbackTotal;
            } else {
                AC[i] = 0.0;
            }
        }

        // Current per-class counts
        double[] sarCurrentArr = new double[Z];
        double[] emsCurrentArr = new double[Z];
        for (int i = 0; i < Z; i++) {
            Double s = sarCur.get(i);
            Double e = emsCur.get(i);
            if (s != null && e != null) {
                sarCurrentArr[i] = s; emsCurrentArr[i] = e;
            } else if (AC[i] > 0) {
                // Approximate split by hazard-level ratios if not provided
                double[] ratio = hazardSplitRatios(rArr[i]); // returns [sarRatio, emsRatio]
                sarCurrentArr[i] = ratio[0] * AC[i];
                emsCurrentArr[i] = Math.max(0.0, AC[i] - sarCurrentArr[i]);
            } else {
                sarCurrentArr[i] = 0.0; emsCurrentArr[i] = 0.0;
            }
        }

    return new Data(
        Z, C,
        ids.toArray(new String[ids.size()]),
        names.toArray(new String[names.size()]),
        rArr, fArr, E, AC,
        sarCurrentArr, emsCurrentArr,
        toPrimitive(latList, Double.NaN), toPrimitive(lonList, Double.NaN),
        classIds.toArray(new String[classIds.size()]),
        classNames.toArray(new String[classNames.size()]),
        toPrimitive(lambda, 1.0),
        toPrimitive(supply, 0.0)
    );
    }

    private static int indexOf(String[] arr, String key) throws IOException {
        int idx = indexOfOptional(arr, key);
        if (idx < 0) throw new IOException("Missing column: " + key);
        return idx;
    }

    private static int indexOfOptional(String[] arr, String key) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equalsIgnoreCase(key)) return i;
        }
        return -1;
    }

    private static String get(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return "";
        return row[idx];
    }

    private static double[] toPrimitive(List<Double> list, double defVal) {
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Double v = list.get(i);
            out[i] = v == null ? defVal : v;
        }
        return out;
    }

    private static Double parseDoubleNullable(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static double parseDoubleSafe(String s, double defVal) {
        Double v = parseDoubleNullable(s);
        return v == null ? defVal : v;
    }

    private static double hazardTextToLevel(String t) {
        String s = t == null ? "" : t.trim().toLowerCase();
        if (s.startsWith("low")) return 1.0;
        if (s.startsWith("med")) return 2.0;
        if (s.startsWith("high")) return 3.0;
        return 1.0; // default low
    }

    private static double[] hazardSplitRatios(double r) {
        // From manuscript table: High 85/15, Medium 75/25, Low 65/35 (SAR/EMS)
        if (r >= 2.5) return new double[]{0.85, 0.15};
        if (r >= 1.5) return new double[]{0.75, 0.25};
        return new double[]{0.65, 0.35};
    }
}

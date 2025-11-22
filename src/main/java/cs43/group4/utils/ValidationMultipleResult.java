package cs43.group4.utils;

import java.util.ArrayList;
import java.util.List;

public class ValidationMultipleResult {

    // === MEAN ACROSS RUNS (Average of Averages) ===
    public double meanPopulationCloseness;
    public double meanSarCloseness;
    public double meanEmsCloseness;
    public double meanHazardCloseness;
    public double meanCombinedCloseness;
    public double meanPopulationScore;

    // === STANDARD DEVIATION ACROSS RUNS ===
    public double stdPopulationCloseness;
    public double stdSarCloseness;
    public double stdEmsCloseness;
    public double stdHazardCloseness;
    public double stdCombinedCloseness;
    public double stdPopulationScore;

    // === MIN VALUES ACROSS RUNS ===
    public double minPopulationCloseness;
    public double minSarCloseness;
    public double minEmsCloseness;
    public double minHazardCloseness;
    public double minCombinedCloseness;
    public double minPopulationScore;

    // === MAX VALUES ACROSS RUNS ===
    public double maxPopulationCloseness;
    public double maxSarCloseness;
    public double maxEmsCloseness;
    public double maxHazardCloseness;
    public double maxCombinedCloseness;
    public double maxPopulationScore;

    // === Coefficient of Variation (Std Dev / Mean) ===
    public double cvPopulationCloseness;
    public double cvSarCloseness;
    public double cvEmsCloseness;
    public double cvHazardCloseness;
    public double cvCombinedCloseness;
    public double cvPopulationScore;

    public List<ValidationSingleResult> individualRuns;
    public List<PerBarangayMultiStats> perBarangayStats = new ArrayList<>();

    public static class PerBarangayMultiStats {
        public String barangayId;
        public String barangayName;

        public double meanPopulationCloseness;
        public double stdPopulationCloseness;
        public double cvPopulationCloseness;

        public double meanSarCloseness;
        public double stdSarCloseness;
        public double cvSarCloseness;

        public double meanEmsCloseness;
        public double stdEmsCloseness;
        public double cvEmsCloseness;

        public double meanHazardCloseness;
        public double stdHazardCloseness;
        public double cvHazardCloseness;

        public double meanCombinedCloseness;
        public double stdCombinedCloseness;
        public double cvCombinedCloseness;

        public PerBarangayMultiStats(String barangayId, String barangayName) {
            this.barangayId = barangayId;
            this.barangayName = barangayName;
        }
    }
}

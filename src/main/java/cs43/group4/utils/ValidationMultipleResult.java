package cs43.group4.utils;

import java.util.List;

public class ValidationMultipleResult {

    // === MEAN ACROSS RUNS (Average of Averages) ===
    public double meanPopulationCloseness;
    public double meanHazardCloseness;
    public double meanCombinedCloseness;
    public double meanPopulationScore;

    // === STANDARD DEVIATION ACROSS RUNS ===
    public double stdPopulationCloseness;
    public double stdHazardCloseness;
    public double stdCombinedCloseness;
    public double stdPopulationScore;

    // === MIN VALUES ACROSS RUNS ===
    public double minPopulationCloseness;
    public double minHazardCloseness;
    public double minCombinedCloseness;
    public double minPopulationScore;

    // === MAX VALUES ACROSS RUNS ===
    public double maxPopulationCloseness;
    public double maxHazardCloseness;
    public double maxCombinedCloseness;
    public double maxPopulationScore;

    // === OPTIONAL: Coefficient of Variation (Std Dev / Mean) ===
    public double cvPopulationCloseness;
    public double cvHazardCloseness;
    public double cvCombinedCloseness;
    public double cvPopulationScore;

    public List<ValidationSingleResult> individualRuns;

    public class PerBarangayMultiStats {
        public String barangayId;
        public String barangayName;

        public double meanPopulationCloseness;
        public double stdPopulationCloseness;

        public double meanHazardCloseness;
        public double stdHazardCloseness;

        public double meanCombinedCloseness;
        public double stdCombinedCloseness;
    }
}

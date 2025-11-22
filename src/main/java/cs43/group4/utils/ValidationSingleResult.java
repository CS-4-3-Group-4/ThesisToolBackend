package cs43.group4.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents validation results based on NDRRMC Gawad KALASAG standards
 */
public class ValidationSingleResult {
    // Main validation result fields
    public String standard = "NDRRMC Gawad KALASAG";
    public String baseline = "1:500 population-responder ratio";
    public List<BarangayValidation> barangayValidations = new ArrayList<>();
    public OverallStats overallStats;
    public String interpretation;
    public String error; // null if no error
    public long timestamp;

    // Barangay-level validation
    public static class BarangayValidation {
        public String barangayId;
        public String barangayName;
        public long population;
        public String hazardLevel;

        // Ideal values (based on 1:500 ratio and hazard distribution)
        public long idealTotal;
        public long idealSAR;
        public long idealEMS;

        // Actual values (from algorithm output)
        public long actualTotal;
        public long actualSAR;
        public long actualEMS;

        // Closeness scores (0-100%)
        public double populationCloseness;
        public double sarCloseness;
        public double emsCloseness;
        public double hazardCloseness;
        public double combinedCloseness;

        public int populationScore;

        public BarangayValidation() {}

        public BarangayValidation(String barangayId, String barangayName, long population, String hazardLevel) {
            this.barangayId = barangayId;
            this.barangayName = barangayName;
            this.population = population;
            this.hazardLevel = hazardLevel;
        }
    }

    public ValidationSingleResult() {
        this.timestamp = System.currentTimeMillis();
    }

    public boolean hasError() {
        return error != null;
    }

    /**
     * Generate human-readable interpretation for single run
     */
    public String generateSingleRunInterpretation() {
        if (overallStats == null) {
            return "Insufficient data for interpretation";
        }

        return String.format(
                "Across %d barangays, the algorithm achieved an average compliance score of %.1f%% "
                        + "relative to the Gawad KALASAG population-responder ratios and hazard-based SAR/EMS "
                        + "distribution standards. This indicates %s alignment with national benchmarks.",
                overallStats.totalBarangays,
                overallStats.averageCombinedCloseness,
                overallStats.getQualityRating().toLowerCase());
    }
}

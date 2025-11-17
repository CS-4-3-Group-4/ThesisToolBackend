package cs43.group4.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents validation results based on NDRRMC Gawad KALASAG standards
 */
public class ValidationResult {

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
        public double combinedScore;

        public BarangayValidation() {}

        public BarangayValidation(String barangayId, String barangayName, long population, String hazardLevel) {
            this.barangayId = barangayId;
            this.barangayName = barangayName;
            this.population = population;
            this.hazardLevel = hazardLevel;
        }
    }

    // Overall statistics across all barangays
    public static class OverallStats {
        public int totalBarangays;
        public double averagePopulationCloseness;
        public double averageHazardCloseness;
        public double averageCombinedScore;

        public OverallStats() {}

        public OverallStats(
                int totalBarangays, double avgPopCloseness, double avgHazardCloseness, double avgCombinedScore) {
            this.totalBarangays = totalBarangays;
            this.averagePopulationCloseness = avgPopCloseness;
            this.averageHazardCloseness = avgHazardCloseness;
            this.averageCombinedScore = avgCombinedScore;
        }

        public String getQualityRating() {
            if (averageCombinedScore >= 90) return "Excellent";
            if (averageCombinedScore >= 80) return "Strong";
            if (averageCombinedScore >= 70) return "Good";
            if (averageCombinedScore >= 60) return "Moderate";
            return "Needs Improvement";
        }
    }

    // Multi-run statistics
    public static class MultiRunStats {
        public int totalRuns;
        public int successfulRuns;
        public int failedRuns;

        public double meanScore;
        public double standardDeviation;
        public double minScore;
        public double maxScore;
        public double confidenceInterval95Lower;
        public double confidenceInterval95Upper;

        public List<RunScore> runScores = new ArrayList<>();

        public MultiRunStats() {}

        public String getConsistencyRating() {
            if (standardDeviation < 3) return "Highly Consistent";
            if (standardDeviation < 5) return "Consistent";
            if (standardDeviation < 8) return "Moderately Consistent";
            return "Variable";
        }
    }

    public static class RunScore {
        public int runNumber;
        public double validationScore;
        public double fitnessMaximization;
        public double fitnessMinimization;

        public RunScore() {}

        public RunScore(int runNumber, double validationScore, double fitnessMax, double fitnessMin) {
            this.runNumber = runNumber;
            this.validationScore = validationScore;
            this.fitnessMaximization = fitnessMax;
            this.fitnessMinimization = fitnessMin;
        }
    }

    // Main validation result fields
    public String standard = "NDRRMC Gawad KALASAG";
    public String baseline = "1:500 population-responder ratio";
    public List<BarangayValidation> barangayValidations = new ArrayList<>();
    public OverallStats overallStats;
    public MultiRunStats multiRunStats; // null for single run
    public String interpretation;
    public String error; // null if no error
    public long timestamp;

    public ValidationResult() {
        this.timestamp = System.currentTimeMillis();
    }

    public boolean hasError() {
        return error != null;
    }

    public boolean isMultiRun() {
        return multiRunStats != null;
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
                overallStats.averageCombinedScore,
                overallStats.getQualityRating().toLowerCase());
    }

    /**
     * Generate human-readable interpretation for multiple runs
     */
    public String generateMultiRunInterpretation() {
        if (multiRunStats == null) {
            return "Not a multi-run validation";
        }

        return String.format(
                "Across %d independent runs, the algorithm achieved an average validity score of %.1f%% "
                        + "(SD = %.1f%%), demonstrating %s performance and alignment with NDRRMC allocation standards.",
                multiRunStats.totalRuns,
                multiRunStats.meanScore,
                multiRunStats.standardDeviation,
                multiRunStats.getConsistencyRating().toLowerCase());
    }
}

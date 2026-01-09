package cs43.group4.stats;

/**
 * Represents a comparison between two barangay scores (typically FA vs EFA).
 */
public class BarangayComparison {
    public final String barangayId;
    public final String barangayName;
    public final BarangayScore baselineScore;  // FA
    public final BarangayScore comparisonScore; // EFA
    public final double percentageChange;

    public BarangayComparison(String barangayId, String barangayName,
                             BarangayScore baseline, BarangayScore comparison,
                             double percentageChange) {
        this.barangayId = barangayId;
        this.barangayName = barangayName;
        this.baselineScore = baseline;
        this.comparisonScore = comparison;
        this.percentageChange = percentageChange;
    }

    public boolean isImprovement() {
        return percentageChange > 0;
    }

    public boolean isNoChange() {
        return Math.abs(percentageChange) < 0.01;
    }

    public boolean isDegradation() {
        return percentageChange < 0;
    }
}

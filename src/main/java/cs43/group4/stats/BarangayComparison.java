package cs43.group4.stats;

/**
 * Represents a comparison between two barangay scores (typically FA vs EFA).
 */
public class BarangayComparison {
    public final String barangayId;
    public final String barangayName;
    public final BarangayScore barangayFAScore;  // FA
    public final BarangayScore barangayEFAScore; // EFA
    public final double percentageChange;

    public BarangayComparison(String barangayId, String barangayName,
                             BarangayScore barangayFAScore, BarangayScore barangayEFAScore,
                             double percentageChange) {
        this.barangayId = barangayId;
        this.barangayName = barangayName;
        this.barangayFAScore = barangayFAScore;
        this.barangayEFAScore = barangayEFAScore;
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

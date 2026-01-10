package cs43.group4.stats;

import cs43.group4.utils.MathUtils;

/**
 * Represents the solution quality evaluation for a single barangay.
 * BS_b = A_b / R_b (capped at 1.0)
 */
public class BarangayScore {
    public final String barangayId;
    public final String barangayName;
    public final long allocated;      // A_b: personnel assigned by algorithm
    public final long ideal;       // R_b: personnel required (Gawad Kalasag)
    public final double solutionQuality;        // BS_b: barangay score (0.0 to 1.0)
    public final String hazardLevel;  // High/Medium/Low/None

    public BarangayScore(String barangayId, String barangayName, String hazardLevel, long allocated, long ideal) {
        this.barangayId = barangayId;
        this.barangayName = barangayName;
        this.hazardLevel = hazardLevel;
        this.allocated = allocated;
        this.ideal = ideal;

        // BS_b = min(A_b / R_b, 1.0)
        if (ideal > 0) {
            // this.score = Math.min(1.0, (double) allocated / required);
            double solutionQuality = (double) (allocated - ideal) / ideal;
            this.solutionQuality = MathUtils.round(solutionQuality, 2);
        } else {
            // No personnel required (hazardLevel = "None")
            // Score = 1.0 if correctly allocated nothing (A_b = 0)
            // Score = 0.0 if wrongly allocated personnel (A_b > 0)
            this.solutionQuality = allocated == 0 ? 1.0 : 0.0;
        }
    }

    /**
     * Calculate percentage change compared to another barangay score.
     * ΔBS_b(%) = ((BS_EFA,b - BS_FA,b) / BS_FA,b) × 100
     */
    public double percentageChange(BarangayScore other) {
        double epsilon = 1e-6;
        double change = ((this.solutionQuality - other.solutionQuality) / (other.solutionQuality + epsilon)) * 100.0;
        return MathUtils.round(change, 2);
    }
}

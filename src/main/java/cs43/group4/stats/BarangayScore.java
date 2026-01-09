package cs43.group4.stats;


/**
 * Represents the solution quality evaluation for a single barangay.
 * BS_b = A_b / R_b (capped at 1.0)
 */
public class BarangayScore {
    public final String barangayId;
    public final String barangayName;
    public final long allocated;      // A_b: personnel assigned by algorithm
    public final long required;       // R_b: personnel required (Gawad Kalasag)
    public final double score;        // BS_b: barangay score (0.0 to 1.0)
    public final String hazardLevel;  // High/Medium/Low/None

    public BarangayScore(String barangayId, String barangayName, String hazardLevel, long allocated, long required) {
        this.barangayId = barangayId;
        this.barangayName = barangayName;
        this.hazardLevel = hazardLevel;
        this.allocated = allocated;
        this.required = required;

        // BS_b = min(A_b / R_b, 1.0)
        if (required > 0) {
            this.score = Math.min(1.0, (double) allocated / required);
        } else {
            // No personnel required (hazardLevel = "None")
            // Score = 1.0 if correctly allocated nothing (A_b = 0)
            // Score = 0.0 if wrongly allocated personnel (A_b > 0)
            this.score = allocated == 0 ? 1.0 : 0.0;
        }
    }

    /**
     * Calculate percentage change compared to another barangay score.
     * ΔBS_b(%) = ((BS_EFA,b - BS_FA,b) / BS_FA,b) × 100
     */
    public double percentageChange(BarangayScore other) {
        double epsilon = 1e-6;
        return ((this.score - other.score) / (other.score + epsilon)) * 100.0;
    }
}

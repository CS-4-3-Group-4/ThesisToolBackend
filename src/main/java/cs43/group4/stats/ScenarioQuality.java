package cs43.group4.stats;

import java.util.ArrayList;
import java.util.List;

import cs43.group4.utils.MathUtils;

/**
 * Represents the solution quality for a single scenario.
 * SQ_s = average of all barangay scores in the scenario
 */
public class ScenarioQuality {
    public final int scenarioNumber;
    public final List<BarangayScore> barangayScores;
    public final double solutionQuality;    // SQ_s: mean of all BS_b
    public final int totalBarangays;
    public final int affectedBarangays;
    public final long totalAllocated;
    public final long totalRequired;

    public ScenarioQuality(int scenarioNumber, List<BarangayScore> barangayScores) {
        this.scenarioNumber = scenarioNumber;
        this.barangayScores = new ArrayList<>(barangayScores);
        this.totalBarangays = barangayScores.size();

        // Calculate totals
        long sumAllocated = 0;
        long sumRequired = 0;
        double sumScores = 0.0;
        int affected = 0;

        for (BarangayScore bs : barangayScores) {
            sumAllocated += bs.allocated;
            sumRequired += bs.ideal;

            // Only include affected barangays (not "None") in SQ calculation
            if (!bs.hazardLevel.equals("None")) {
                sumScores += bs.solutionQuality;
                affected++;
            }
        }

        this.totalAllocated = sumAllocated;
        this.totalRequired = sumRequired;
        this.affectedBarangays = affected;

        // Use affected, not totalBarangays
        this.solutionQuality = MathUtils.round(affected > 0 ? sumScores / affected : 0.0, 2);
    }

    /**
     * Calculate percentage change in solution quality compared to another scenario.
     * ΔSQ_s(%) = ((SQ_EFA,s - SQ_FA,s) / SQ_FA,s) × 100
     */
    public double percentageChange(ScenarioQuality other) {
        double epsilon = 1e-6;
        double change = ((this.solutionQuality - other.solutionQuality) / (other.solutionQuality + epsilon)) * 100.0;
        return MathUtils.round(change, 2);
    }

    /**
     * Get barangay-level percentage changes for this scenario.
     */
    public List<BarangayComparison> getBarangayComparisons(ScenarioQuality other) {
        List<BarangayComparison> comparisons = new ArrayList<>();

        int minSize = Math.min(this.barangayScores.size(), other.barangayScores.size());
        for (int i = 0; i < minSize; i++) {
            BarangayScore thisScore = this.barangayScores.get(i);
            BarangayScore otherScore = other.barangayScores.get(i);

            comparisons.add(new BarangayComparison(
                thisScore.barangayId,
                thisScore.barangayName,
                otherScore,  // FA score
                thisScore,   // EFA score
                thisScore.percentageChange(otherScore)
            ));
        }

        return comparisons;
    }
}

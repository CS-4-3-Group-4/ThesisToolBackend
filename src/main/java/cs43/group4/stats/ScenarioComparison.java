package cs43.group4.stats;

import java.util.List;

/**
 * Represents scenario-level comparison with barangay details.
 */
public class ScenarioComparison {
    public final int scenarioNumber;
    public final double faSolutionQuality;
    public final double efaSolutionQuality;
    public final double percentageChange;  // Equation 3: ΔSQ_s(%)
    public final List<BarangayComparison> barangayComparisons;  // Equation 1.5: ΔBS_b(%)

    public ScenarioComparison(int scenarioNumber, double faSQ, double efaSQ,
                             double percentageChange, List<BarangayComparison> barangayComparisons) {
        this.scenarioNumber = scenarioNumber;
        this.faSolutionQuality = faSQ;
        this.efaSolutionQuality = efaSQ;
        this.percentageChange = percentageChange;
        this.barangayComparisons = barangayComparisons;
    }
}

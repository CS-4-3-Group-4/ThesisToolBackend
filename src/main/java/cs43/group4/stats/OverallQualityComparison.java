package cs43.group4.stats;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents the overall quality comparison across all scenarios (SOP 1 Result).
 * Includes Equation 4: Mean ΔSQ
 */
public class OverallQualityComparison {
    @JsonIgnore
    private final List<ScenarioQuality> faQualities;  // FA scenarios
    @JsonIgnore
    private final List<ScenarioQuality> efaQualities; // EFA scenarios
    public final List<Double> scenarioPercentageChanges;
    public final List<ScenarioComparison> scenarioComparisons;
    public final double meanPercentageChange;  // Main SOP 1 result
    // public final double stdDevPercentageChange;
    public final double minPercentageChange;
    public final double maxPercentageChange;

    // Summary statistics
    public final double faMeanSQ;
    public final double efaMeanSQ;
    public final int improvedScenarios;
    public final int unchangedScenarios;
    public final int degradedScenarios;

    public OverallQualityComparison(List<ScenarioQuality> faQualities,
                                   List<ScenarioQuality> efaQualities) {
        this.faQualities = new ArrayList<>(faQualities);
        this.efaQualities = new ArrayList<>(efaQualities);
        this.scenarioPercentageChanges = new ArrayList<>();
        this.scenarioComparisons = new ArrayList<>();

        // Calculate scenario-level percentage changes
        double sumChanges = 0.0;
        double minChange = Double.POSITIVE_INFINITY;
        double maxChange = Double.NEGATIVE_INFINITY;
        int improved = 0, unchanged = 0, degraded = 0;

        int minSize = Math.min(faQualities.size(), efaQualities.size());
        for (int i = 0; i < minSize; i++) {
            ScenarioQuality faScenario = faQualities.get(i);
            ScenarioQuality efaScenario = efaQualities.get(i);

            double change = efaScenario.percentageChange(faScenario);
            scenarioPercentageChanges.add(change);
            sumChanges += change;

            if (change < minChange) minChange = change;
            if (change > maxChange) maxChange = change;

            if (change > 0.01) improved++;
            else if (change < -0.01) degraded++;
            else unchanged++;

            List<BarangayComparison> barangayComps =
                efaScenario.getBarangayComparisons(faScenario);

            scenarioComparisons.add(new ScenarioComparison(
                faScenario.scenarioNumber,
                faScenario.solutionQuality,
                efaScenario.solutionQuality,
                change,
                barangayComps
            ));
        }


        this.meanPercentageChange = minSize > 0 ? sumChanges / minSize : 0.0;
        this.minPercentageChange = minSize > 0 ? minChange : 0.0;
        this.maxPercentageChange = minSize > 0 ? maxChange : 0.0;
        this.improvedScenarios = improved;
        this.unchangedScenarios = unchanged;
        this.degradedScenarios = degraded;

        // // Calculate standard deviation
        // double sumSquaredDiff = 0.0;
        // for (double change : scenarioPercentageChanges) {
        //     double diff = change - meanPercentageChange;
        //     sumSquaredDiff += diff * diff;
        // }
        // this.stdDevPercentageChange = minSize > 1 ?
        //     Math.sqrt(sumSquaredDiff / (minSize - 1)) : 0.0;

        // Calculate mean SQ for both algorithms
        double faSum = 0.0;
        double efaSum = 0.0;
        for (ScenarioQuality sq : faQualities) faSum += sq.solutionQuality;
        for (ScenarioQuality sq : efaQualities) efaSum += sq.solutionQuality;

        this.faMeanSQ = faQualities.size() > 0 ? faSum / faQualities.size() : 0.0;
        this.efaMeanSQ = efaQualities.size() > 0 ? efaSum / efaQualities.size() : 0.0;
    }

    public List<ScenarioQuality> getFaQualities() { return faQualities; }
    public List<ScenarioQuality> getEfaQualities() { return efaQualities; }
}

package cs43.group4.stats;

import cs43.group4.core.DataLoader;
import cs43.group4.utils.AllocationResult;
import java.util.List;


public class SolutionQualityIntegration {

    /**
     * STEP 1: Add this to FARunner/EFARunner after creating allocations
     */
    public static ScenarioQuality calculateQualityForRun(
            int scenarioNumber,
            List<AllocationResult> allocations,
            DataLoader.Data data) {

        List<BarangayScore> scores =
            SolutionQualityCalculator.calculateBarangayScores(allocations, data);

        return SolutionQualityCalculator.calculateScenarioQuality(
            scenarioNumber, scores);
    }

    /**
     * STEP 2: Add this endpoint to controller to compare FA vs EFA
     */
    public static OverallQualityComparison compareAlgorithms(
            List<ScenarioQuality> faQualities,
            List<ScenarioQuality> efaQualities) {

        return SolutionQualityCalculator.calculateOverallComparison(
            faQualities, efaQualities);
    }
}

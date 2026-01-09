package cs43.group4.stats;

import java.util.ArrayList;
import java.util.List;

import cs43.group4.core.DataLoader;
import cs43.group4.utils.AllocationResult;

/**
 * Calculator utility for solution quality evaluations.
 */
public class SolutionQualityCalculator {
    /**
     * Calculate barangay scores from allocations and requirements.
     */
    public static List<BarangayScore> calculateBarangayScores(
            List<AllocationResult> allocations,
            DataLoader.Data data) {

        List<BarangayScore> scores = new ArrayList<>();

        for (int i = 0; i < Math.min(allocations.size(), data.Z); i++) {
            AllocationResult allocation = allocations.get(i);

            // Get flood depth and determine hazard level
            double floodDepth = data.f[i];
            String hazardLevel = determineHazardLevel(floodDepth);

            // Calculate required personnel based on hazard level
            long required = calculateRequiredPersonnel(data, i);

            scores.add(new BarangayScore(
                allocation.id,
                allocation.name,
                hazardLevel,
                allocation.total,
                required
            ));
        }

        return scores;
    }

  /**
     * Calculate required personnel (R_b) based on flood depth and population.
     * Uses different ratios based on hazard level:
     * - High (>1.5m / 4.92126 ft): 1:500 (more responders needed)
     * - Medium (0.5-1.5m): 1:1000
     * - Low (0.2-0.5m): 1:2000
     * - None (<0.2m): 0 (no personnel needed)
     */
    private static long calculateRequiredPersonnel(DataLoader.Data data, int barangayIndex) {
        double floodDepth = data.f[barangayIndex]; // flood depth in ft
        double population = data.populations[barangayIndex];

        // High hazard: >1.5m (4.92126 ft) - 1:500 ratio
        if (floodDepth > 4.92126) {
            return (long) Math.ceil(population / 500.0);
        }

        // Medium hazard: 0.5m - 1.5m (1.64042 - 4.92126 ft) - 1:1000 ratio
        if (floodDepth > 1.64042) {
            return (long) Math.ceil(population / 1000.0);
        }

        // Low hazard: 0.2m - 0.5m (0.656168 - 1.64042 ft) - 1:2000 ratio
        if (floodDepth >= cs43.group4.core.Constants.UNAFFECTED_FLOOD_DEPTH_FT) {
            return (long) Math.ceil(population / 2000.0);
        }

        // None: <0.2m (0.656168 ft) - no personnel needed
        return 0;
    }

    /**
     * Get hazard level text for informational purposes.
     */
    public static String determineHazardLevel(double floodDepth) {
        if (floodDepth > 4.92126) return "High";
        if (floodDepth > 1.64042) return "Medium";
        if (floodDepth >= cs43.group4.core.Constants.UNAFFECTED_FLOOD_DEPTH_FT) return "Low";
        return "None";
    }

    /**
     * Calculate scenario quality from barangay scores.
     */
    public static ScenarioQuality calculateScenarioQuality(
            int scenarioNumber,
            List<BarangayScore> barangayScores) {
        return new ScenarioQuality(scenarioNumber, barangayScores);
    }

    /**
     * Calculate overall comparison between two sets of scenario qualities.
     */
    public static OverallQualityComparison calculateOverallComparison(
            List<ScenarioQuality> baseline,
            List<ScenarioQuality> comparison) {
        return new OverallQualityComparison(baseline, comparison);
    }
}

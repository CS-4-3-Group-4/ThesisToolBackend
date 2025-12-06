package cs43.group4.utils;

// Overall statistics across all barangays
public class OverallStats {
    public int totalBarangays;
    public double averagePopulationScore;
    public double averagePopulationCloseness;
    public double averageSarCloseness;
    public double averageEmsCloseness;
    public double averageHazardCloseness;
    public double averageCombinedCloseness;

    public OverallStats() {}

    public OverallStats(
            int totalBarangays,
            double avgPopScore,
            double avgPopCloseness,
            double avgSarCloseness,
            double avgEmsCloseness,
            double avgHazardCloseness,
            double avgCombinedCloseness) {
        this.totalBarangays = totalBarangays;
        this.averagePopulationScore = avgPopScore;
        this.averagePopulationCloseness = avgPopCloseness;
        this.averageSarCloseness = avgSarCloseness;
        this.averageEmsCloseness = avgEmsCloseness;
        this.averageHazardCloseness = avgHazardCloseness;
        this.averageCombinedCloseness = avgCombinedCloseness;
    }

    public String getQualityRating() {
        // Population closeness > 1.0 means more responders than minimum (better)
        // Population closeness < 1.0 means fewer responders than minimum (worse)

        if (averagePopulationCloseness >= 1.50) return "Excellent"; // 150%+ of minimum
        if (averagePopulationCloseness >= 1.25) return "Strong"; // 125%+ of minimum
        if (averagePopulationCloseness >= 1.00) return "Good"; // 100%+ of minimum
        if (averagePopulationCloseness >= 0.80) return "Moderate"; // 80%+ of minimum
        return "Needs Improvement"; // < 80% of minimum
    }
}

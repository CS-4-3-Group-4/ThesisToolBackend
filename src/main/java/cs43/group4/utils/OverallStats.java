package cs43.group4.utils;

// Overall statistics across all barangays
public class OverallStats {
    public int totalBarangays;
    public double averagePopulationCloseness;
    public double averageHazardCloseness;
    public double averageCombinedCloseness;
    public double averagePopulationScore;

    public OverallStats() {}

    public OverallStats(
            int totalBarangays,
            double avgPopScore,
            double avgPopCloseness,
            double avgHazardCloseness,
            double avgCombinedCloseness) {
        this.totalBarangays = totalBarangays;
        this.averagePopulationScore = avgPopScore;
        this.averagePopulationCloseness = avgPopCloseness;
        this.averageHazardCloseness = avgHazardCloseness;
        this.averageCombinedCloseness = avgCombinedCloseness;
    }

    public String getQualityRating() {
        if (averageCombinedCloseness >= 90) return "Excellent";
        if (averageCombinedCloseness >= 80) return "Strong";
        if (averageCombinedCloseness >= 70) return "Good";
        if (averageCombinedCloseness >= 60) return "Moderate";
        return "Needs Improvement";
    }
}

package cs43.group4.controllers;

import cs43.group4.stats.OverallQualityComparison;
import cs43.group4.stats.ScenarioQuality;
import cs43.group4.stats.SolutionQualityIntegration;
import cs43.group4.utils.Log;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;

public class QualityController {
    private List<ScenarioQuality> faQualities;
    private List<ScenarioQuality> efaQualities;

    public void setFAQualities(List<ScenarioQuality> qualities) {
        this.faQualities = qualities;
        Log.info("Stored FA solution qualities: " + qualities.size() + " scenarios");
    }

    public void setEFAQualities(List<ScenarioQuality> qualities) {
        this.efaQualities = qualities;
        Log.info("Stored EFA solution qualities: " + qualities.size() + " scenarios");
    }

    /**
     * GET /quality/comparison
     * Compare FA vs EFA solution quality (SOP 1 result)
     */
    public void getComparison(Context ctx) {
        Log.info("Solution quality comparison requested");

        if (faQualities == null || faQualities.isEmpty()) {
            ctx.status(400).json(Map.of("error", "FA multiple run must be completed first"));
            return;
        }

        if (efaQualities == null || efaQualities.isEmpty()) {
            ctx.status(400).json(Map.of("error", "EFA multiple run must be completed first"));
            return;
        }

        OverallQualityComparison comparison =
            SolutionQualityIntegration.compareAlgorithms(faQualities, efaQualities);

        ctx.json(comparison);
    }

    /**
     * GET /quality/fa
     * Get FA solution qualities
     */
    public void getFAQualities(Context ctx) {
        if (faQualities == null || faQualities.isEmpty()) {
            ctx.status(404).json(Map.of("error", "No FA solution qualities available"));
            return;
        }
        ctx.json(Map.of("qualities", faQualities));
    }

    /**
     * GET /quality/efa
     * Get EFA solution qualities
     */
    public void getEFAQualities(Context ctx) {
        if (efaQualities == null || efaQualities.isEmpty()) {
            ctx.status(404).json(Map.of("error", "No EFA solution qualities available"));
            return;
        }
        ctx.json(Map.of("qualities", efaQualities));
    }
}

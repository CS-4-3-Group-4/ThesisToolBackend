package cs43.group4;

import cs43.group4.controllers.FAController;
import cs43.group4.utils.Log;
import io.javalin.Javalin;
import java.util.Map;

public class Main {
    private static final int PORT = 8080;

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
                    config.http.defaultContentType = "application/json";
                    config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
                })
                .start(PORT);

        FAController faController = new FAController();

        app.get("/health", ctx -> {
            Log.info("Health check requested");
            ctx.json(Map.of("status", "UP"));
        });

        Log.info("═══════════════════════════════════════════════════════════");
        Log.info("Server started successfully on http://localhost:%d", PORT);
        Log.info("═══════════════════════════════════════════════════════════");
        Log.info("API Endpoints:");
        Log.info("  Utility:");
        Log.info("    GET  /health                             - Health check");
        Log.info("");
        Log.info("  FA Algorithm:");
        Log.info("    General:");
        Log.info("      GET  /fa/status                        - Get current status");
        Log.info("      POST /fa/stop                          - Stop running algorithm");
        Log.info("      GET  /fa/results                       - Get results");
        Log.info("      GET  /fa/iterations                    - Get iteration history");
        Log.info("");
        Log.info("    Single Run:");
        Log.info("      POST /fa/single/run                    - Start single run");
        Log.info("");
        Log.info("    Multiple Runs:");
        Log.info("      POST /fa/multiple/run?runs=N           - Start N runs (2-100)");
        Log.info("");
        Log.info("    Downloads:");
        Log.info("      GET  /fa/download/flows                - Download flows CSV");
        Log.info("      GET  /fa/download/allocations          - Download allocations CSV");
        Log.info("");
        Log.info("  EFA Algorithm:");
        Log.info("    Control:");
        Log.info("      (Coming soon)");
        Log.info("    Results:");
        Log.info("      (Coming soon)");
        Log.info("    Downloads:");
        Log.info("      (Coming soon)");
        Log.info("═══════════════════════════════════════════════════════════");

        // General endpoints (work for both single and multiple runs)
        app.get("/fa/status", faController::getStatus);
        app.post("/fa/stop", faController::postStop);
        app.get("/fa/results", faController::getResults);
        app.get("/fa/iterations", faController::getIterations);

        // Single run
        app.post("/fa/single/run", faController::postSingleRun);

        // Multiple runs
        app.post("/fa/multiple/run", faController::postMultipleRun);
    }
}

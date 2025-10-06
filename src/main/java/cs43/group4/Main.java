package cs43.group4;

import cs43.group4.controllers.EFAController;
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
        EFAController efaController = new EFAController();

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
        Log.info("    Data:");
        Log.info("      GET  /fa/allocations                   - Get allocation details");
        Log.info("      GET  /fa/flows                         - Get flow details");
        Log.info("");
        Log.info("");
        Log.info("  EFA Algorithm:");
        Log.info("    General:");
        Log.info("      GET  /efa/status                       - Get current status");
        Log.info("      POST /efa/stop                         - Stop running algorithm");
        Log.info("      GET  /efa/results                      - Get results");
        Log.info("      GET  /efa/iterations                   - Get iteration history");
        Log.info("");
        Log.info("    Single Run:");
        Log.info("      POST /efa/single/run                   - Start single run");
        Log.info("");
        Log.info("    Multiple Runs:");
        Log.info("      POST /efa/multiple/run?runs=N          - Start N runs (2-100)");
        Log.info("");
        Log.info("    Data:");
        Log.info("      GET  /efa/allocations                  - Get allocation details");
        Log.info("      GET  /efa/flows                        - Get flow details");
        Log.info("═══════════════════════════════════════════════════════════");

        // ========== FA ENDPOINTS ==========

        // General endpoints (work for both single and multiple runs)
        app.get("/fa/status", faController::getStatus);
        app.post("/fa/stop", faController::postStop);
        app.get("/fa/results", faController::getResults);
        app.get("/fa/iterations", faController::getIterations);

        // Single run
        app.post("/fa/single/run", faController::postSingleRun);

        // Multiple runs
        app.post("/fa/multiple/run", faController::postMultipleRun);

        // Data endpoints (single run only)
        app.get("/fa/allocations", faController::getAllocations);
        app.get("/fa/flows", faController::getFlows);

        // ========== EFA ENDPOINTS ==========

        // General endpoints (work for both single and multiple runs)
        app.get("/efa/status", efaController::getStatus);
        app.post("/efa/stop", efaController::postStop);
        app.get("/efa/results", efaController::getResults);
        app.get("/efa/iterations", efaController::getIterations);

        // Single run
        app.post("/efa/single/run", efaController::postSingleRun);

        // Multiple runs
        app.post("/efa/multiple/run", efaController::postMultipleRun);

        // Data endpoints (single run only)
        app.get("/efa/allocations", efaController::getAllocations);
        app.get("/efa/flows", efaController::getFlows);
    }
}

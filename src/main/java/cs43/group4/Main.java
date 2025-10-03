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
        Log.info("  Algorithm Control:");
        Log.info("    GET  /algorithm/status                   - Get algorithm status");
        Log.info("    POST /algorithm/run                      - Start algorithm");
        Log.info("    POST /algorithm/stop                     - Stop running algorithm");
        Log.info("  Results:");
        Log.info("    GET  /algorithm/results                  - Get final results");
        Log.info("    GET  /algorithm/iterations               - Get iteration history");
        Log.info("  Downloads:");
        Log.info("    GET  /algorithm/download/flows           - Download flows CSV");
        Log.info("    GET  /algorithm/download/allocations     - Download allocations CSV");
        Log.info("═══════════════════════════════════════════════════════════");

        app.get("/algorithm/status", faController::getStatus);
        app.post("/algorithm/run", faController::postRun);
        app.post("/algorithm/stop", faController::postStop);
        app.get("/algorithm/results", faController::getResults);
        app.get("/algorithm/iterations", faController::getIterations);
    }
}

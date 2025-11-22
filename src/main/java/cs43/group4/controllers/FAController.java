package cs43.group4.controllers;

import cs43.group4.FARunner;
import cs43.group4.parameters.FAParams;
import cs43.group4.utils.Log;
import io.javalin.http.Context;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FAController {
    private FARunner runner = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ========== GENERAL ENDPOINTS (work for both single and multiple) ==========

    public void getStatus(Context ctx) {
        Log.info("FA algorithm status requested");
        if (runner == null) {
            ctx.json(Map.of("status", "idle", "message", "No algorithm running"));
        } else {
            ctx.json(runner.getStatus());
        }
    }

    public void postStop(Context ctx) {
        Log.info("FA stop requested");

        if (runner != null && runner.isRunning()) {
            Log.warn("FA algorithm stopped by user");
            runner.stop();
            ctx.json(Map.of("message", "Algorithm stopped"));
        } else {
            Log.debug("Stop requested but no FA algorithm running");
            ctx.status(400).json(Map.of("error", "No running algorithm to stop"));
        }
    }

    public void getResults(Context ctx) {
        Log.info("FA results requested");

        if (runner == null) {
            ctx.status(404).json(Map.of("error", "No algorithm has been run"));
        } else if (runner.isRunning()) {
            ctx.status(400).json(Map.of("error", "Algorithm still running"));
        } else {
            ctx.json(runner.getResults());
        }
    }

    public void getIterations(Context ctx) {
        Log.info("FA iteration history requested");

        if (runner == null) {
            ctx.status(404).json(Map.of("error", "No algorithm has been run"));
        } else {
            Map<String, Object> status = runner.getStatus();
            if ("multiple".equals(status.get("mode"))) {
                ctx.json(Map.of(
                        "error", "Iteration history not available for multiple runs",
                        "suggestion", "Use /fa/results to see aggregated statistics"));
            } else {
                ctx.json(Map.of("iterations", runner.getIterationHistory()));
            }
        }
    }

    // ========== SINGLE RUN ==========

    public void postSingleRun(Context ctx) {
        Log.info("FA single run requested");

        if (runner != null && runner.isRunning()) {
            Log.warn("Attempted to start FA run while one is already active");
            ctx.status(409).json(Map.of("error", "Algorithm already running"));
            return;
        }

        try {
            FAParams params = parseParams(ctx);
            Log.debug("FA single run parameters: " + params.toString());

            runner = new FARunner(params);
            executor.submit(() -> {
                try {
                    runner.run();
                    Log.info("FA single run completed successfully");
                } catch (Exception e) {
                    Log.error("FA single run failed: %s", e.getMessage(), e);
                    if (runner != null) runner.setError(e.getMessage());
                }
            });

            ctx.json(Map.of("message", "Single run started"));
        } catch (IllegalArgumentException e) {
            handleInvalidParams(ctx, e);
        }
    }

    // ========== MULTIPLE RUNS ==========

    public void postMultipleRun(Context ctx) {
        Log.info("FA multiple runs requested");

        if (runner != null && runner.isRunning()) {
            Log.warn("Attempted to start FA run while one is already active");
            ctx.status(409).json(Map.of("error", "Algorithm already running"));
            return;
        }

        try {
            FAParams params = parseParams(ctx);

            // Get number of runs from query parameter
            String runsParam = ctx.queryParam("runs");
            if (runsParam == null || runsParam.isBlank()) {
                ctx.status(400)
                        .json(Map.of(
                                "error", "Missing runs parameter",
                                "details", "Use ?runs=N where N is between 2 and 100"));
                return;
            }

            int numRuns;
            try {
                numRuns = Integer.parseInt(runsParam);
            } catch (NumberFormatException e) {
                ctx.status(400)
                        .json(Map.of(
                                "error", "Invalid runs parameter",
                                "details", "Must be a valid integer"));
                return;
            }

            Log.debug("FA multiple runs parameters: " + params.toString());
            Log.info("Starting " + numRuns + " FA runs");

            runner = new FARunner(params);
            executor.submit(() -> {
                try {
                    runner.runMultiple(numRuns);
                    Log.info("FA multiple runs completed successfully");
                } catch (Exception e) {
                    Log.error("FA multiple runs failed: %s", e.getMessage(), e);
                    if (runner != null) runner.setError(e.getMessage());
                }
            });

            ctx.json(Map.of("message", "Multiple runs started", "totalRuns", numRuns));

        } catch (IllegalArgumentException e) {
            handleInvalidParams(ctx, e);
        }
    }

    public void getAllocations(Context ctx) {
        Log.info("FA allocations requested");

        if (runner == null) {
            ctx.status(404).json(Map.of("error", "No algorithm has been run"));
        } else if (runner.isRunning()) {
            ctx.status(400).json(Map.of("error", "Algorithm still running"));
        } else {
            Map<String, Object> status = runner.getStatus();
            if ("multiple".equals(status.get("mode"))) {
                ctx.json(Map.of(
                        "error", "Allocations not available for multiple runs",
                        "suggestion", "Only available for single runs"));
            } else {
                ctx.json(Map.of("allocations", runner.getAllocations()));
            }
        }
    }

    public void getFlows(Context ctx) {
        Log.info("FA flows requested");

        if (runner == null) {
            ctx.status(404).json(Map.of("error", "No algorithm has been run"));
        } else if (runner.isRunning()) {
            ctx.status(400).json(Map.of("error", "Algorithm still running"));
        } else {
            Map<String, Object> status = runner.getStatus();
            if ("multiple".equals(status.get("mode"))) {
                ctx.json(Map.of(
                        "error", "Flows not available for multiple runs",
                        "suggestion", "Only available for single runs"));
            } else {
                ctx.json(Map.of("flows", runner.getFlows()));
            }
        }
    }

    public void getValidationReport(Context ctx) {
        Log.info("FA validation report requested");

        if (runner == null) {
            ctx.status(404).json(Map.of("error", "No algorithm has been run"));
        } else if (runner.isRunning()) {
            ctx.status(400).json(Map.of("error", "Algorithm still running"));
        } else {
            Map<String, Object> status = runner.getStatus();
            if ("multiple".equals(status.get("mode"))) {
                // For multiple runs, return the multiple validation report
                ctx.json(Map.of("validationReport", runner.getValidationMultipleReport()));
            } else {
                // For single run, return the single validation report
                ctx.json(Map.of("validationReport", runner.getValidationSingleReport()));
            }
        }
    }

    // ========== HELPER METHODS ==========

    private FAParams parseParams(Context ctx) {
        if (ctx.body().isBlank()) {
            return new FAParams(); // use defaults
        } else {
            FAParams params = ctx.bodyAsClass(FAParams.class);
            params.validate();
            return params;
        }
    }

    private void handleInvalidParams(Context ctx, IllegalArgumentException e) {
        Log.error("Invalid FA parameters: %s", e.getMessage());
        ctx.status(400).json(Map.of("error", "Invalid parameters", "details", e.getMessage()));
    }
}

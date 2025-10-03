package cs43.group4.controllers;

import cs43.group4.AlgorithmParams;
import cs43.group4.FARunner;
import cs43.group4.utils.Log;
import io.javalin.http.Context;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FAController {
    private FARunner faRunner = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void getStatus(Context ctx) {
        Log.info("Algorithm status requested");
        if (faRunner == null) {
            ctx.json(Map.of("status", "idle", "message", "No algorithm running"));
        } else {
            ctx.json(faRunner.getStatus());
        }
    }

    public void postRun(Context ctx) {
        if (faRunner != null && faRunner.isRunning()) {
            Log.warn("Attempted to start algorithm while one is already running");
            ctx.status(409).json(Map.of("error", "Algorithm already running"));
            return;
        }

        try {
            Log.info("Algorithm run requested");
            AlgorithmParams params;
            if (ctx.body().isBlank()) {
                params = new AlgorithmParams(); // use defaults
            } else {
                params = ctx.bodyAsClass(AlgorithmParams.class);
            }

            params.validate();

            Log.debug("Algorithm parameters: " + params.toString());

            faRunner = new FARunner(params);

            executor.submit(() -> {
                try {
                    faRunner.run();
                    Log.info("Algorithm completed successfully");
                } catch (Exception e) {
                    Log.error("Algorithm failed with error: %s", e.getMessage(), e);
                    if (faRunner != null) faRunner.setError(e.getMessage());
                }
            });

            ctx.json(Map.of("message", "Algorithm started"));

        } catch (IllegalArgumentException e) {
            Log.error("Invalid parameters: %s", e.getMessage());
            ctx.status(400).json(Map.of("error", "Invalid parameters", "details", e.getMessage()));
        }
    }

    public void postStop(Context ctx) {
        if (faRunner != null && faRunner.isRunning()) {
            Log.warn("Algorithm stopped by user request");
            faRunner.stop();
            faRunner = null;
            ctx.json(Map.of("message", "Algorithm stopped"));
        } else {
            Log.debug("Stop requested but no algorithm running");
            ctx.status(404).json(Map.of("error", "No running algorithm to stop"));
        }
    }

    public void getResults(Context ctx) {
        Log.info("Algorithm results requested");
        if (faRunner == null) {
            ctx.status(404).json(Map.of("error", "No algorithm has been run"));
        } else if (faRunner.isRunning()) {
            ctx.status(400).json(Map.of("error", "Algorithm still running"));
        } else {
            ctx.json(faRunner.getResults());
        }
    }

    public void getIterations(Context ctx) {
        Log.info("Iteration history requested");
        if (faRunner == null) {
            ctx.status(404).json(Map.of("error", "No algorithm has been run"));
        } else {
            ctx.json(Map.of("iterations", faRunner.getIterationHistory()));
        }
    }
}

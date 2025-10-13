package cs43.group4;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.management.ThreadMXBean;

import cs43.group4.core.DataLoader;
import cs43.group4.core.DataLoader.Data;
import cs43.group4.core.FireflyAlgorithm;
import cs43.group4.core.FlowAllocator;
import cs43.group4.core.ObjectiveFunction;
import cs43.group4.core.ThesisObjective;
import cs43.group4.parameters.FAParams;
import cs43.group4.utils.AllocationNormalizer;
import cs43.group4.utils.AllocationResult;
import cs43.group4.utils.FlowResult;
import cs43.group4.utils.IterationResult;
import cs43.group4.utils.Log;

public class FARunner {
    private final FAParams params;
    private volatile boolean running = false;
    private volatile boolean stopped = false;
    private volatile String error = null;

    // Single run state
    private final List<IterationResult> iterationHistory = new CopyOnWriteArrayList<>();
    private final List<AllocationResult> allocations = new ArrayList<>();
    private final List<FlowResult> flows = new ArrayList<>();
    private Map<String, Object> results = null;
    private int currentIteration = 0;
    private double bestFitness;
    private double executionTime;
    private double memoryUsage;

    // Multiple runs state
    private int totalRuns = 1;
    private int currentRun = 0;
    private final List<RunResult> multipleRunResults = new CopyOnWriteArrayList<>();
    private final List<String> multipleRunErrors = new CopyOnWriteArrayList<>();
    private long multiRunStartTime;
    private long multiRunEndTime;

    private final int precision = 12;

    public FARunner(FAParams params) {
        this.params = params;
    }

    // ========== SINGLE RUN ==========

    public void run() throws Exception {
        running = true;
        iterationHistory.clear();
        allocations.clear();
        flows.clear();
        try {
            executeSingleRun();
        } catch (InterruptedException e) {
            this.error = "Stopped by user.";
            System.err.println("Stopped by user.");
        } catch (Exception e) {
            this.error = "FARunner error: " + e.getMessage();
            System.err.println("FARunner error: " + e.getMessage());
            throw e;
        } finally {
            running = false;
        }
    }

    // ========== MULTIPLE RUNS ==========

    public void runMultiple(int numRuns) throws Exception {
        if (numRuns < 2) {
            throw new IllegalArgumentException("Number of runs must be at least 2");
        }
        if (numRuns > 100) {
            throw new IllegalArgumentException("Number of runs cannot exceed 100");
        }

        running = true;
        totalRuns = numRuns;
        multipleRunResults.clear();
        multipleRunErrors.clear();
        multiRunStartTime = System.currentTimeMillis();

        try {
            Log.info("Starting " + numRuns + " runs");

            for (int run = 1; run <= numRuns; run++) {
                if (stopped) {
                    Log.warn("Multiple runs stopped by user at run " + run);
                    break;
                }

                currentRun = run;
                Log.info("Starting run " + run + "/" + numRuns);

                try {
                    // Clear single-run state for each run
                    iterationHistory.clear();
                    results = null;
                    currentIteration = 0;

                    // Execute single run
                    executeSingleRun();

                    // Store results
                    if (results != null) {
                        multipleRunResults.add(new RunResult(run, results));
                        Log.info("Run " + run + "/" + numRuns + " completed successfully");
                    }

                } catch (InterruptedException e) {
                    Log.warn("Run " + run + " stopped by user");
                    multipleRunErrors.add("Run " + run + ": Stopped by user");
                    break;
                } catch (Exception e) {
                    Log.error("Run " + run + " failed: %s", e.getMessage(), e);
                    multipleRunErrors.add("Run " + run + ": " + e.getMessage());
                }
            }

            multiRunEndTime = System.currentTimeMillis();
            Log.info("Completed " + multipleRunResults.size() + " out of " + numRuns + " runs");

        } catch (Exception e) {
            this.error = "Multiple runs error: " + e.getMessage();
            System.err.println("Multiple runs error: " + e.getMessage());
            throw e;
        } finally {
            running = false;
            currentRun = 0;
        }
    }

    // ========== SHARED EXECUTION LOGIC ==========

    private void executeSingleRun() throws Exception {
        var data = DataLoader.load(Path.of("data", "barangays.csv"), Path.of("data", "classes.csv"));
        int Z = data.Z, C = data.C;
        int D = Z * C;

        // Diagnostic: FA runs baseline optimizer with flows distance-aware if geo present
        boolean haveGeo = (data.lat != null && data.lon != null);
        Log.info("[FA] Running FireflyAlgorithm (baseline). Flow distance-aware: %s", haveGeo);

        double[] lower = new double[D];
        double[] upper = new double[D];
        for (int i = 0; i < Z; i++) {
            for (int c = 0; c < C; c++) {
                int k = i * C + c;
                lower[k] = 0.0;
                double cap = Math.max(1.0, Math.min(data.supply[c], data.AC[i] + 200));
                upper[k] = cap;
            }
        }

        double[][] currentPerClass = new double[C][Z];
        for (int i = 0; i < Z; i++) {
            if (C >= 1) currentPerClass[0][i] = data.sarCurrent[i];
            if (C >= 2) currentPerClass[1][i] = data.emsCurrent[i];
        }

        ObjectiveFunction baseObjective = new ThesisObjective(
                Z,
                C,
                data.r,
                data.f,
                data.E,
                data.AC,
                data.lambda,
                data.supply,
                1e-6,
                10.0,
                null,
                1.0,
                currentPerClass,
                null,
                null,
                0.01);

    final double objScale = params.os;
        ObjectiveFunction thesisObj = new ObjectiveFunction() {
            @Override
            public double evaluate(double[] x) {
                // ThesisObjective returns minimization value (-(fitness)+penalties).
                double v = baseObjective.evaluate(x);
                return v * objScale;
            }
        };

        FireflyAlgorithm fa = new FireflyAlgorithm(
                thesisObj,
                params.numFireflies,
                lower,
                upper,
                params.gamma,
                params.beta0,
                params.alpha0,
                params.alphaFinal,
                params.generations);

        fa.setProgressListener((generation, bestX) -> {
            if (stopped) return;

            currentIteration = generation;
            // Use optimizer's best minimization value -> convert to maximization for display
            double bestMin = fa.getBestValue();
            // Report the scaled fitness directly (consistent with scaled optimization objective)
            double bestFit = -bestMin;
            bestFit = roundToPrecision(bestFit);
            iterationHistory.add(new IterationResult(generation, bestFit));

            if (generation % 50 == 0) {
                String runPrefix = (totalRuns > 1) ? "[Run " + currentRun + "/" + totalRuns + "] " : "";
                double logFit =
                        iterationHistory.isEmpty() ? 0.0 : iterationHistory.get(iterationHistory.size() - 1).fitness;
                Log.info(runPrefix + "Iter " + generation + ": Fitness Score (Maximization) = " + logFit);
            }
        });

        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().getId();
        long allocatedBefore = threadBean.getThreadAllocatedBytes(threadId);
        long startTime = System.nanoTime();

        fa.optimize();
        checkStopped();

        long endTime = System.nanoTime();
        long allocatedAfter = threadBean.getThreadAllocatedBytes(threadId);

        double[] x = fa.getBestSolution();
        double[][] A = new double[Z][C];
        int k = 0;
        for (int i = 0; i < Z; i++) for (int c = 0; c < C; c++, k++) A[i][c] = Math.max(0.0, x[k]);

        for (int c = 0; c < C; c++) {
            double used = 0.0;
            for (int i = 0; i < Z; i++) used += A[i][c];
            double cap = data.supply[c];
            if (used > cap + 1e-6) {
                double scale = cap / (used + 1e-6);
                for (int i = 0; i < Z; i++) A[i][c] *= scale;
            }
        }

        // Final normalization: integer allocations that do not exceed per-class supplies
        A = AllocationNormalizer.enforceSupplyAndRound(A, data.supply);

        // Derive final metrics from optimizer's best values to reflect the true optimum found
    double minimizedObjective = fa.getBestValue();
    minimizedObjective = roundToPrecision(minimizedObjective);
    bestFitness = roundToPrecision(-minimizedObjective);
        executionTime = roundToPrecision((endTime - startTime) / 1_000_000.0);
        memoryUsage = allocatedAfter - allocatedBefore;

        // Only write outputs and log for single runs (not in multiple runs mode)
        if (totalRuns == 1) {
            Log.info("Execution Time: " + executionTime + " ms");
            Log.info("Memory Allocated: " + memoryUsage + " bytes ("
                    + String.format("%.2f", memoryUsage / (1024.0 * 1024.0)) + " MB)");
            Log.info("Best Fitness Score (Maximization) = " + bestFitness);
            Log.info("Best Fitness Score (Minimization) = " + minimizedObjective);

            var flow = (data.lat != null && data.lon != null)
                    ? FlowAllocator.allocate(A, currentPerClass, data.lat, data.lon)
                    : FlowAllocator.allocate(A, currentPerClass);

            allocations.addAll(createAllocations(A, data));
            flows.addAll(createFlows(flow.flows, data));

            // Optional: Still write CSVs if we want
            // writeFlowsCsv(flow.flows, data, Path.of("out", "flows.csv"));
            // writeAllocationsCsv(A, data, Path.of("out", "allocations.csv"));

        results = Map.of(
            "fitnessMaximization", bestFitness,
            "fitnessMinimization", minimizedObjective,
                    "totalIterations", params.generations,
                    "executionTimeMs", executionTime,
                    "memoryBytes", memoryUsage);

            // System.out.println(banner("Output Files"));
            // System.out.println("Wrote allocations CSV to: " + allocsPath.toString());
            // System.out.println("Wrote flows CSV to: " + flowsPath.toString());
            // System.out.println("Wrote iteration log to: " + logPath.toString());
            // System.out.println(line());
            // System.out.println();
        } else {
            // For multiple runs, just store minimal results
        results = Map.of(
            "fitnessMaximization", bestFitness,
            "fitnessMinimization", minimizedObjective,
                    "executionTimeMs", executionTime,
                    "memoryBytes", memoryUsage);
        }
    }

    // ========== STATUS & RESULTS ==========

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", running);

        if (totalRuns > 1) {
            // Multiple runs status
            status.put("mode", "multiple");
            status.put("currentRun", currentRun);
            status.put("totalRuns", totalRuns);
            status.put("completedRuns", multipleRunResults.size());
            status.put("failedRuns", multipleRunErrors.size());
            double runProgress = currentIteration / (double) params.generations;
            double overallProgress = (currentRun - 1 + runProgress) / totalRuns;
            status.put("progress", overallProgress);

            if (!running && multiRunEndTime > 0) {
                status.put("totalDurationMs", multiRunEndTime - multiRunStartTime);
            }

            if (!multipleRunErrors.isEmpty()) {
                status.put("errors", new ArrayList<>(multipleRunErrors));
            }
        } else {
            // Single run status
            status.put("mode", "single");
            status.put("currentIteration", currentIteration);
            status.put("totalIterations", params.generations);
            status.put("progress", running ? (double) currentIteration / params.generations : 1.0);
        }

        if (error != null) {
            status.put("error", error);
        }
        if (!running && results != null) {
            status.put("completed", true);
        }

        return status;
    }

    public Map<String, Object> getResults() {
        if (totalRuns > 1) {
            return getMultipleRunResults();
        } else {
            return results != null ? results : Map.of("error", "No results available");
        }
    }

    private Map<String, Object> getMultipleRunResults() {
        if (multipleRunResults.isEmpty()) {
            return Map.of("error", "No successful runs completed");
        }

        DoubleSummaryStatistics fitnessMaxStats = new DoubleSummaryStatistics();
        DoubleSummaryStatistics fitnessMinStats = new DoubleSummaryStatistics();
        DoubleSummaryStatistics timeStats = new DoubleSummaryStatistics();
        DoubleSummaryStatistics memoryStats = new DoubleSummaryStatistics();

        for (RunResult result : multipleRunResults) {
            Map<String, Object> data = result.results;
            if (data.containsKey("fitnessMaximization")) {
                fitnessMaxStats.accept((Double) data.get("fitnessMaximization"));
            }
            if (data.containsKey("fitnessMinimization")) {
                fitnessMinStats.accept((Double) data.get("fitnessMinimization"));
            }
            if (data.containsKey("executionTimeMs")) {
                timeStats.accept((Double) data.get("executionTimeMs"));
            }
            if (data.containsKey("memoryBytes")) {
                memoryStats.accept((Double) data.get("memoryBytes"));
            }
        }

        Map<String, Object> aggregated = new HashMap<>();
        aggregated.put("totalRuns", totalRuns);
        aggregated.put("successfulRuns", multipleRunResults.size());
        aggregated.put("failedRuns", multipleRunErrors.size());
        aggregated.put("totalDurationMs", multiRunEndTime - multiRunStartTime);

        aggregated.put(
                "fitnessMaximization",
                Map.of(
                        "best", fitnessMaxStats.getMax(),
                        "worst", fitnessMaxStats.getMin(),
                        "average", fitnessMaxStats.getAverage()));

        aggregated.put(
                "fitnessMinimization",
                Map.of(
                        "best", fitnessMinStats.getMin(),
                        "worst", fitnessMinStats.getMax(),
                        "average", fitnessMinStats.getAverage()));

        aggregated.put(
                "executionTime",
                Map.of(
                        "average", timeStats.getAverage(),
                        "min", timeStats.getMin(),
                        "max", timeStats.getMax()));

        aggregated.put(
                "memory",
                Map.of(
                        "average", memoryStats.getAverage(),
                        "min", memoryStats.getMin(),
                        "max", memoryStats.getMax()));

        List<Map<String, Object>> individualRuns = new ArrayList<>();
        for (RunResult result : multipleRunResults) {
            individualRuns.add(Map.of(
                    "runNumber", result.runNumber,
                    "fitnessMaximization", result.results.get("fitnessMaximization"),
                    "fitnessMinimization", result.results.get("fitnessMinimization"),
                    "executionTimeMs", result.results.get("executionTimeMs"),
                    "memoryBytes", result.results.get("memoryBytes")));
        }
        aggregated.put("runs", individualRuns);

        if (!multipleRunErrors.isEmpty()) {
            aggregated.put("errors", new ArrayList<>(multipleRunErrors));
        }

        return aggregated;
    }

    private double calculateStdDev(List<RunResult> results, String key, double mean) {
        double sumSquaredDiff = 0.0;
        int count = 0;

        for (RunResult result : results) {
            if (result.results.containsKey(key)) {
                double value = (Double) result.results.get(key);
                double diff = value - mean;
                sumSquaredDiff += diff * diff;
                count++;
            }
        }

        return count > 0 ? Math.sqrt(sumSquaredDiff / count) : 0.0;
    }

    public List<AllocationResult> getAllocations() {
        return new ArrayList<>(allocations);
    }

    public List<FlowResult> getFlows() {
        return new ArrayList<>(flows);
    }

    public List<IterationResult> getIterationHistory() {
        return new ArrayList<>(iterationHistory);
    }

    // ========== CONTROL ==========

    public void stop() {
        stopped = true;
    }

    public boolean isRunning() {
        return running;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isStopped() {
        return stopped;
    }

    public String getError() {
        return error;
    }

    private void checkStopped() throws InterruptedException {
        if (stopped) {
            throw new InterruptedException("Optimization stopped by user.");
        }
    }

    // ========== HELPER CLASSES ==========

    private static class RunResult {
        final int runNumber;
        final Map<String, Object> results;

        RunResult(int runNumber, Map<String, Object> results) {
            this.runNumber = runNumber;
            this.results = results;
        }
    }

    private static List<AllocationResult> createAllocations(double[][] A, Data data) {
        List<AllocationResult> allocations = new ArrayList<>();

        for (int i = 0; i < data.Z; i++) {
            AllocationResult allocation = new AllocationResult(data.barangayIds[i], data.barangayNames[i]);

            double total = 0.0;
            for (int c = 0; c < data.C; c++) {
                long amount = (long) Math.rint(A[i][c]);
                allocation.personnel.put(data.classNames[c], amount);
                total += A[i][c];
            }
            allocation.total = (long) Math.rint(total);
            allocations.add(allocation);
        }

        return allocations;
    }

    private static List<FlowResult> createFlows(double[][][] flows, Data data) {
        List<FlowResult> flowResults = new ArrayList<>();

        for (int c = 0; c < data.C; c++) {
            for (int from = 0; from < data.Z; from++) {
                for (int to = 0; to < data.Z; to++) {
                    double amt = flows[c][from][to];
                    long units = Math.max(0L, Math.round(amt));

                    if (units > 0L) {
                        flowResults.add(new FlowResult(
                                data.classIds[c],
                                data.classNames[c],
                                data.barangayIds[from],
                                data.barangayNames[from],
                                data.barangayIds[to],
                                data.barangayNames[to],
                                units));
                    }
                }
            }
        }

        return flowResults;
    }

    // ========== UTILITY METHODS (unchanged) ==========

    private static void writeAllocationsCsv(double[][] A, Data data, Path path) {
        try {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("id,name");
            for (int c = 0; c < data.C; c++) sb.append(",").append(data.classNames[c]);
            sb.append(",total\n");
            for (int i = 0; i < data.Z; i++) {
                double total = 0.0;
                sb.append(escapeCsv(data.barangayIds[i])).append(",").append(escapeCsv(data.barangayNames[i]));
                for (int c = 0; c < data.C; c++) {
                    sb.append(",").append((long) Math.rint(A[i][c]));
                    total += A[i][c];
                }
                sb.append(",").append((long) Math.rint(total)).append("\n");
            }
            Files.writeString(path, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Failed to write allocations CSV: " + e.getMessage());
        }
    }

    private static void writeFlowsCsv(double[][][] flows, Data data, Path path) {
        try {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("class_id,class_name,from_id,from_name,to_id,to_name,amount\n");
            for (int c = 0; c < data.C; c++) {
                for (int from = 0; from < data.Z; from++) {
                    for (int to = 0; to < data.Z; to++) {
                        double amt = flows[c][from][to];
                        long units = Math.max(0L, Math.round(amt));
                        if (units > 0L) {
                            sb.append(escapeCsv(data.classIds[c]))
                                    .append(",")
                                    .append(escapeCsv(data.classNames[c]))
                                    .append(",")
                                    .append(escapeCsv(data.barangayIds[from]))
                                    .append(",")
                                    .append(escapeCsv(data.barangayNames[from]))
                                    .append(",")
                                    .append(escapeCsv(data.barangayIds[to]))
                                    .append(",")
                                    .append(escapeCsv(data.barangayNames[to]))
                                    .append(",")
                                    .append(units)
                                    .append("\n");
                        }
                    }
                }
            }
            Files.writeString(path, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Failed to write flows CSV: " + e.getMessage());
        }
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static double estimateFitness(double[][] A, Data data, double eps) {
        int Z = data.Z, C = data.C;
        double[] totalPerI = new double[Z];
        double P = 0.0;
        for (int i = 0; i < Z; i++) {
            double s = 0.0;
            for (int c = 0; c < C; c++) s += A[i][c];
            totalPerI[i] = s;
            P += s;
        }
        double denomP = Math.max(P, eps);

        int Cz = 0;
        for (int i = 0; i < Z; i++) if (totalPerI[i] > 0) Cz++;
        double obj1 = (double) Cz / (double) Z;

        double obj2sum = 0.0;
        for (int i = 0; i < Z; i++) {
            double logTerm = Math.log(1.0 + Math.max(0.0, data.r[i]));
            for (int c = 0; c < C; c++) obj2sum += A[i][c] * logTerm;
        }
        double obj2 = obj2sum / denomP;

        double mean = 0.0;
        for (double v : totalPerI) mean += v;
        mean /= Math.max(1, Z);
        double var = 0.0;
        for (double v : totalPerI) {
            double d = v - mean;
            var += d * d;
        }
        double std = Math.sqrt(var / Math.max(1, Z));
        double obj3 = std / (mean + eps);

        double obj4sum = 0.0;
        for (int i = 0; i < Z; i++) {
            double Si = Math.max(0.0, data.r[i]) * Math.max(0.0, data.f[i]);
            for (int c = 0; c < C; c++) {
                double DiC = data.lambda[c] * (data.E[i] * Si) / (data.AC[i] + eps);
                double denom = Math.max(DiC, eps);
                double frac = Math.min(1.0, A[i][c] / denom);
                obj4sum += frac;
            }
        }
        double obj4 = obj4sum / (Z * C);
        return obj1 + obj2 - obj3 + obj4;
    }

    private static double estimateSupplyPenalty(double[][] A, Data data) {
        int Z = data.Z, C = data.C;
        double wSupply = 10.0;
        double penalty = 0.0;
        for (int c = 0; c < C; c++) {
            double used = 0.0;
            for (int i = 0; i < Z; i++) used += A[i][c];
            double viol = Math.max(0.0, used - data.supply[c]);
            penalty += wSupply * viol * viol;
        }
        return penalty;
    }

    private double roundToPrecision(double value) {
        double scale = Math.pow(10, precision);
        return Math.round(value * scale) / scale;
    }

    private static final int BANNER_WIDTH = 64;

    private static String banner(String label) {
        String text = " " + label.trim() + " ";
        int pad = Math.max(0, BANNER_WIDTH - text.length());
        int left = pad / 2;
        int right = pad - left;
        return "=".repeat(left) + text + "=".repeat(right);
    }

    private static String line() {
        return "=".repeat(BANNER_WIDTH);
    }
}

package cs43.group4;

import com.sun.management.ThreadMXBean;
import cs43.group4.core.DataLoader;
import cs43.group4.core.DataLoader.Data;
import cs43.group4.core.ExtendedFireflyAlgorithm;
import cs43.group4.core.FlowAllocator;
import cs43.group4.core.ObjectiveFunction;
import cs43.group4.core.ThesisObjective;
import cs43.group4.parameters.EFAParams;
import cs43.group4.utils.AllocationNormalizer;
import cs43.group4.utils.AllocationResult;
import cs43.group4.utils.FlowResult;
import cs43.group4.utils.IterationResult;
import cs43.group4.utils.Log;
import cs43.group4.utils.OverallStats;
import cs43.group4.utils.ValidationMultipleResult;
import cs43.group4.utils.ValidationMultipleResult.PerBarangayMultiStats;
import cs43.group4.utils.ValidationSingleResult;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class EFARunner {
    private final EFAParams params;
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
    private final List<ValidationSingleResult> multipleValidationResults = new CopyOnWriteArrayList<>();
    private long multiRunStartTime;
    private long multiRunEndTime;

    private final int precision = 12;

    public EFARunner(EFAParams params) {
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
            this.error = "ThesisRunner error: " + e.getMessage();
            System.err.println("ThesisRunner error: " + e.getMessage());
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
        multipleValidationResults.clear();
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

        // Diagnostic: EFA optimizer with flows distance-aware if geo present
        boolean haveGeo = (data.lat != null && data.lon != null);
        boolean objectiveFiltering = true; // DomainConstraintEvaluator is used inside ExtendedFireflyAlgorithm
        Log.info(
                "[EFA] Running ExtendedFireflyAlgorithm. Flow distance-aware: %s; Objective filtering: %s",
                haveGeo, objectiveFiltering);

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

        ObjectiveFunction thesisObj = new ThesisObjective(
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

        ExtendedFireflyAlgorithm efa = new ExtendedFireflyAlgorithm(
                thesisObj,
                data,
                params.numFireflies,
                lower,
                upper,
                params.gamma,
                params.beta0,
                params.betaMin,
                params.alpha0,
                params.alphaFinal,
                params.generations);

        // Tune gamma on the normalized scale
        efa.tuneGammaByInfluenceRadius(1.0, 0.6);

        efa.setProgressListener((generation, bestX, reinitializedCount) -> {
            if (stopped) return;

            currentIteration = generation;
            // Use optimizer's best value to ensure monotonic best-so-far
            double bestMin = efa.getBestValue();
            double bestFit = -bestMin; // convert to maximization-style fitness
            bestFit = roundToPrecision(bestFit);
            iterationHistory.add(new IterationResult(generation, bestFit));

            if (generation % 50 == 0) {
                String runPrefix = (totalRuns > 1) ? "[Run " + currentRun + "/" + totalRuns + "] " : "";
                double logFit = iterationHistory.isEmpty()
                        ? bestFit
                        : iterationHistory.get(iterationHistory.size() - 1).fitness;
                Log.info(runPrefix + "Iter " + generation + ": Fitness Score (Maximization) = " + logFit);
            }
        });

        ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().getId();
        long allocatedBefore = threadBean.getThreadAllocatedBytes(threadId);
        long startTime = System.nanoTime();

        efa.optimize();
        checkStopped();

        long endTime = System.nanoTime();
        long allocatedAfter = threadBean.getThreadAllocatedBytes(threadId);

        double[] x = efa.getBestSolution();
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

        // Use optimizer's best value for final metrics
        double minimizedObjective = efa.getBestValue();
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

            results = Map.of(
                    "fitnessMaximization", bestFitness,
                    "fitnessMinimization", minimizedObjective,
                    "totalIterations", params.generations,
                    "executionTimeMs", executionTime,
                    "memoryBytes", memoryUsage);
        } else {
            // var flow = (data.lat != null && data.lon != null)
            //         ? FlowAllocator.allocate(A, currentPerClass, data.lat, data.lon)
            //         : FlowAllocator.allocate(A, currentPerClass);

            allocations.clear();
            allocations.addAll(createAllocations(A, data));

            // Generate validation for this run
            ValidationSingleResult validation = generateValidation(data, allocations);
            if (!validation.hasError()) {
                multipleValidationResults.add(validation);
            }

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

    public List<AllocationResult> getAllocations() {
        return new ArrayList<>(allocations);
    }

    public List<FlowResult> getFlows() {
        return new ArrayList<>(flows);
    }

    public List<IterationResult> getIterationHistory() {
        return new ArrayList<>(iterationHistory);
    }

    // ========== VALIDATION METHODS ==========

    private ValidationSingleResult generateValidation(Data data, List<AllocationResult> currentAllocations) {
        ValidationSingleResult result = new ValidationSingleResult();

        try {
            if (currentAllocations.isEmpty()) {
                result.error = "No allocations available for validation";
                return result;
            }

            if (data.populations == null) {
                result.error = "Population data not available for validation";
                return result;
            }

            double totalPopulationScore = 0.0;
            double totalPopulationCloseness = 0.0;
            double totalSarCloseness = 0.0;
            double totalEmsCloseness = 0.0;
            double totalHazardCloseness = 0.0;
            double totalCombinedCloseness = 0.0;
            int validBarangays = 0;

            for (int i = 0; i < Math.min(currentAllocations.size(), data.Z); i++) {
                AllocationResult allocation = currentAllocations.get(i);
                double population = data.populations[i];

                if (population <= 0) continue;

                String hazardLevel = determineHazardLevel(data, i);
                ValidationSingleResult.BarangayValidation bv = new ValidationSingleResult.BarangayValidation(
                        data.barangayIds[i], data.barangayNames[i], (long) population, hazardLevel);

                // Population-based validation
                long idealResponders = Math.round(population / 500);
                long actualResponders = allocation.total;
                bv.populationCloseness = roundToPercent(((double) actualResponders / idealResponders));

                // Hazard-based validation
                double[] idealRatios = getIdealSARRatio(hazardLevel);
                long idealSAR = Math.round(idealResponders * idealRatios[0]);
                long idealEMS = Math.round(idealResponders * idealRatios[1]);

                bv.idealTotal = idealResponders;
                bv.idealSAR = idealSAR;
                bv.idealEMS = idealEMS;

                long actualSAR = allocation.personnel.getOrDefault("SAR", 0L);
                long actualEMS = allocation.personnel.getOrDefault("EMS", 0L);

                bv.actualTotal = allocation.total;
                bv.actualSAR = actualSAR;
                bv.actualEMS = actualEMS;

                double sarCloseness = (idealSAR > 0) ? ((double) actualSAR / idealSAR) : 1.0;
                double emsCloseness = (idealEMS > 0) ? ((double) actualEMS / idealEMS) : 1.0;

                bv.sarCloseness = roundToPercent(sarCloseness);
                bv.emsCloseness = roundToPercent(emsCloseness);
                bv.hazardCloseness = roundToPercent((sarCloseness + emsCloseness) / 2.0);

                double combinedCloseness = (bv.populationCloseness + bv.hazardCloseness) / 2.0;
                bv.combinedCloseness = roundToPercent(combinedCloseness);

                bv.populationScore = getPopulationScore(data.populations[i], bv.actualTotal);

                result.barangayValidations.add(bv);

                totalPopulationScore += bv.populationScore;
                totalPopulationCloseness += bv.populationCloseness;
                totalSarCloseness += bv.sarCloseness;
                totalEmsCloseness += bv.emsCloseness;
                totalHazardCloseness += bv.hazardCloseness;
                totalCombinedCloseness += bv.combinedCloseness;
                validBarangays++;
            }

            if (validBarangays > 0) {
                result.overallStats = new OverallStats(
                        validBarangays,
                        roundToPercent(totalPopulationScore / validBarangays),
                        roundToPercent(totalPopulationCloseness / validBarangays),
                        roundToPercent(totalSarCloseness / validBarangays),
                        roundToPercent(totalEmsCloseness / validBarangays),
                        roundToPercent(totalHazardCloseness / validBarangays),
                        roundToPercent(totalCombinedCloseness / validBarangays));
            }

        } catch (Exception e) {
            result.error = "Validation error: " + e.getMessage();
            Log.error("Validation error: %s", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Generates a comprehensive validation report based on NDRRMC standards
     * Uses getAllocations() to get the allocation data
     * @return ValidationSingleResult object with all validation metrics
     */
    public ValidationSingleResult getValidationSingleReport() {
        try {
            List<AllocationResult> currentAllocations = getAllocations();
            if (currentAllocations.isEmpty()) {
                ValidationSingleResult result = new ValidationSingleResult();
                result.error = "No allocations available for validation";
                return result;
            }

            var data = DataLoader.load(Path.of("data", "barangays.csv"), Path.of("data", "classes.csv"));
            ValidationSingleResult result = generateValidation(data, currentAllocations);

            if (result.overallStats != null) {
                result.interpretation = result.generateSingleRunInterpretation();
            }

            return result;

        } catch (Exception e) {
            ValidationSingleResult result = new ValidationSingleResult();
            result.error = "Validation error: " + e.getMessage();
            Log.error("Validation error: %s", e.getMessage(), e);
            return result;
        }
    }

    public ValidationMultipleResult getValidationMultipleReport() {
        ValidationMultipleResult result = new ValidationMultipleResult();

        if (multipleValidationResults.isEmpty()) {
            return result; // Return empty result if no validation data
        }

        // Collect overall stats from each run
        List<Double> populationClosenessList = new ArrayList<>();
        List<Double> sarClosenessList = new ArrayList<>();
        List<Double> emsClosenessList = new ArrayList<>();
        List<Double> hazardClosenessList = new ArrayList<>();
        List<Double> combinedClosenessList = new ArrayList<>();
        List<Double> populationScoreList = new ArrayList<>();

        for (ValidationSingleResult run : multipleValidationResults) {
            if (run.overallStats != null) {
                populationClosenessList.add(run.overallStats.averagePopulationCloseness);
                sarClosenessList.add(run.overallStats.averageSarCloseness);
                emsClosenessList.add(run.overallStats.averageEmsCloseness);
                hazardClosenessList.add(run.overallStats.averageHazardCloseness);
                combinedClosenessList.add(run.overallStats.averageCombinedCloseness);
                populationScoreList.add(run.overallStats.averagePopulationScore);
            }
        }

        if (populationClosenessList.isEmpty()) {
            return result; // No valid data
        }

        // Calculate mean
        result.meanPopulationCloseness = calculateMean(populationClosenessList);
        result.meanSarCloseness = calculateMean(sarClosenessList);
        result.meanEmsCloseness = calculateMean(emsClosenessList);
        result.meanHazardCloseness = calculateMean(hazardClosenessList);
        result.meanCombinedCloseness = calculateMean(combinedClosenessList);
        result.meanPopulationScore = calculateMean(populationScoreList);

        // Calculate std dev
        result.stdPopulationCloseness = calculateStdDev(populationClosenessList, result.meanPopulationCloseness);
        result.stdSarCloseness = calculateStdDev(sarClosenessList, result.meanSarCloseness);
        result.stdEmsCloseness = calculateStdDev(emsClosenessList, result.meanEmsCloseness);
        result.stdHazardCloseness = calculateStdDev(hazardClosenessList, result.meanHazardCloseness);
        result.stdCombinedCloseness = calculateStdDev(combinedClosenessList, result.meanCombinedCloseness);
        result.stdPopulationScore = calculateStdDev(populationScoreList, result.meanPopulationScore);

        // Calculate min/max
        result.minPopulationCloseness =
                populationClosenessList.stream().min(Double::compare).orElse(0.0);
        result.minSarCloseness = sarClosenessList.stream().min(Double::compare).orElse(0.0);
        result.minEmsCloseness = emsClosenessList.stream().min(Double::compare).orElse(0.0);
        result.minHazardCloseness =
                hazardClosenessList.stream().min(Double::compare).orElse(0.0);
        result.minCombinedCloseness =
                combinedClosenessList.stream().min(Double::compare).orElse(0.0);
        result.minPopulationScore =
                populationScoreList.stream().min(Double::compare).orElse(0.0);

        result.maxPopulationCloseness =
                populationClosenessList.stream().max(Double::compare).orElse(0.0);
        result.maxSarCloseness = sarClosenessList.stream().max(Double::compare).orElse(0.0);
        result.maxEmsCloseness = emsClosenessList.stream().max(Double::compare).orElse(0.0);
        result.maxHazardCloseness =
                hazardClosenessList.stream().max(Double::compare).orElse(0.0);
        result.maxCombinedCloseness =
                combinedClosenessList.stream().max(Double::compare).orElse(0.0);
        result.maxPopulationScore =
                populationScoreList.stream().max(Double::compare).orElse(0.0);

        // Calculate coefficient of variation
        result.cvPopulationCloseness = (result.meanPopulationCloseness != 0)
                ? result.stdPopulationCloseness / result.meanPopulationCloseness
                : 0.0;
        result.cvSarCloseness = (result.meanSarCloseness != 0) ? result.stdSarCloseness / result.meanSarCloseness : 0.0;
        result.cvEmsCloseness = (result.meanEmsCloseness != 0) ? result.stdEmsCloseness / result.meanEmsCloseness : 0.0;
        result.cvHazardCloseness =
                (result.meanHazardCloseness != 0) ? result.stdHazardCloseness / result.meanHazardCloseness : 0.0;
        result.cvCombinedCloseness =
                (result.meanCombinedCloseness != 0) ? result.stdCombinedCloseness / result.meanCombinedCloseness : 0.0;
        result.cvPopulationScore =
                (result.meanPopulationScore != 0) ? result.stdPopulationScore / result.meanPopulationScore : 0.0;

        // Store individual runs
        result.individualRuns = new ArrayList<>(multipleValidationResults);

        result.perBarangayStats = calculatePerBarangayStats(multipleValidationResults);

        return result;
    }

    private List<ValidationMultipleResult.PerBarangayMultiStats> calculatePerBarangayStats(
            List<ValidationSingleResult> validationResults) {

        if (validationResults.isEmpty()) {
            return new ArrayList<>();
        }

        // Get the first run to determine barangay list
        ValidationSingleResult firstRun = validationResults.get(0);
        if (firstRun.barangayValidations.isEmpty()) {
            return new ArrayList<>();
        }

        int numBarangays = firstRun.barangayValidations.size();
        List<PerBarangayMultiStats> perBarangayStats = new ArrayList<>();

        // For each barangay
        for (int i = 0; i < numBarangays; i++) {
            // Collect data across all runs for this barangay
            List<Double> populationCloseness = new ArrayList<>();
            List<Double> sarCloseness = new ArrayList<>();
            List<Double> emsCloseness = new ArrayList<>();
            List<Double> hazardCloseness = new ArrayList<>();
            List<Double> combinedCloseness = new ArrayList<>();

            String barangayId = null;
            String barangayName = null;

            for (ValidationSingleResult run : validationResults) {
                if (i < run.barangayValidations.size()) {
                    ValidationSingleResult.BarangayValidation bv = run.barangayValidations.get(i);

                    // Capture ID and name from first valid run
                    if (barangayId == null) {
                        barangayId = bv.barangayId;
                        barangayName = bv.barangayName;
                    }

                    populationCloseness.add(bv.populationCloseness);
                    sarCloseness.add(bv.sarCloseness);
                    emsCloseness.add(bv.emsCloseness);
                    hazardCloseness.add(bv.hazardCloseness);
                    combinedCloseness.add(bv.combinedCloseness);
                }
            }

            if (barangayId != null && !populationCloseness.isEmpty()) {
                ValidationMultipleResult.PerBarangayMultiStats stats =
                        new ValidationMultipleResult.PerBarangayMultiStats(barangayId, barangayName);

                // Calculate mean and std dev for each metric
                stats.meanPopulationCloseness = calculateMean(populationCloseness);
                stats.stdPopulationCloseness = calculateStdDev(populationCloseness, stats.meanPopulationCloseness);
                stats.cvPopulationCloseness = (stats.meanPopulationCloseness != 0)
                        ? stats.stdPopulationCloseness / stats.meanPopulationCloseness
                        : 0.0;

                stats.meanSarCloseness = calculateMean(sarCloseness);
                stats.stdSarCloseness = calculateStdDev(sarCloseness, stats.meanSarCloseness);
                stats.cvSarCloseness =
                        (stats.meanSarCloseness != 0) ? stats.stdSarCloseness / stats.meanSarCloseness : 0.0;

                stats.meanEmsCloseness = calculateMean(emsCloseness);
                stats.stdEmsCloseness = calculateStdDev(emsCloseness, stats.meanEmsCloseness);
                stats.cvEmsCloseness =
                        (stats.meanEmsCloseness != 0) ? stats.stdEmsCloseness / stats.meanEmsCloseness : 0.0;

                stats.meanHazardCloseness = calculateMean(hazardCloseness);
                stats.stdHazardCloseness = calculateStdDev(hazardCloseness, stats.meanHazardCloseness);
                stats.cvHazardCloseness =
                        (stats.meanHazardCloseness != 0) ? stats.stdHazardCloseness / stats.meanHazardCloseness : 0.0;

                stats.meanCombinedCloseness = calculateMean(combinedCloseness);
                stats.stdCombinedCloseness = calculateStdDev(combinedCloseness, stats.meanCombinedCloseness);
                stats.cvCombinedCloseness = (stats.meanCombinedCloseness != 0)
                        ? stats.stdCombinedCloseness / stats.meanCombinedCloseness
                        : 0.0;

                perBarangayStats.add(stats);
            }
        }

        return perBarangayStats;
    }

    // ========== VALIDATION HELPER METHODS ==========

    private String determineHazardLevel(Data data, int barangayIndex) {
        double depth = data.f[barangayIndex]; // flood depth in ft

        if (depth > 4.92126) return "High"; // >1.5 m
        if (depth > 1.64042) return "Medium"; // 0.5 m – 1.5 m
        if (depth >= 0.656168) return "Low"; // 0.2 m – 0.5 m

        return "Medium"; // fallback for depth < 0.656168 ft (<0.2 m)
    }

    private double[] getIdealSARRatio(String hazardLevel) {
        // Returns [SAR ratio, EMS ratio] based on hazard level
        switch (hazardLevel) {
            case "High":
                return new double[] {0.85, 0.15};
            case "Medium":
                return new double[] {0.75, 0.25};
            case "Low":
                return new double[] {0.65, 0.35};
            default:
                return new double[] {0.75, 0.25}; // Default to medium
        }
    }

    private int getPopulationScore(double population, long actualTotalResponders) {
        if (actualTotalResponders == 0) return 0;

        double peoplePerResponder = (double) population / actualTotalResponders;

        // Score based on ratio (lower ratio = more responders = better)
        if (peoplePerResponder <= 500) return 4; // 1:500 or better
        else if (peoplePerResponder <= 1000) return 3; // 1:1000 or better
        else if (peoplePerResponder <= 2000) return 2; // 1:2000 or better
        else if (peoplePerResponder <= 3000) return 1; // 1:3000 or better
        else return 0; // Worse than 1:3000
    }

    private double roundToPercent(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // Calculate sample standard deviation
    private double calculateStdDev(List<Double> values, double mean) {
        int n = values.size();
        if (n <= 1) return 0.0;

        double sumSquaredDiff = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }

        return Math.sqrt(sumSquaredDiff / (n - 1));
    }

    private double calculateMean(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
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

    // ========== UTILITY METHODS ==========
    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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
}

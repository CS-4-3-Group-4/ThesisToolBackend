package cs43.group4;

import cs43.group4.core.DataLoader;
import cs43.group4.core.DataLoader.Data;
import cs43.group4.core.FireflyAlgorithm;
import cs43.group4.core.FlowAllocator;
import cs43.group4.core.ObjectiveFunction;
import cs43.group4.core.ThesisObjective;
import cs43.group4.utils.IterationResult;
import cs43.group4.utils.Log;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class FARunner {
    private final AlgorithmParams params;
    private volatile boolean running = false;
    private volatile boolean stopped = false;
    private volatile String error = null;

    private final List<IterationResult> iterationHistory = new CopyOnWriteArrayList<>();
    private Map<String, Object> results = null;

    private int currentIteration = 0;
    private double bestFitness;
    private double executionTime;
    private double memoryUsage;

    private final int precision = 12; // fixed-length decimals for logs and terminal

    public FARunner(AlgorithmParams params) {
        this.params = params;
    }

    public void run() throws Exception {
        running = true;
        try {
            var data = DataLoader.load(Path.of("data", "barangays.csv"), Path.of("data", "classes.csv"));
            int Z = data.Z, C = data.C;
            int D = Z * C;

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

            // Prepare current per-class counts [C][Z] for distance penalty and flows
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
                    data.lat,
                    data.lon,
                    0.01 // 0.01 penalty per km of average movement
                    );

            // Firefly Algorithm Parameters
            FireflyAlgorithm fa = new FireflyAlgorithm(
                    thesisObj,
                    params.numFireflies,
                    lower,
                    upper,
                    params.gamma,
                    params.beta,
                    params.alpha0,
                    params.alphaFinal,
                    params.generations);

            // Per-iteration progress: log all, print only every 50th
            fa.setProgressListener((generation, bestX) -> {
                if (stopped) return;

                currentIteration = generation;
                double[][] Aiter = new double[Z][C];
                int kk2 = 0;
                for (int i = 0; i < Z; i++) {
                    for (int c = 0; c < C; c++, kk2++) {
                        Aiter[i][c] = Math.max(0.0, bestX[kk2]);
                    }
                }
                // Apply same per-class repair as objective
                for (int c = 0; c < C; c++) {
                    double used = 0.0;
                    for (int i = 0; i < Z; i++) used += Aiter[i][c];
                    double cap = data.supply[c];
                    if (used > cap + 1e-6) {
                        double scale = cap / (used + 1e-6);
                        for (int i = 0; i < Z; i++) Aiter[i][c] *= scale;
                    }
                }
                double fit = estimateFitness(Aiter, data, 1e-6);
                fit = roundToPrecision(fit);
                iterationHistory.add(new IterationResult(generation, fit));

                // Print Fitness Score to terminal every 50 iterations
                if (generation % 50 == 0) {
                    Log.info("Iter " + generation + ": Fitness Score (Maximization) = " + fit);
                }
            });

            // Before Optimization
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            long startTime = System.nanoTime();

            fa.optimize();
            checkStopped();

            /// After Optimization
            long endTime = System.nanoTime();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

            double[] x = fa.getBestSolution();
            double[][] A = new double[Z][C];
            int k = 0;
            for (int i = 0; i < Z; i++) for (int c = 0; c < C; c++, k++) A[i][c] = Math.max(0.0, x[k]);
            // Apply same per-class repair as objective for consistent reporting
            for (int c = 0; c < C; c++) {
                double used = 0.0;
                for (int i = 0; i < Z; i++) used += A[i][c];
                double cap = data.supply[c];
                if (used > cap + 1e-6) {
                    double scale = cap / (used + 1e-6);
                    for (int i = 0; i < Z; i++) A[i][c] *= scale;
                }
            }

            double fitness = estimateFitness(A, data, 1e-6);
            double minimizedObjective = -(fitness) + estimateSupplyPenalty(A, data);
            bestFitness = roundToPrecision(fitness);
            executionTime = roundToPrecision((endTime - startTime) / 1_000_000.0); // Convert ns to ms
            memoryUsage = memoryAfter - memoryBefore; // in bytes
            // memoryUsage = roundToPrecision((memoryAfter - memoryBefore) / (1024.0 * 1024.0));
            // Convert bytes to MB

            Log.info("Execution Time: " + executionTime + " ms");
            Log.info("Memory Usage: " + memoryUsage + " bytes");
            Log.info("Best Fitness Score (Maximization) = " + bestFitness);
            Log.info("Best Fitness Score (Minimization) = " + minimizedObjective);

            // Write outputs (flows and allocations to CSV files and iteration log to .log file)
            Path flowsPath = Path.of("out", "flows.csv");
            Path allocsPath = Path.of("out", "allocations.csv");
            Path logPath = Path.of("out", "iterations.log");

            var flow = (data.lat != null && data.lon != null)
                    ? FlowAllocator.allocate(A, currentPerClass, data.lat, data.lon)
                    : FlowAllocator.allocate(A, currentPerClass);
            writeFlowsCsv(flow.flows, data, flowsPath);
            writeAllocationsCsv(A, data, allocsPath);

            results = Map.of(
                    "fitnessMaximization",
                    fitness,
                    "fitnessMinimization",
                    minimizedObjective,
                    "totalIterations",
                    params.generations,
                    "flowsFile",
                    flowsPath.toString(),
                    "allocationsFile",
                    allocsPath.toString());

            // Output Piles Section
            System.out.println(banner("Output Files"));
            System.out.println("Wrote allocations CSV to: " + allocsPath.toString());
            System.out.println("Wrote flows CSV to: " + flowsPath.toString());
            System.out.println("Wrote iteration log to: " + logPath.toString());
            System.out.println(line());
            System.out.println();
        } catch (InterruptedException e) {
            this.error = "Stopped by user.";
            System.err.println("Stopped by user.");
        } catch (Exception e) {
            this.error = "ThesisRunner error: " + e.getMessage();
            System.err.println("ThesisRunner error: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    private static void writeAllocationsCsv(double[][] A, Data data, Path path) {
        try {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder();
            // Header
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
            // Header
            sb.append("class_id,class_name,from_id,from_name,to_id,to_name,amount\n");
            for (int c = 0; c < data.C; c++) {
                for (int from = 0; from < data.Z; from++) {
                    for (int to = 0; to < data.Z; to++) {
                        double amt = flows[c][from][to];
                        long units = Math.max(0L, Math.round(amt)); // integer persons
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
            // Removed per-file console print; grouped at end
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

    // Compute thesis fitness (without penalties) for reporting only
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

        // Obj1 coverage
        int Cz = 0;
        for (int i = 0; i < Z; i++) if (totalPerI[i] > 0) Cz++;
        double obj1 = (double) Cz / (double) Z;

        // Obj2 prioritization
        double obj2sum = 0.0;
        for (int i = 0; i < Z; i++) {
            double logTerm = Math.log(1.0 + Math.max(0.0, data.r[i]));
            for (int c = 0; c < C; c++) obj2sum += A[i][c] * logTerm;
        }
        double obj2 = obj2sum / denomP;

        // Obj3 imbalance
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

        // Obj4 demand satisfaction
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

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", running);
        status.put("currentIteration", currentIteration);
        status.put("totalIterations", params.generations);
        status.put("progress", running ? (double) currentIteration / params.generations : 1.0);
        if (error != null) {
            status.put("error", error);
        }
        if (!running && results != null) {
            status.put("completed", true);
        }
        return status;
    }

    public Map<String, Object> getResults() {
        return results != null ? results : Map.of("error", "No results available");
    }

    public List<IterationResult> getIterationHistory() {
        return new ArrayList<>(iterationHistory);
    }

    private void checkStopped() throws InterruptedException {
        if (stopped) {
            throw new InterruptedException("Optimization stopped by user.");
        }
    }

    // Utils
    private double roundToPrecision(double value) {
        double scale = Math.pow(10, precision);
        return Math.round(value * scale) / scale;
    }

    // Banner Builder Width
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
